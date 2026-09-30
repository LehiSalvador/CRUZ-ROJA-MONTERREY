package mx.crnl.clinica.beta.core.navigation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarLayoutTest {
    private val padding = 8.dp

    @Test
    fun `con fuente normal las cinco etiquetas caben y se muestran`() {
        val widths = listOf(38.dp, 58.dp, 32.dp, 66.dp, 38.dp)

        assertTrue(BottomBarLayout.labelsFit(widths, barWidth = 411.dp, itemPadding = padding))
    }

    @Test
    fun `con fuente al 160 por ciento la etiqueta mas larga ya no cabe y la barra pasa a iconos`() {
        val widths = listOf(61.dp, 93.dp, 51.dp, 106.dp, 61.dp)

        assertFalse(BottomBarLayout.labelsFit(widths, barWidth = 411.dp, itemPadding = padding))
        assertFalse(BottomBarLayout.labelsFit(widths, barWidth = 360.dp, itemPadding = padding))
    }

    @Test
    fun `basta una etiqueta que no quepa para no mostrar ninguna cortada`() {
        val widths = listOf(20.dp, 20.dp, 20.dp, 20.dp, 90.dp)

        assertFalse(BottomBarLayout.labelsFit(widths, barWidth = 360.dp, itemPadding = padding))
    }

    @Test
    fun `una barra ancha muestra las etiquetas aunque la fuente sea grande`() {
        val widths = listOf(61.dp, 93.dp, 51.dp, 106.dp, 61.dp)

        assertTrue(BottomBarLayout.labelsFit(widths, barWidth = 800.dp, itemPadding = padding))
    }

    @Test
    fun `sin destinos no hay nada que cortar`() {
        assertTrue(BottomBarLayout.labelsFit(emptyList(), barWidth = 411.dp, itemPadding = padding))
    }
}
