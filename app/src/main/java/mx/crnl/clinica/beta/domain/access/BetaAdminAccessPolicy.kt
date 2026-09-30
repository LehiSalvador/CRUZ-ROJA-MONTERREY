package mx.crnl.clinica.beta.domain.access

import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole

/**
 * Superficies de administración de la Beta local. La bitácora no lleva contenido clínico, así que administración del
 * sistema puede consultarla; restablecer los datos borra información clínica ficticia y queda en administración clínica.
 */
object BetaAdminAccessPolicy {
    fun canViewAudit(user: UserAccount): Boolean =
        user.status == AccountStatus.ACTIVE && (user.role == UserRole.CLINICAL_ADMIN || user.role == UserRole.SYSTEM_ADMIN)

    fun canResetBetaData(user: UserAccount): Boolean = user.status == AccountStatus.ACTIVE && user.role == UserRole.CLINICAL_ADMIN

    /** Alguna herramienta de administración disponible: decide si el perfil muestra la sección. */
    fun hasAdministration(user: UserAccount): Boolean =
        BetaUserAdministrationPolicy.canViewUserDirectory(user) || canViewAudit(user) || canResetBetaData(user)
}
