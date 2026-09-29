package mx.crnl.clinica.beta.core.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.crnl.clinica.beta.core.ui.theme.LocalStatusColors

enum class StatusTone { Neutral, Info, Success, Warning, Danger }

@Composable
fun StatusChip(label: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    val (container, content) = when (tone) {
        StatusTone.Neutral -> scheme.surfaceVariant to scheme.onSurfaceVariant
        StatusTone.Info -> scheme.secondaryContainer to scheme.onSecondaryContainer
        StatusTone.Success -> status.successContainer to status.onSuccessContainer
        StatusTone.Warning -> status.warningContainer to status.onWarningContainer
        StatusTone.Danger -> scheme.errorContainer to scheme.onErrorContainer
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}
