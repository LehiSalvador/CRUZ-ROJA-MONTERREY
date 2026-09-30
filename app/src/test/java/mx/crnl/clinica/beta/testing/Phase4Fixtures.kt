package mx.crnl.clinica.beta.testing

import java.time.Instant
import mx.crnl.clinica.beta.domain.model.AccessGrant
import mx.crnl.clinica.beta.domain.model.AccessGrantStatus
import mx.crnl.clinica.beta.domain.model.AccessRequest
import mx.crnl.clinica.beta.domain.model.AccessScope
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.AdministrationMode
import mx.crnl.clinica.beta.domain.model.AssessmentDetail
import mx.crnl.clinica.beta.domain.model.AssessmentInstrument
import mx.crnl.clinica.beta.domain.model.AssessmentStatus
import mx.crnl.clinica.beta.domain.model.AssessmentSummary
import mx.crnl.clinica.beta.domain.model.AssignmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ManagedAccount
import mx.crnl.clinica.beta.domain.model.PatientAssignment
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeContext
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeRequest
import mx.crnl.clinica.beta.domain.model.ProfessionalOption
import mx.crnl.clinica.beta.domain.model.RequestStatus
import mx.crnl.clinica.beta.domain.model.UserRole

val PhaseNow: Instant = TestNow.toInstant()

fun managedAccount(
    id: String,
    status: AccountStatus = AccountStatus.PENDING_APPROVAL,
    role: UserRole = UserRole.PROFESSIONAL,
    area: ClinicalArea? = ClinicalArea.PSYCHOLOGY,
    requestedAt: Instant = PhaseNow,
) = ManagedAccount(userAccount(id = id, role = role, area = area, status = status, firstName = id), requestedAt)

fun accessRequestOf(
    id: String = "r1",
    requester: String = "mariana",
    ownerArea: ClinicalArea = ClinicalArea.NUTRITION,
    status: RequestStatus = RequestStatus.PENDING,
    grant: AccessGrant? = null,
    patientId: String = "p1",
) = AccessRequest(
    requestId = id,
    patientId = patientId,
    patientName = "Fernanda Guerra Domínguez",
    patientNumber = "CRNL-000003",
    requesterId = requester,
    requesterName = "Mariana Elizondo",
    requesterArea = ClinicalArea.PSYCHOLOGY,
    ownerArea = ownerArea,
    reason = "Necesito consultar el seguimiento",
    scope = AccessScope.READ,
    requestedAt = PhaseNow,
    status = status,
    reviewerName = null,
    reviewedAt = null,
    grant = grant,
)

fun grantOf(
    id: String = "g1",
    grantee: String = "mariana",
    area: ClinicalArea = ClinicalArea.NUTRITION,
    from: Instant = PhaseNow,
    until: Instant = PhaseNow.plusSeconds(7 * 86_400L),
    status: AccessGrantStatus = AccessGrantStatus.ACTIVE,
    patientId: String = "p1",
) = AccessGrant(id, "r1", patientId, grantee, area, AccessScope.READ, from, until, status, revokedAt = null)

fun changeRequestOf(
    id: String = "o1",
    requester: String = "rodrigo",
    area: ClinicalArea = ClinicalArea.PSYCHOLOGY,
    status: RequestStatus = RequestStatus.PENDING,
    upcoming: Int = 0,
) = ProfessionalChangeRequest(
    requestId = id,
    patientId = "p1",
    patientName = "Fernanda Guerra Domínguez",
    patientNumber = "CRNL-000003",
    area = area,
    currentAssignmentId = "a1",
    currentProfessionalId = "rodrigo",
    currentProfessionalName = "Rodrigo Villarreal",
    requestedProfessionalId = "mariana",
    requestedProfessionalName = "Mariana Elizondo",
    requesterId = requester,
    requesterName = "Rodrigo Villarreal",
    reason = "Cambio por carga de agenda",
    requestedAt = PhaseNow,
    status = status,
    reviewerName = null,
    reviewedAt = null,
    resolutionReason = null,
    upcomingAppointmentCount = upcoming,
)

fun changeContextOf(pendingRequestId: String? = null, upcoming: Int = 0) = ProfessionalChangeContext(
    patientId = "p1",
    patientName = "Fernanda Guerra Domínguez",
    patientNumber = "CRNL-000003",
    area = ClinicalArea.PSYCHOLOGY,
    currentAssignment = PatientAssignment(
        "a1", ClinicalArea.PSYCHOLOGY, "rodrigo", "Rodrigo Villarreal", PhaseNow.minusSeconds(86_400), null, AssignmentStatus.ACTIVE, null, "Claudia",
    ),
    candidates = listOf(ProfessionalOption("mariana", "Mariana Elizondo Cantú", "00000103", ClinicalArea.PSYCHOLOGY)),
    upcomingAppointmentCount = upcoming,
    pendingRequestId = pendingRequestId,
)

fun assessmentDetailOf(
    id: String = "s1",
    area: ClinicalArea? = ClinicalArea.PSYCHOLOGY,
    mode: AdministrationMode = AdministrationMode.SUPERVISED_PATIENT,
    canOpen: Boolean = true,
) = AssessmentDetail(
    summary = AssessmentSummary(
        assessmentId = id,
        area = area,
        professionalName = "Rodrigo Villarreal",
        status = AssessmentStatus.COMPLETED,
        startedAt = PhaseNow,
        completedAt = PhaseNow.plusSeconds(1_200),
        instrument = AssessmentInstrument.PLACEHOLDER_A,
        instrumentVersion = "0",
        mode = mode,
        hasResult = true,
        rawScore = 12.0,
    ),
    patientId = "p1",
    patientName = "Diego Alejandro Treviño Salinas",
    patientNumber = "CRNL-000002",
    encounterId = "e1",
    scoringVersion = "placeholder-0",
    canOpenSupervisedPreview = canOpen,
)
