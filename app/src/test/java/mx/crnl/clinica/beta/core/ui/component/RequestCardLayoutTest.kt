package mx.crnl.clinica.beta.core.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.crnl.clinica.beta.core.ui.theme.ClinicalTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Con fuentes grandes el chip de estado de una solicitud se llevaba media tarjeta y partía el nombre a mitad de palabra
 * («Cisnero» / «s»): a partir de 1,3 el chip va debajo del título y el nombre usa todo el ancho.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class RequestCardLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun show(fontScale: Float) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                ClinicalTheme {
                    RequestCard(
                        title = TITLE,
                        lines = listOf("Profesional · Nutrición"),
                        statusLabel = CHIP,
                        statusTone = StatusTone.Warning,
                        onClick = {},
                    )
                }
            }
        }
    }

    private fun bounds(text: String): DpRect = composeRule.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot()

    @Test
    fun `con fuente normal el chip queda al lado del titulo en la misma fila`() {
        show(fontScale = 1.0f)

        val title = bounds(TITLE)
        val chip = bounds(CHIP)

        assertTrue("el chip debería estar a la derecha del título", chip.left >= title.right)
        assertTrue("el chip debería compartir fila con el título", chip.top < title.bottom)
    }

    @Test
    fun `con fuente al 160 por ciento el chip pasa debajo del titulo y el nombre usa todo el ancho`() {
        show(fontScale = 1.6f)

        val title = bounds(TITLE)
        val chip = bounds(CHIP)

        assertTrue("el chip debería quedar debajo del título", chip.top >= title.bottom)
        assertTrue("el título no debería compartir fila con el chip", chip.left < title.right)
    }

    private companion object {
        const val TITLE = "Valeria Ramos Cisneros"
        const val CHIP = "Pendiente de aprobación"
    }
}
