package mx.crnl.clinica.beta.feature.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.PrimaryButton
import mx.crnl.clinica.beta.core.ui.theme.Spacing

@Composable
fun SplashRoute(factory: ViewModelProvider.Factory, onFinished: (SplashDestination) -> Unit) {
    val viewModel: SplashViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    SplashScreen(state = state, onRetry = viewModel::retry, onFinished = onFinished)
}

@Composable
fun SplashScreen(
    state: SplashUiState,
    onRetry: () -> Unit,
    onFinished: (SplashDestination) -> Unit,
) {
    val ready = state as? SplashUiState.Ready
    LaunchedEffect(ready) {
        if (ready != null) onFinished(ready.destination)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_brand_mark),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xl))
            if (state is SplashUiState.Failed) {
                Text(
                    text = stringResource(R.string.splash_error_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.splash_error_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.md))
                PrimaryButton(text = stringResource(R.string.action_retry), onClick = onRetry)
            } else {
                val loadingLabel = stringResource(R.string.state_loading)
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = loadingLabel },
                    strokeWidth = 3.dp,
                )
            }
        }
    }
}
