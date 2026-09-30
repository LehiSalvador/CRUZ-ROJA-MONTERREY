package mx.crnl.clinica.beta.domain.access

import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole

/** Qué citas puede ver una persona en la agenda. */
sealed interface AppointmentScope {
    data object None : AppointmentScope

    data object All : AppointmentScope

    data class InArea(val area: ClinicalArea) : AppointmentScope

    data class OwnAsProfessional(val userId: String) : AppointmentScope

    fun accepts(area: ClinicalArea, professionalId: String): Boolean = when (this) {
        None -> false
        All -> true
        is InArea -> this.area == area
        is OwnAsProfessional -> userId == professionalId
    }
}

/**
 * Autorización local de la Beta para las operaciones de citas, asignaciones y encuentros. Es una defensa de la
 * aplicación local, reemplazable y deliberadamente pequeña: NO es la matriz institucional final ni seguridad de
 * producción (no existe backend que la respalde). Solo una cuenta activa puede algo, y el administrador del sistema
 * no obtiene capacidad clínica por su rol técnico.
 */
object BetaClinicalAccessPolicy {
    fun viewableAreas(user: UserAccount): Set<ClinicalArea> = when {
        !user.isActive() -> emptySet()
        user.role == UserRole.CLINICAL_ADMIN -> ClinicalArea.entries.toSet()
        user.role == UserRole.PROFESSIONAL || user.role == UserRole.AREA_COORDINATOR -> setOfNotNull(user.area)
        else -> emptySet()
    }

    fun canViewAreaDetail(user: UserAccount, area: ClinicalArea): Boolean = area in viewableAreas(user)

    /** Áreas en las que la persona puede agendar y gestionar citas (según su rol; al profesional le falta además ser el titular). */
    fun manageableAreas(user: UserAccount): Set<ClinicalArea> = viewableAreas(user)

    fun canCreateAssignment(user: UserAccount, area: ClinicalArea): Boolean = when {
        !user.isActive() -> false
        user.role == UserRole.CLINICAL_ADMIN -> true
        user.role == UserRole.AREA_COORDINATOR -> user.area == area
        else -> false
    }

    fun canManageAppointment(user: UserAccount, area: ClinicalArea, appointmentProfessionalId: String): Boolean = when {
        !user.isActive() -> false
        user.role == UserRole.CLINICAL_ADMIN -> true
        user.role == UserRole.AREA_COORDINATOR -> user.area == area
        user.role == UserRole.PROFESSIONAL -> user.area == area && user.userId == appointmentProfessionalId
        else -> false
    }

    /**
     * El encuentro se atribuye siempre al profesional que tiene asignado el paciente en el área; sin asignación no hay
     * a quién atribuirlo. Un profesional solo lo registra siendo ese profesional; coordinación, dentro de su área.
     */
    fun canCreateEncounter(
        user: UserAccount,
        area: ClinicalArea,
        professionalId: String,
        assignedProfessionalId: String?,
    ): Boolean = when {
        !user.isActive() -> false
        assignedProfessionalId == null || professionalId != assignedProfessionalId -> false
        user.role == UserRole.CLINICAL_ADMIN -> true
        user.role == UserRole.AREA_COORDINATOR -> user.area == area
        user.role == UserRole.PROFESSIONAL -> user.area == area && user.userId == professionalId
        else -> false
    }

    fun appointmentScope(user: UserAccount): AppointmentScope = when {
        !user.isActive() -> AppointmentScope.None
        user.role == UserRole.CLINICAL_ADMIN -> AppointmentScope.All
        user.role == UserRole.AREA_COORDINATOR -> user.area?.let(AppointmentScope::InArea) ?: AppointmentScope.None
        user.role == UserRole.PROFESSIONAL -> AppointmentScope.OwnAsProfessional(user.userId)
        else -> AppointmentScope.None
    }

    private fun UserAccount.isActive(): Boolean = status == AccountStatus.ACTIVE
}
