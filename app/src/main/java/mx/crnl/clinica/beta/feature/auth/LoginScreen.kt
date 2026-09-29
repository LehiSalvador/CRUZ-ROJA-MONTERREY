package mx.crnl.clinica.beta.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
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
fun LoginRoute(factory: ViewModelProvider.Factory, onSessionStarted: () -> Unit) {
    val viewModel: LoginViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isSessionStarted) {
        if (state.isSessionStarted) onSessionStarted()
    }
    LoginScreen(state = state, onContinue = viewModel::onContinue)
}

@Composable
fun LoginScreen(state: LoginUiState, onContinue: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_brand_mark),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = stringResource(R.string.login_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.login_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.xl))
                PrimaryButton(
                    text = stringResource(R.string.login_continue),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    loading = state.isSubmitting,
                )
                if (state.hasError) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(R.string.login_error),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
