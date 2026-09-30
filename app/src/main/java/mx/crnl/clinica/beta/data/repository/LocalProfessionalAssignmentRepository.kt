package mx.crnl.clinica.beta.data.repository

import androidx.room.withTransaction
import java.text.Collator
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.core.util.ClinicTime
import mx.crnl.clinica.beta.data.local.entity.DemoUserEntity
import mx.crnl.clinica.beta.data.local.entity.ProfessionalAssignmentEntity
import mx.crnl.clinica.beta.data.local.mapper.toDomain
import mx.crnl.clinica.beta.data.local.mapper.toProfessionalOption
import mx.crnl.clinica.beta.domain.access.BetaClinicalAccessPolicy
import mx.crnl.clinica.beta.domain.access.InterareaAccessPolicy
import mx.crnl.clinica.beta.domain.common.EntityKind
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AccessGrant
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.AssignmentStatus
import mx.crnl.clinica.beta.domain.model.AuditAction
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.PatientAssignment
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.ProfessionalOption
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.domain.repository.ProfessionalAssignmentRepository
import mx.crnl.clinica.beta.domain.text.TextNormalizer

class LocalProfessionalAssignmentRepository(
    private val database: ClinicalDatabase,
    private val audit: AuditRecorder,
    private val clock: Clock,
    private val idGenerator: () -> String,
) : ProfessionalAssignmentRepository {
    private val assignmentDao = database.assignmentDao()
    private val patientDao = database.patientDao()
    private val userDao = database.userDao()
    private val grants = EffectiveGrantSource(database.accessGrantDao(), clock)

    // Leer un área es por rol o por una concesión de lectura vigente sobre ese paciente; asignar nunca depende de ella.
    override fun observeActiveAssignments(patientId: String, viewer: UserAccount): Flow<List<PatientAssignment>> =
        combine(assignmentDao.observeRows(patientId), grants.observe(viewer)) { rows, active ->
            val viewable = readableAreas(viewer, active, patientId)
            rows.map { it.toDomain() }
                .filter { it.status == AssignmentStatus.ACTIVE && it.area in viewable }
                .sortedBy { it.area }
        }.flowOn(Dispatchers.Default)

    override fun observeHistory(patientId: String, area: ClinicalArea, viewer: UserAccount): Flow<List<PatientAssignment>> =
        combine(assignmentDao.observeRows(patientId), grants.observe(viewer)) { rows, active ->
            if (area in readableAreas(viewer, active, patientId)) rows.map { it.toDomain() }.filter { it.area == area } else emptyList()
        }.flowOn(Dispatchers.Default)

    override suspend fun getActiveAssignment(patientId: String, area: ClinicalArea, viewer: UserAccount): PatientAssignment? {
        val readable = BetaClinicalAccessPolicy.viewableAreas(viewer) + grants.readableAreas(viewer, patientId)
        if (area !in readable) return null
        return assignmentDao.getActiveRow(patientId, area.name)?.toDomain()
    }

    private fun readableAreas(viewer: UserAccount, active: List<AccessGrant>, patientId: String): Set<ClinicalArea> =
        BetaClinicalAccessPolicy.viewableAreas(viewer) +
            InterareaAccessPolicy.readableAreas(viewer, active, patientId, clock.instant()).keys

    override suspend fun listAssignableProfessionals(area: ClinicalArea, viewer: UserAccount): List<ProfessionalOption> {
        if (!BetaClinicalAccessPolicy.canCreateAssignment(viewer, area)) return emptyList()
        return userDao.listActiveProfessionals(area.name)
            .sortedWith(byName)
            .map { it.toProfessionalOption() }
    }

    override suspend fun createInitialAssignment(
        patientId: String,
        area: ClinicalArea,
        professionalId: String,
        reason: String?,
        actorUserId: String,
    ): OperationResult<PatientAssignment> = database.withTransaction {
        val actor = userDao.getById(actorUserId)?.toDomain()
        if (actor == null || !BetaClinicalAccessPolicy.canCreateAssignment(actor, area)) {
            return@withTransaction OperationResult.Failure(OperationError.NotAuthorized)
        }
        val patient = patientDao.getById(patientId)
            ?: return@withTransaction OperationResult.Failure(OperationError.NotFound(EntityKind.PATIENT))
        if (patient.status != PatientStatus.ACTIVE.name) return@withTransaction OperationResult.Failure(OperationError.PatientNotActive)

        val professional = userDao.getById(professionalId)
            ?: return@withTransaction OperationResult.Failure(OperationError.NotFound(EntityKind.PROFESSIONAL))
        val available = professional.status == AccountStatus.ACTIVE.name &&
            professional.roleCode == UserRole.PROFESSIONAL.name &&
            professional.areaCode == area.name
        if (!available) return@withTransaction OperationResult.Failure(OperationError.ProfessionalNotAvailable)

        // Solo hay una asignación vigente por paciente y área; cambiar de profesional no está habilitado en esta versión.
        if (assignmentDao.countActive(patientId, area.name) > 0) {
            return@withTransaction OperationResult.Failure(OperationError.AssignmentAlreadyActive)
        }

        val now = clock.millis()
        val assignmentId = idGenerator()
        val cleanReason = TextNormalizer.tidy(reason.orEmpty()).take(MAX_REASON_LENGTH).ifEmpty { null }
        assignmentDao.insert(
            ProfessionalAssignmentEntity(
                assignmentId = assignmentId,
                patientId = patientId,
                areaCode = area.name,
                professionalId = professionalId,
                startAt = now,
                endAt = null,
                status = AssignmentStatus.ACTIVE.name,
                reason = cleanReason,
                assignedBy = actor.userId,
                createdAt = now,
            ),
        )
        audit.record(
            AuditAction.ASSIGNMENT_CREATED,
            actorUserId = actor.userId,
            entityType = ENTITY_ASSIGNMENT,
            entityId = assignmentId,
            patientId = patientId,
            areaCode = area.name,
            metadata = mapOf(
                "professionalId" to AuditRecorder.text(professionalId),
                "reasonGiven" to AuditRecorder.flag(cleanReason != null),
            ),
        )
        OperationResult.Success(requireNotNull(assignmentDao.getRow(assignmentId)) { "La asignación $assignmentId debería existir" }.toDomain())
    }

    private companion object {
        const val ENTITY_ASSIGNMENT = "ASSIGNMENT"
        const val MAX_REASON_LENGTH = 200

        private val collator: Collator = Collator.getInstance(ClinicTime.locale).apply { strength = Collator.PRIMARY }

        val byName: Comparator<DemoUserEntity> = Comparator { first, second ->
            compareValuesBy(
                first,
                second,
                { collator.getCollationKey(it.paternalSurname) },
                { collator.getCollationKey(it.maternalSurname.orEmpty()) },
                { collator.getCollationKey(it.firstName) },
            )
        }
    }
}
