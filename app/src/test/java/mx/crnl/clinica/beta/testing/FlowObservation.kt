package mx.crnl.clinica.beta.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile

/**
 * Sigue una sola suscripción a un flujo de Room: toma su primer valor, ejecuta [change] y sigue recogiendo hasta que
 * un valor cumple [until]. Devuelve todo lo observado. Un flujo que combina varias tablas emite más de una vez por
 * cambio (una por cada tabla invalidada, en orden no garantizado), así que lo que se comprueba es el resultado final
 * y no cuántas emisiones intermedias hubo.
 */
suspend fun <T> Flow<T>.observeAround(change: suspend () -> Unit, until: (T) -> Boolean): List<T> {
    var changed = false
    return transformWhile { value ->
        emit(value)
        if (!changed) {
            changed = true
            change()
            true
        } else {
            !until(value)
        }
    }.toList()
}
