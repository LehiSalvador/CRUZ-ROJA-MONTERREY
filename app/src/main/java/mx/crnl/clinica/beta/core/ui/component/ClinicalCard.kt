package mx.crnl.clinica.beta.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.crnl.clinica.beta.core.ui.theme.Spacing

/** Tarjeta de contenido; con [onClick] es una superficie táctil que abre otro destino. */
@Composable
fun ClinicalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick == null) {
        OutlinedCard(modifier = modifier, colors = colors, border = border) {
            Column(modifier = Modifier.padding(Spacing.md), content = content)
        }
    } else {
        OutlinedCard(onClick = onClick, modifier = modifier, colors = colors, border = border) {
            Column(modifier = Modifier.padding(Spacing.md), content = content)
        }
    }
}
