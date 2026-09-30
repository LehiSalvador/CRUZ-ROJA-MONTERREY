package mx.crnl.clinica.beta.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import mx.crnl.clinica.beta.domain.access.AccountAction
import mx.crnl.clinica.beta.domain.access.BetaUserAdministrationPolicy
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AccessRequest
import mx.crnl.clinica.beta.domain.model.AccessRequestContext
import mx.crnl.clinica.beta.domain.model.AccessRequestDraft
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.AssessmentDetail
import mx.crnl.clinica.beta.domain.model.AssessmentSummary
import mx.crnl.clinica.beta.domain.model.AuditFilter
import mx.crnl.clinica.beta.domain.model.AuditRecord
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ManagedAccount
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeContext
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeDraft
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeRequest
import mx.crnl.clinica.beta.domain.model.SupervisedPreview
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AccessRequestRepository
import mx.crnl.clinica.beta.domain.repository.AccountAdministrationRepository
import mx.crnl.clinica.beta.domain.repository.AssessmentRepository
import mx.crnl.clinica.beta.domain.repository.AuditRepository
import mx.crnl.clinica.beta.domain.repository.BetaMaintenanceRepository
import mx.crnl.clinica.beta.domain.repository.BetaResetSummary
import mx.crnl.clinica.beta.domain.repository.ProfessionalChangeRepository

/** Cuentas en memoria con la política real de administración; suficiente para probar pantallas sin Room. */
class FakeAccountAdministrationRepository(initial: List<ManagedAccount> = emptyList()) : AccountAdministrationRepository {
    private val accounts = MutableStateFlow(initial)
    val applied = mutableListOf<Triple<String, AccountAction, String>>()

    override fun observeAccounts(viewer: UserAccount): Flow<List<ManagedAccount>> =
        accounts.map { list -> list.filter { BetaUserAdministrationPolicy.canSee(viewer, it.user) } }

    override fun observeAccount(userId: String, viewer: UserAccount): Flow<ManagedAccount?> =
        accounts.map { list -> list.firstOrNull { it.user.userId == userId }?.takeIf { BetaUserAdministrationPolicy.canSee(viewer, it.user) } }

    override suspend fun apply(
        targetUserId: String,
        action: AccountAction,
        expectedStatus: AccountStatus,
        actorUserId: String,
    ): OperationResult<ManagedAccount> {
        applied += Triple(targetUserId, action, actorUserId)
        val target = accounts.value.firstOrNull { it.user.userId == targetUserId }
            ?: return OperationResult.Failure(OperationError.NotAuthorized)
        val updated = target.copy(user = target.user.copy(status = action.resultingStatus))
        accounts.value = accounts.value.map { if (it.user.userId == targetUserId) updated else it }
        return OperationResult.Success(updated)
    }
}

/** Solicitudes de acceso configurables: lo que devuelve el contexto y el resultado de crear se fija desde la prueba. */
class FakeAccessRequestRepository(
    var context: AccessRequestContext? = null,
    var requests: List<AccessRequest> = emptyList(),
) : AccessRequestRepository {
    val drafts = mutableListOf<AccessRequestDraft>()
    var createError: OperationError? = null
    var failWithException = false
    val approvals = mutableListOf<Pair<String, Int>>()
    val rejections = mutableListOf<String>()
    val revocations = mutableListOf<String>()

    override fun observeRequests(viewer: UserAccount): Flow<List<AccessRequest>> = flowOf(requests)

    override fun observeRequest(requestId: String, viewer: UserAccount): Flow<AccessRequest?> =
        flowOf(requests.firstOrNull { it.requestId == requestId })

    override suspend fun getRequestContext(patientId: String, area: ClinicalArea, viewer: UserAccount): AccessRequestContext? = context

    override suspend fun create(draft: AccessRequestDraft, actorUserId: String): OperationResult<AccessRequest> {
        if (failWithException) throw java.io.IOException("fallo simulado")
        drafts += draft
        createError?.let { return OperationResult.Failure(it) }
        return OperationResult.Success(requests.firstOrNull() ?: error("configura requests para el resultado"))
    }

    override suspend fun approve(requestId: String, durationDays: Int, actorUserId: String): OperationResult<AccessRequest> {
        approvals += requestId to durationDays
        return OperationResult.Success(requests.first { it.requestId == requestId })
    }

    override suspend fun reject(requestId: String, actorUserId: String): OperationResult<AccessRequest> {
        rejections += requestId
        return OperationResult.Success(requests.first { it.requestId == requestId })
    }

    override suspend fun revokeGrant(grantId: String, actorUserId: String): OperationResult<AccessRequest> {
        revocations += grantId
        return OperationResult.Success(requests.first())
    }
}

/** Cambios de profesional configurables desde la prueba. */
class FakeProfessionalChangeRepository(
    var context: ProfessionalChangeContext? = null,
    var requests: List<ProfessionalChangeRequest> = emptyList(),
) : ProfessionalChangeRepository {
    val drafts = mutableListOf<ProfessionalChangeDraft>()
    var createError: OperationError? = null
    val approvals = mutableListOf<String>()
    val rejections = mutableListOf<Pair<String, String?>>()

    override fun observeRequests(viewer: UserAccount): Flow<List<ProfessionalChangeRequest>> = flowOf(requests)

    override fun observeRequest(requestId: String, viewer: UserAccount): Flow<ProfessionalChangeRequest?> =
        flowOf(requests.firstOrNull { it.requestId == requestId })

    override suspend fun getFormContext(patientId: String, area: ClinicalArea, viewer: UserAccount): ProfessionalChangeContext? = context

    override suspend fun create(draft: ProfessionalChangeDraft, actorUserId: String): OperationResult<ProfessionalChangeRequest> {
        drafts += draft
        createError?.let { return OperationResult.Failure(it) }
        return OperationResult.Success(requests.firstOrNull() ?: error("configura requests para el resultado"))
    }

    override suspend fun approve(requestId: String, actorUserId: String): OperationResult<ProfessionalChangeRequest> {
        approvals += requestId
        return OperationResult.Success(requests.first { it.requestId == requestId })
    }

    override suspend fun reject(requestId: String, resolutionReason: String?, actorUserId: String): OperationResult<ProfessionalChangeRequest> {
        rejections += requestId to resolutionReason
        return OperationResult.Success(requests.first { it.requestId == requestId })
    }
}

class FakeAssessmentRepository(
    private val details: Map<String, AssessmentDetail> = emptyMap(),
    private val allowPreview: Boolean = true,
) : AssessmentRepository {
    val previewsOpened = mutableListOf<String>()

    override fun observePatientAssessments(patientId: String, viewer: UserAccount): Flow<List<AssessmentSummary>> =
        flowOf(details.values.filter { it.patientId == patientId }.map { it.summary })

    override fun observeAssessment(assessmentId: String, viewer: UserAccount): Flow<AssessmentDetail?> = flowOf(details[assessmentId])

    override suspend fun openSupervisedPreview(assessmentId: String, actorUserId: String): OperationResult<SupervisedPreview> {
        if (!allowPreview) return OperationResult.Failure(OperationError.NotAuthorized)
        previewsOpened += assessmentId
        return OperationResult.Success(SupervisedPreview)
    }
}

class FakeAuditRepository(private val entries: List<AuditRecord> = emptyList()) : AuditRepository {
    override fun observeEntries(filter: AuditFilter, viewer: UserAccount): Flow<List<AuditRecord>> =
        flowOf(entries.filter { record -> filter.category?.accepts(record.actionCode) ?: true })
}

class FakeMaintenanceRepository(private val allow: Boolean = true) : BetaMaintenanceRepository {
    var resets = 0
        private set

    override suspend fun resetBetaData(actorUserId: String): OperationResult<BetaResetSummary> {
        if (!allow) return OperationResult.Failure(OperationError.NotAuthorized)
        resets += 1
        return OperationResult.Success(BetaResetSummary(users = 10, patients = 8, appointments = 12, assessments = 4))
    }
}
