package mx.crnl.clinica.beta.testing

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
