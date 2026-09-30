package mx.crnl.clinica.beta.data.repository

import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.data.local.dao.AssessmentRow
import mx.crnl.clinica.beta.data.local.mapper.toDetail
import mx.crnl.clinica.beta.data.local.mapper.toDomain
import mx.crnl.clinica.beta.data.local.mapper.toSummary
import mx.crnl.clinica.beta.domain.access.BetaClinicalAccessPolicy
import mx.crnl.clinica.beta.domain.access.InterareaAccessPolicy
import mx.crnl.clinica.beta.domain.common.EntityKind
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AccessGrant
import mx.crnl.clinica.beta.domain.model.AdministrationMode
import mx.crnl.clinica.beta.domain.model.AssessmentDetail
import mx.crnl.clinica.beta.domain.model.AssessmentSummary
import mx.crnl.clinica.beta.domain.model.AuditAction
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.SupervisedPreview
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AssessmentRepository

/** Solo lectura de las aplicaciones ya registradas; no crea, calcula ni clasifica nada. */
class LocalAssessmentRepository(
    private val database: ClinicalDatabase,
    private val audit: AuditRecorder,
    private val clock: Clock,
) : AssessmentRepository {
    private val assessmentDao = database.assessmentDao()
    private val userDao = database.userDao()
    private val grants = EffectiveGrantSource(database.accessGrantDao(), clock)

    override fun observePatientAssessments(patientId: String, viewer: UserAccount): Flow<List<AssessmentSummary>> =
        combine(assessmentDao.observeRowsByPatient(patientId), grants.observe(viewer)) { rows, active ->
            val readable = readableAreas(viewer, active, patientId)
            rows.filter { canRead(readable, it) }.map { it.toSummary() }
        }.flowOn(Dispatchers.Default)

    override fun observeAssessment(assessmentId: String, viewer: UserAccount): Flow<AssessmentDetail?> =
        combine(assessmentDao.observeRow(assessmentId), grants.observe(viewer)) { row, active ->
            row
                ?.takeIf { canRead(readableAreas(viewer, active, it.patientId), it) }
                ?.let { found -> found.toDetail(canOpenSupervisedPreview = canOpenSupervised(viewer, found)) }
        }.flowOn(Dispatchers.Default)

    override suspend fun openSupervisedPreview(assessmentId: String, actorUserId: String): OperationResult<SupervisedPreview> {
        val actor = userDao.getById(actorUserId)?.toDomain() ?: return OperationResult.Failure(OperationError.NotAuthorized)
        val row = assessmentDao.getRow(assessmentId) ?: return OperationResult.Failure(OperationError.NotFound(EntityKind.ASSESSMENT))
        if (!canOpenSupervised(actor, row)) return OperationResult.Failure(OperationError.NotAuthorized)
        audit.record(
            AuditAction.SUPERVISED_PREVIEW_OPENED,
            actorUserId = actor.userId,
            entityType = ENTITY_ASSESSMENT,
            entityId = assessmentId,
            patientId = row.patientId,
            areaCode = row.areaCode,
        )
        return OperationResult.Success(SupervisedPreview)
    }

    /** Por rol o por una concesión de lectura vigente sobre ese paciente. */
    private fun readableAreas(viewer: UserAccount, active: List<AccessGrant>, patientId: String): Set<ClinicalArea> =
        BetaClinicalAccessPolicy.viewableAreas(viewer) +
            InterareaAccessPolicy.readableAreas(viewer, active, patientId, clock.instant()).keys

    // Una aplicación sin encuentro no tiene área que atribuir: solo quien lee todas las áreas la recibe.
    private fun canRead(readable: Set<ClinicalArea>, row: AssessmentRow): Boolean =
        row.areaCode?.let { ClinicalArea.valueOf(it) in readable } ?: readable.containsAll(ClinicalArea.entries)

    /** La vista previa del modo supervisado depende del rol, no de un acceso temporal de lectura. */
    private fun canOpenSupervised(actor: UserAccount, row: AssessmentRow): Boolean {
        if (AdministrationMode.valueOf(row.administrationModeCode) != AdministrationMode.SUPERVISED_PATIENT) return false
        return canRead(BetaClinicalAccessPolicy.viewableAreas(actor), row)
    }

    private companion object {
        const val ENTITY_ASSESSMENT = "ASSESSMENT"
    }
}
