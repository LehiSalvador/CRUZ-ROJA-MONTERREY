package mx.crnl.clinica.beta.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/** En pantallas anchas (horizontal, tabletas) el contenido se limita a este ancho y se centra. */
val ContentMaxWidth = 640.dp

private val CompactHeight = 700.dp

/** Verdadero en pantallas de poca altura (teléfonos pequeños o en horizontal), donde conviene compactar cabeceras y márgenes. */
@Composable
fun isCompactHeight(): Boolean {
    val heightPx = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current
    return heightPx > 0 && with(density) { heightPx.toDp() } < CompactHeight
}

private val CompactLandscapeHeight = 480.dp

/** Teléfono en horizontal: alto insuficiente para una barra inferior, se usa un riel lateral. */
@Composable
fun isCompactLandscape(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return size.width > size.height && with(density) { size.height.toDp() } < CompactLandscapeHeight
}
