package mx.crnl.clinica.beta.domain.repository

import kotlinx.coroutines.flow.Flow

/** Estado simple de sesión y configuración local; nunca contiene datos clínicos ni credenciales. */
interface SessionRepository {
    val isSessionActive: Flow<Boolean>

    suspend fun startSession()

    suspend fun endSession()

    /** Versión del conjunto de datos ficticios ya aplicado; 0 si todavía no se ha cargado. */
    suspend fun appliedSeedVersion(): Int

    suspend fun recordAppliedSeedVersion(version: Int)
}
