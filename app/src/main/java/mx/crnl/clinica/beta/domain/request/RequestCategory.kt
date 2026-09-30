package mx.crnl.clinica.beta.domain.request

import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole

/** Bandejas de la pestaña Solicitudes. Cada persona ve solo las que su rol usa. */
enum class RequestCategory { ACCOUNTS, ACCESS, CHANGES }

object RequestCategories {
    /**
     * Profesionales: sus solicitudes de acceso y de cambio. Coordinación y administración clínica: además las cuentas.
     * El administrador del sistema solo ve cuentas: las solicitudes de acceso y cambio están ligadas a pacientes.
     */
    fun forUser(user: UserAccount): List<RequestCategory> = when {
        user.status != AccountStatus.ACTIVE -> emptyList()
        user.role == UserRole.PROFESSIONAL -> listOf(RequestCategory.ACCESS, RequestCategory.CHANGES)
        user.role == UserRole.AREA_COORDINATOR || user.role == UserRole.CLINICAL_ADMIN ->
            listOf(RequestCategory.ACCOUNTS, RequestCategory.ACCESS, RequestCategory.CHANGES)
        user.role == UserRole.SYSTEM_ADMIN -> listOf(RequestCategory.ACCOUNTS)
        else -> emptyList()
    }

    /** Estados de cuenta que aparecen en la bandeja de cuentas: las pendientes y las rechazadas como historial. */
    val ACCOUNT_STATUSES: Set<AccountStatus> = setOf(AccountStatus.PENDING_APPROVAL, AccountStatus.REJECTED)
}
