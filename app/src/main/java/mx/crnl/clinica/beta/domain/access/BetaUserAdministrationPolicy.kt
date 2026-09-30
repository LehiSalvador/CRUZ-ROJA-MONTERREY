package mx.crnl.clinica.beta.domain.access

import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole

/** Lo que una persona puede hacerle a la cuenta de otra. */
enum class AccountAction(val requiredStatus: AccountStatus, val resultingStatus: AccountStatus) {
    APPROVE(AccountStatus.PENDING_APPROVAL, AccountStatus.ACTIVE),
    REJECT(AccountStatus.PENDING_APPROVAL, AccountStatus.REJECTED),
    SUSPEND(AccountStatus.ACTIVE, AccountStatus.SUSPENDED),
    REACTIVATE(AccountStatus.SUSPENDED, AccountStatus.ACTIVE),
}

/**
 * Quién revisa y administra cuentas en la Beta local. Es una defensa de la aplicación, reemplazable y deliberadamente
 * pequeña: NO es la matriz institucional final. Nadie actúa sobre su propia cuenta, y el administrador del sistema
 * puede consultar cuentas pero no otorgar acceso, porque aprobar una cuenta clínica concede acceso clínico.
 */
object BetaUserAdministrationPolicy {
    /** Quién puede abrir la lista completa de usuarios. Administración clínica gestiona; el sistema solo consulta. */
    fun canViewUserDirectory(actor: UserAccount): Boolean =
        actor.status == AccountStatus.ACTIVE && (actor.role == UserRole.CLINICAL_ADMIN || actor.role == UserRole.SYSTEM_ADMIN)

    fun canManageUsers(actor: UserAccount): Boolean = actor.status == AccountStatus.ACTIVE && actor.role == UserRole.CLINICAL_ADMIN

    /** Si la cuenta [target] aparece para [actor] entre las que puede consultar. */
    fun canSee(actor: UserAccount, target: UserAccount): Boolean = when {
        actor.status != AccountStatus.ACTIVE -> false
        actor.role == UserRole.CLINICAL_ADMIN || actor.role == UserRole.SYSTEM_ADMIN -> true
        actor.role == UserRole.AREA_COORDINATOR ->
            target.role == UserRole.PROFESSIONAL && target.area != null && target.area == actor.area &&
                target.status in COORDINATOR_VISIBLE_STATUSES
        else -> false
    }

    /** Acciones que [actor] puede aplicar hoy a [target], según el estado en que está. */
    fun availableActions(actor: UserAccount, target: UserAccount): Set<AccountAction> =
        AccountAction.entries.filterTo(mutableSetOf()) { canApply(actor, target, it) }

    fun canApply(actor: UserAccount, target: UserAccount, action: AccountAction): Boolean {
        if (actor.status != AccountStatus.ACTIVE || actor.userId == target.userId) return false
        if (target.status != action.requiredStatus) return false
        return when (action) {
            AccountAction.APPROVE, AccountAction.REJECT -> canReview(actor, target)
            AccountAction.SUSPEND, AccountAction.REACTIVATE -> actor.role == UserRole.CLINICAL_ADMIN && target.isClinicalStaff()
        }
    }

    private fun canReview(actor: UserAccount, target: UserAccount): Boolean = when (actor.role) {
        UserRole.CLINICAL_ADMIN -> target.isClinicalStaff()
        UserRole.AREA_COORDINATOR -> target.role == UserRole.PROFESSIONAL && target.area != null && target.area == actor.area
        UserRole.PROFESSIONAL, UserRole.SYSTEM_ADMIN -> false
    }

    /** Solo profesionales y coordinación de un área clínica se aprueban, suspenden o reactivan aquí. */
    private fun UserAccount.isClinicalStaff(): Boolean =
        (role == UserRole.PROFESSIONAL || role == UserRole.AREA_COORDINATOR) && area != null

    private val COORDINATOR_VISIBLE_STATUSES = setOf(AccountStatus.PENDING_APPROVAL, AccountStatus.REJECTED)
}
