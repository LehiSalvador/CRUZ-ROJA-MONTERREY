package mx.crnl.clinica.beta.data.repository

import androidx.room.withTransaction
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.data.local.entity.ClinicalEncounterEntity
import mx.crnl.clinica.beta.data.local.mapper.personName
import mx.crnl.clinica.beta.data.local.mapper.toDomain
import mx.crnl.clinica.beta.data.local.mapper.toSummary
import mx.crnl.clinica.beta.domain.access.BetaClinicalAccessPolicy
import mx.crnl.clinica.beta.domain.access.InterareaAccessPolicy
import mx.crnl.clinica.beta.domain.clinical.EncounterRules
import mx.crnl.clinica.beta.domain.common.EntityKind
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AccessGrant
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AuditAction
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.EncounterDetail
import mx.crnl.clinica.beta.domain.model.EncounterDraft
import mx.crnl.clinica.beta.domain.model.EncounterFormContext
import mx.crnl.clinica.beta.domain.model.EncounterStatus
import mx.crnl.clinica.beta.domain.model.EncounterSummary
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.domain.repository.EncounterRepository

class LocalEncounterRepository(
    private val database: ClinicalDatabase,
    private val audit: AuditRecorder,
    private val clock: Clock,
    private val idGenerator: () -> String,
) : EncounterRepository {
    private val encounterDao = database.encounterDao()
    private val assignmentDao = database.assignmentDao()
    private val appointmentDao = database.appointmentDao()
    private val patientDao = database.patientDao()
    private val userDao = database.userDao()
    private val grants = EffectiveGrantSource(database.accessGrantDao(), clock)

    // Leer un área es por rol o por una concesión de lectura vigente sobre ese paciente; escribir nunca depende de ella.
    override fun observeAreaEncounters(patientId: String, area: ClinicalArea, viewer: UserAccount): Flow<List<EncounterSummary>> =
        combine(encounterDao.observeAreaRows(patientId, area.name), grants.observe(viewer)) { rows, active ->
            if (area in readableAreas(viewer, active, patientId)) rows.map { it.toSummary() } else emptyList()
        }.flowOn(Dispatchers.Default)

    override fun observeEncounter(encounterId: String, viewer: UserAccount): Flow<EncounterDetail?> =
        combine(encounterDao.observeDetailRow(encounterId), grants.observe(viewer)) { row, active ->
            row
                ?.takeIf { ClinicalArea.valueOf(it.areaCode) in readableAreas(viewer, active, it.patientId) }
                ?.toDomain()
        }.flowOn(Dispatchers.Default)

    private fun readableAreas(viewer: UserAccount, active: List<AccessGrant>, patientId: String): Set<ClinicalArea> =
        BetaClinicalAccessPolicy.viewableAreas(viewer) +
            InterareaAccessPolicy.readableAreas(viewer, active, patientId, clock.instant()).keys

    override suspend fun getFormContext(patientId: String, area: ClinicalArea, viewer: UserAccount): EncounterFormContext? {
        if (!BetaClinicalAccessPolicy.canViewAreaDetail(viewer, area)) return null
        val patient = patientDao.getById(patientId) ?: return null
        val assignment = assignmentDao.getActiveRow(patientId, area.name)?.toDomain()
        val assignedId = assignment?.professionalId
        val linkable = if (assignedId == null) {
            emptyList()
        } else {
            appointmentDao.observeRowsByPatient(patientId).first()
                .map { it.toDomain() }
                .filter { it.area == area && it.professionalId == assignedId && it.status in EncounterRules.LINKABLE_APPOINTMENT_STATUSES }
                .sortedByDescending { it.start }
        }
        return EncounterFormContext(
            patientId = patientId,
            patientName = personName(patient.firstName, patient.paternalSurname, patient.maternalSurname),
            patientNumber = patient.patientNumber,
            area = area,
            assignment = assignment,
            linkableAppointments = linkable,
            canCreate = BetaClinicalAccessPolicy.canCreateEncounter(viewer, area, assignedId.orEmpty(), assignedId),
        )
    }

    override suspend fun createEncounter(draft: EncounterDraft, actorUserId: String): OperationResult<EncounterDetail> =
        database.withTransaction {
            val actor = userDao.getById(actorUserId)?.toDomain()
            if (actor == null || !BetaClinicalAccessPolicy.canViewAreaDetail(actor, draft.area)) {
                return@withTransaction OperationResult.Failure(OperationError.NotAuthorized)
            }
            val patient = patientDao.getById(draft.patientId)
                ?: return@withTransaction OperationResult.Failure(OperationError.NotFound(EntityKind.PATIENT))
            if (patient.status != PatientStatus.ACTIVE.name) return@withTransaction OperationResult.Failure(OperationError.PatientNotActive)

            val assigned = assignmentDao.getActiveRow(draft.patientId, draft.area.name)
                ?: return@withTransaction OperationResult.Failure(OperationError.NoActiveAssignment)
            if (!BetaClinicalAccessPolicy.canCreateEncounter(actor, draft.area, draft.professionalId, assigned.professionalId)) {
                return@withTransaction OperationResult.Failure(OperationError.NotAuthorized)
            }
            if (!isAvailableProfessional(draft.professionalId, draft.area)) {
                return@withTransaction OperationResult.Failure(OperationError.ProfessionalNotAvailable)
            }

            if (draft.appointmentId != null) {
                val appointment = appointmentDao.getById(draft.appointmentId)
                    ?: return@withTransaction OperationResult.Failure(OperationError.NotFound(EntityKind.APPOINTMENT))
                val coherent = appointment.patientId == draft.patientId &&
                    appointment.areaCode == draft.area.name &&
                    appointment.professionalId == draft.professionalId &&
                    AppointmentStatus.valueOf(appointment.status) in EncounterRules.LINKABLE_APPOINTMENT_STATUSES
                if (!coherent) return@withTransaction OperationResult.Failure(OperationError.AppointmentMismatch)
            }

            val now = clock.instant()
            val issues = EncounterRules.issues(draft.type, draft.eventAt, now)
            if (issues.isNotEmpty()) return@withTransaction OperationResult.Failure(OperationError.InvalidEncounter(issues))

            // Un doble toque llegaría con exactamente los mismos datos: no se guarda dos veces.
            val identical = encounterDao.countIdentical(
                draft.patientId,
                draft.area.name,
                draft.professionalId,
                draft.type.name,
                draft.eventAt.toEpochMilli(),
                draft.appointmentId,
            )
            if (identical > 0) return@withTransaction OperationResult.Failure(OperationError.DuplicateEncounter)

            val encounterId = idGenerator()
            encounterDao.insert(
                ClinicalEncounterEntity(
                    encounterId = encounterId,
                    patientId = draft.patientId,
                    areaCode = draft.area.name,
                    professionalId = draft.professionalId,
                    appointmentId = draft.appointmentId,
                    encounterTypeCode = draft.type.name,
                    // Sin contenido clínico que redactar ni enmiendas definidas, el encuentro base nace completo y de solo lectura.
                    status = EncounterStatus.COMPLETED.name,
                    eventAt = draft.eventAt.toEpochMilli(),
                    recordedAt = now.toEpochMilli(),
                    createdBy = actor.userId,
                    updatedAt = now.toEpochMilli(),
                    updatedBy = actor.userId,
                ),
            )
            audit.record(
                AuditAction.ENCOUNTER_CREATED,
                actorUserId = actor.userId,
                entityType = ENTITY_ENCOUNTER,
                entityId = encounterId,
                patientId = draft.patientId,
                areaCode = draft.area.name,
                metadata = buildMap {
                    put("type", AuditRecorder.text(draft.type.name))
                    put("professionalId", AuditRecorder.text(draft.professionalId))
                    put("eventAt", AuditRecorder.text(draft.eventAt.toString()))
                    draft.appointmentId?.let { put("appointmentId", AuditRecorder.text(it)) }
                },
            )
            OperationResult.Success(requireNotNull(encounterDao.getDetailRow(encounterId)) { "El encuentro $encounterId debería existir" }.toDomain())
        }

    private suspend fun isAvailableProfessional(professionalId: String, area: ClinicalArea): Boolean {
        val professional = userDao.getById(professionalId) ?: return false
        return professional.status == AccountStatus.ACTIVE.name &&
            professional.roleCode == UserRole.PROFESSIONAL.name &&
            professional.areaCode == area.name
    }

    private companion object {
        const val ENTITY_ENCOUNTER = "ENCOUNTER"
    }
}
