package mx.crnl.clinica.beta.core.navigation

import androidx.compose.ui.unit.Dp

/** Decide cómo se dibuja la barra inferior sin depender de la pantalla: pura y comprobable. */
object BottomBarLayout {
    /**
     * Las etiquetas se muestran solo si la más ancha cabe en la fracción de barra que le toca a cada destino (menos un
     * pequeño margen). Con fuentes grandes ninguna etiqueta debe verse cortada: si alguna no cabe, la barra pasa a
     * iconos con su nombre accesible.
     */
    fun labelsFit(labelWidths: List<Dp>, barWidth: Dp, itemPadding: Dp): Boolean {
        if (labelWidths.isEmpty()) return true
        val available = barWidth / labelWidths.size - itemPadding
        return labelWidths.all { it <= available }
    }
}
