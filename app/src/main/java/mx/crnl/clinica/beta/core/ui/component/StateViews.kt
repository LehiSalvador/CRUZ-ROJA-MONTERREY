package mx.crnl.clinica.beta.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.theme.Spacing

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    StateLayout(modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StateTexts(title = title, message = message)
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.state_loading)
    StateLayout(modifier) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = label })
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.state_error_title),
    message: String = stringResource(R.string.state_error_message),
) {
    StateLayout(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        StateTexts(title = title, message = message)
        PrimaryButton(text = stringResource(R.string.action_retry), onClick = onRetry)
    }
}

@Composable
private fun StateLayout(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) {
        content()
    }
}

@Composable
private fun StateTexts(title: String, message: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
