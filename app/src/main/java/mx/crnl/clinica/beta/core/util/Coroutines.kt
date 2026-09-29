package mx.crnl.clinica.beta.core.util

import kotlinx.coroutines.CancellationException

/** Como `runCatching`, pero la cancelación de la corrutina se propaga en lugar de convertirse en un fallo. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}
