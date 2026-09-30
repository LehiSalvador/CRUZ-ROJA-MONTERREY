package mx.crnl.clinica.beta.domain.model

import java.time.Instant

/** Cuenta con la fecha en que se solicitó, tal como la revisa quien administra usuarios. */
data class ManagedAccount(val user: UserAccount, val requestedAt: Instant)

/** Concesión de lectura de un área de un paciente para una persona, con su vigencia. */
data class AccessGrant(
    val grantId: String,
    val requestId: String,
    val patientId: String,
    val granteeUserId: String,
    val ownerArea: ClinicalArea,
    val scope: AccessScope,
    val validFrom: Instant,
    val expiresAt: Instant,
    /** Solo ACTIVE o REVOKED: el vencimiento se deduce con [statusAt]. */
    val storedStatus: AccessGrantStatus,
    val revokedAt: Instant?,
) {
    fun statusAt(now: Instant): AccessGrantStatus = when {
        storedStatus == AccessGrantStatus.REVOKED -> AccessGrantStatus.REVOKED
        !now.isBefore(expiresAt) -> AccessGrantStatus.EXPIRED
        else -> AccessGrantStatus.ACTIVE
    }
}

/** Solicitud de acceso de lectura a un área ajena. El motivo es administrativo: nunca lleva información clínica. */
data class AccessRequest(
    val requestId: String,
    val patientId: String,
    val patientName: String,
    val patientNumber: String,
    val requesterId: String,
    val requesterName: String,
    val requesterArea: ClinicalArea?,
    val ownerArea: ClinicalArea,
    val reason: String,
    val scope: AccessScope,
    val requestedAt: Instant,
    val status: RequestStatus,
    val reviewerName: String?,
    val reviewedAt: Instant?,
    val grant: AccessGrant?,
)

data class AccessRequestDraft(val patientId: String, val ownerArea: ClinicalArea, val reason: String)

/** Lo que el formulario de solicitud de acceso necesita saber antes de mostrarse. */
data class AccessRequestContext(
    val patientId: String,
    val patientName: String,
    val patientNumber: String,
    val ownerArea: ClinicalArea,
    /** Ya hay una solicitud propia sin resolver para este paciente y área. */
    val alreadyPending: Boolean,
)

/** Solicitud formal de cambio del profesional asignado a un paciente en un área. */
data class ProfessionalChangeRequest(
    val requestId: String,
    val patientId: String,
    val patientName: String,
    val patientNumber: String,
    val area: ClinicalArea,
    val currentAssignmentId: String,
    val currentProfessionalId: String,
    val currentProfessionalName: String,
    val requestedProfessionalId: String,
    val requestedProfessionalName: String,
    val requesterId: String,
    val requesterName: String,
    val reason: String,
    val requestedAt: Instant,
    val status: RequestStatus,
    val reviewerName: String?,
    val reviewedAt: Instant?,
    val resolutionReason: String?,
    /** Citas por ocurrir con el profesional actual: el cambio no las reasigna. Solo se calcula mientras está pendiente. */
    val upcomingAppointmentCount: Int,
)

data class ProfessionalChangeDraft(
    val patientId: String,
    val area: ClinicalArea,
    val requestedProfessionalId: String,
    val reason: String,
)

/** Lo que el formulario de cambio de profesional necesita: el vigente y quiénes pueden sustituirlo. */
data class ProfessionalChangeContext(
    val patientId: String,
    val patientName: String,
    val patientNumber: String,
    val area: ClinicalArea,
    val currentAssignment: PatientAssignment,
    val candidates: List<ProfessionalOption>,
    val upcomingAppointmentCount: Int,
    val pendingRequestId: String?,
)
