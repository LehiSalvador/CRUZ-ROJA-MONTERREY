package mx.crnl.clinica.beta.domain.access

import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole

/**
 * Quién pide y quién resuelve un cambio formal de profesional. La asignación vigente nunca se edita: aprobar cierra
 * la anterior y abre otra. Nadie resuelve su propia solicitud. Matriz provisional de la Beta local, no institucional.
 */
object ProfessionalChangePolicy {
    const val MIN_REASON_LENGTH = 10
    const val MAX_REASON_LENGTH = 300

    fun isValidReason(reason: String): Boolean = reason.trim().length in MIN_REASON_LENGTH..MAX_REASON_LENGTH

    /** El profesional asignado, la coordinación del área o administración clínica. */
    fun canRequest(user: UserAccount, area: ClinicalArea, currentProfessionalId: String): Boolean = when {
        user.status != AccountStatus.ACTIVE -> false
        user.role == UserRole.CLINICAL_ADMIN -> true
        user.role == UserRole.AREA_COORDINATOR -> user.area == area
        user.role == UserRole.PROFESSIONAL -> user.area == area && user.userId == currentProfessionalId
        else -> false
    }

    /** Coordinación del área o administración clínica, y nunca quien la pidió. */
    fun canReview(reviewer: UserAccount, area: ClinicalArea, requesterId: String): Boolean =
        reviewer.userId != requesterId && canManageArea(reviewer, area)

    /** Quién ve la solicitud en su bandeja: quien la pidió y quienes la pueden revisar. El sistema no ve solicitudes de pacientes. */
    fun canSee(viewer: UserAccount, area: ClinicalArea, requesterId: String): Boolean =
        viewer.status == AccountStatus.ACTIVE &&
            viewer.role != UserRole.SYSTEM_ADMIN &&
            (viewer.userId == requesterId || canManageArea(viewer, area))

    /** El sustituto debe ser un profesional activo del mismo área y distinto del vigente. */
    fun isValidCandidate(candidate: UserAccount, area: ClinicalArea, currentProfessionalId: String): Boolean =
        candidate.userId != currentProfessionalId &&
            candidate.status == AccountStatus.ACTIVE &&
            candidate.role == UserRole.PROFESSIONAL &&
            candidate.area == area

    private fun canManageArea(user: UserAccount, area: ClinicalArea): Boolean = user.status == AccountStatus.ACTIVE &&
        (user.role == UserRole.CLINICAL_ADMIN || (user.role == UserRole.AREA_COORDINATOR && user.area == area))
}
