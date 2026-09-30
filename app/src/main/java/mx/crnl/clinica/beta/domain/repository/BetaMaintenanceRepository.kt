package mx.crnl.clinica.beta.domain.repository

import mx.crnl.clinica.beta.domain.common.OperationResult

/** Recuento de lo que quedó cargado tras restablecer los datos de la Beta. */
data class BetaResetSummary(val users: Int, val patients: Int, val appointments: Int, val assessments: Int)

/** Herramientas propias de la Beta local. No existirán con un backend real. */
interface BetaMaintenanceRepository {
    /**
     * Elimina los cambios locales y vuelve a cargar el conjunto de datos ficticios inicial, y termina la sesión. Solo lo
     * permite administración clínica; no toca archivos externos ni ninguna configuración fuera de la base de la Beta.
     */
    suspend fun resetBetaData(actorUserId: String): OperationResult<BetaResetSummary>
}
