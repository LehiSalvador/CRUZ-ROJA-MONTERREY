package mx.crnl.clinica.beta.feature.requests

import mx.crnl.clinica.beta.domain.common.OperationError

/** Qué se resolvió, para confirmarlo en pantalla con un texto claro. */
enum class ReviewOutcome {
    ACCOUNT_APPROVED,
    ACCOUNT_REJECTED,
    ACCOUNT_SUSPENDED,
    ACCOUNT_REACTIVATED,
    ACCESS_APPROVED,
    ACCESS_REJECTED,
    GRANT_REVOKED,
    CHANGE_APPROVED,
    CHANGE_REJECTED,
}

/** Estado de una acción de revisión (aprobar, rechazar, suspender…) desde una pantalla de detalle. */
sealed interface ReviewState {
    data object Idle : ReviewState

    data object Working : ReviewState

    data class Failed(val error: OperationError) : ReviewState

    /** Falló algo inesperado (no un rechazo de las reglas): se pide reintentar sin mostrar detalles técnicos. */
    data object Unavailable : ReviewState

    data class Done(val outcome: ReviewOutcome) : ReviewState
}
