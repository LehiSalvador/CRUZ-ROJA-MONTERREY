package mx.crnl.clinica.beta.domain.repository

import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.domain.account.AccountRequest
import mx.crnl.clinica.beta.domain.account.AccountRequestField
import mx.crnl.clinica.beta.domain.account.AccountRequestIssue
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.UserAccount

sealed interface SignInResult {
    data class Success(val user: UserAccount) : SignInResult

    /** Correo inexistente o contraseña incorrecta; no se distingue a propósito. */
    data object InvalidCredentials : SignInResult

    /** Credenciales correctas de una cuenta que no puede entrar: pendiente, suspendida, rechazada o inactiva. */
    data class AccountNotActive(val status: AccountStatus) : SignInResult
}

sealed interface AccountRequestResult {
    data object Submitted : AccountRequestResult

    data class Invalid(val issues: Map<AccountRequestField, AccountRequestIssue>) : AccountRequestResult

    data object EmailAlreadyRegistered : AccountRequestResult
}

/**
 * Autenticación local de la Beta: verifica credenciales de cuentas ficticias contra el almacenamiento local.
 * No es seguridad de producción; el backend real sustituirá esta implementación.
 */
interface AuthRepository {
    /** Usuario de la sesión vigente. Emite nulo si no hay sesión o si la cuenta dejó de estar activa. */
    val currentUser: Flow<UserAccount?>

    /** Valida la sesión guardada; si apunta a una cuenta inexistente o no activa, la limpia. */
    suspend fun restoreSession(): UserAccount?

    suspend fun signIn(email: String, password: String): SignInResult

    suspend fun signOut()

    /** Registra la solicitud como PENDING_APPROVAL, sin iniciar sesión ni aprobar nada. */
    suspend fun requestAccount(request: AccountRequest): AccountRequestResult
}
