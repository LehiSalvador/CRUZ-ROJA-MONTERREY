package mx.crnl.clinica.beta.core.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import mx.crnl.clinica.beta.R

/**
 * Confirma que se descarta lo capturado. La acción segura (seguir editando) es la principal y descartar va como
 * texto en color de error: la jerarquía la marca la forma del botón, porque el rojo de error y el de marca se parecen.
 */
@Composable
fun DiscardChangesDialog(
    title: String,
    message: String,
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onKeepEditing) { Text(stringResource(R.string.discard_keep)) } },
        dismissButton = {
            TextButton(
                onClick = onDiscard,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.discard_confirm)) }
        },
    )
}
