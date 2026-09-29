package mx.crnl.clinica.beta.domain.repository

import kotlinx.coroutines.flow.Flow

/** Estado local de sesión y configuración simple; nunca contiene datos clínicos ni credenciales. */
interface SessionRepository {
    /** Usuario con la sesión iniciada; nulo si no hay sesión. No garantiza que la cuenta siga activa. */
    val sessionUserId: Flow<String?>

    suspend fun startSession(userId: String)

    suspend fun endSession()

    /** Versión del conjunto de datos ficticios ya aplicado; 0 si todavía no se ha cargado. */
    suspend fun appliedSeedVersion(): Int

    suspend fun recordAppliedSeedVersion(version: Int)
}
