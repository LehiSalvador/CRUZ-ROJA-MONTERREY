package mx.crnl.clinica.beta.testing

import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult

/** Como `assertFailsWith`, pero admite llamadas suspendidas dentro del bloque (es inline). */
inline fun <reified T : Throwable> assertFailsWithType(block: () -> Unit): T {
    try {
        block()
    } catch (error: Throwable) {
        if (error is T) return error
        throw AssertionError("Se esperaba ${T::class.simpleName} pero se lanzó ${error::class.simpleName}: ${error.message}", error)
    }
    throw AssertionError("Se esperaba ${T::class.simpleName} pero no se lanzó ninguna excepción")
}

/** El valor de un resultado que debió salir bien; si falló, el error dice por qué. */
fun <T> OperationResult<T>.success(): T = when (this) {
    is OperationResult.Success -> value
    is OperationResult.Failure -> throw AssertionError("Se esperaba éxito pero falló con $error")
}

/** El error de un resultado que debió fallar con exactamente [E]. */
inline fun <reified E : OperationError> OperationResult<*>.failure(): E = when (this) {
    is OperationResult.Success -> throw AssertionError("Se esperaba ${E::class.simpleName} pero salió bien: $value")
    is OperationResult.Failure -> error as? E
        ?: throw AssertionError("Se esperaba ${E::class.simpleName} pero falló con $error")
}
