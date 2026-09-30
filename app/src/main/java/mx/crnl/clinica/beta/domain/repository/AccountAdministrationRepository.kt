package mx.crnl.clinica.beta.domain.repository

import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.domain.access.AccountAction
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ManagedAccount
import mx.crnl.clinica.beta.domain.model.UserAccount

/**
 * Revisión y administración de cuentas de la Beta local. Nunca borra cuentas ni toca credenciales: solo cambia su
 * estado, con la política [mx.crnl.clinica.beta.domain.access.BetaUserAdministrationPolicy], y lo deja en la bitácora.
 */
interface AccountAdministrationRepository {
    /** Las cuentas que [viewer] puede consultar, con la solicitud más reciente primero. Vacío si no administra cuentas. */
    fun observeAccounts(viewer: UserAccount): Flow<List<ManagedAccount>>

    /** La cuenta; emite nulo si no existe o si [viewer] no puede consultarla. */
    fun observeAccount(userId: String, viewer: UserAccount): Flow<ManagedAccount?>

    /**
     * Aplica [action] a la cuenta si [actorUserId] puede y la cuenta sigue en [expectedStatus] (la que la persona veía);
     * así dos revisiones simultáneas no duplican el cambio ni su auditoría.
     */
    suspend fun apply(
        targetUserId: String,
        action: AccountAction,
        expectedStatus: AccountStatus,
        actorUserId: String,
    ): OperationResult<ManagedAccount>
}
