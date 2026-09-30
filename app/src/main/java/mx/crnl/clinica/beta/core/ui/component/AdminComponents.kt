package mx.crnl.clinica.beta.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.theme.LocalStatusColors
import mx.crnl.clinica.beta.core.ui.theme.Spacing

/** Confirma una acción con consecuencias; la acción segura (cancelar) queda como texto y la confirmación como botón. */
@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Estado de una superficie a la que la persona no tiene acceso: no se muestra dato alguno y se ofrece volver. */
@Composable
fun NotAuthorizedState(onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Filled.Lock,
        title = stringResource(R.string.not_authorized_title),
        message = stringResource(R.string.not_authorized_message),
        modifier = modifier,
        action = onBack?.let { back -> { SecondaryButton(text = stringResource(R.string.action_go_back), onClick = back) } },
    )
}

/** Opciones excluyentes en una fila de chips que salta de línea con fuentes grandes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipFilterRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEach { option ->
            ClinicalFilterChip(selected = option == selected, onClick = { onSelect(option) }, label = optionLabel(option))
        }
    }
}

/** Aviso en línea dentro de una pantalla (vigencia de un acceso, advertencias). El tono no depende solo del color: lleva icono. */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector = Icons.Filled.Info,
    warning: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    val container = if (warning) status.warningContainer else colors.secondaryContainer
    val content = if (warning) status.onWarningContainer else colors.onSecondaryContainer
    Surface(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(imageVector = if (warning) Icons.Filled.Warning else icon, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (title != null) Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Tarjeta de una solicitud o cuenta en una bandeja: un título, líneas de apoyo y el estado. No lleva datos clínicos.
 */
@Composable
fun RequestCard(
    title: String,
    lines: List<String>,
    statusLabel: String,
    statusTone: StatusTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Con fuentes grandes el chip se lleva media tarjeta y parte el nombre a mitad de palabra: pasa debajo del título.
    val stacked = LocalDensity.current.fontScale > 1.3f
    ClinicalCard(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, onClick = onClick) {
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                StatusChip(label = statusLabel, tone = statusTone, singleLine = false)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
                Text(text = title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                StatusChip(label = statusLabel, tone = statusTone, singleLine = false)
            }
        }
        lines.forEach { line ->
            Text(
                text = line,
                modifier = Modifier.padding(top = Spacing.xs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
