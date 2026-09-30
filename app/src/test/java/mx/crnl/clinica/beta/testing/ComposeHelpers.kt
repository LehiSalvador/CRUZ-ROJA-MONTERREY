package mx.crnl.clinica.beta.testing

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString

private const val DEFAULT_TIMEOUT_MILLIS = 20_000L

/** Desplaza el contenedor con scroll más cercano hasta el nodo; si no hay ninguno, el nodo ya está a la vista. */
private fun SemanticsNodeInteraction.scrollIntoViewIfNeeded(): SemanticsNodeInteraction {
    runCatching { performScrollTo() }
    return this
}

fun ComposeTestRule.hasTextNow(text: String): Boolean = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

/** Como `waitUntil`, pero al agotarse el tiempo el error incluye la pantalla que sí había. */
private fun ComposeTestRule.waitUntilOrDescribe(what: String, timeoutMillis: Long, condition: () -> Boolean) {
    try {
        waitUntil(timeoutMillis, condition)
    } catch (error: ComposeTimeoutException) {
        throw AssertionError("$what\nPantalla actual:\n${onRoot(useUnmergedTree = false).printToString(maxDepth = 25)}", error)
    }
}

fun ComposeTestRule.waitForText(text: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {
    waitUntilOrDescribe("No apareció «$text»", timeoutMillis) { hasTextNow(text) }
}

/** Espera un nodo cuyo texto contenga [text] (p. ej. una hora dentro de una línea con la fecha). */
fun ComposeTestRule.waitForTextContaining(text: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {
    waitUntilOrDescribe("No apareció un texto que contenga «$text»", timeoutMillis) {
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }
}

fun ComposeTestRule.waitForTextGone(text: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {
    waitUntilOrDescribe("Siguió apareciendo «$text»", timeoutMillis) { !hasTextNow(text) }
}

/** Escribe en el campo cuya etiqueta es [label], reemplazando lo que hubiera. */
fun ComposeTestRule.typeInto(label: String, value: String) {
    val field = hasSetTextAction() and hasText(label)
    waitUntilOrDescribe("No apareció el campo «$label»", DEFAULT_TIMEOUT_MILLIS) { onAllNodes(field).fetchSemanticsNodes().isNotEmpty() }
    onNode(field).scrollIntoViewIfNeeded()
    onNode(field).performTextClearance()
    onNode(field).performTextInput(value)
    // El texto llega al ViewModel y de vuelta a la composición en fotogramas posteriores; sin esperar, una acción
    // inmediata (p. ej. Atrás) vería el formulario todavía vacío.
    waitForIdle()
}

/** Toca el control con [text] (botón, chip, pestaña o tarjeta), desplazando la pantalla si hace falta. */
fun ComposeTestRule.tap(text: String) {
    waitForText(text)
    val target = hasText(text) and hasClickAction()
    onNode(target).scrollIntoViewIfNeeded()
    onNode(target).performClick()
}

fun ComposeTestRule.assertVisible(text: String) {
    waitForText(text)
    onNodeWithText(text).scrollIntoViewIfNeeded().assertIsDisplayed()
}

/** Desplaza la lista principal hasta que un nodo con [text] esté compuesto. */
fun ComposeTestRule.scrollListTo(text: String) {
    onNode(hasScrollAction()).performScrollToNode(hasText(text))
}
