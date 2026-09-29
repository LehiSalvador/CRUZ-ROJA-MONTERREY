package mx.crnl.clinica.beta.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.BuildConfig
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.SecondaryButton
import mx.crnl.clinica.beta.core.ui.component.SectionHeader
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing

@Composable
fun ProfileRoute(factory: ViewModelProvider.Factory, onSignedOut: () -> Unit) {
    val viewModel: ProfileViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isSignedOut) {
        if (state.isSignedOut) onSignedOut()
    }
    ProfileScreen(
        state = state,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        onSignOut = viewModel::onSignOut,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    versionName: String,
    versionCode: Int,
    onSignOut: () -> Unit,
) {
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.profile_title)) }) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                ClinicalCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(title = stringResource(R.string.profile_about))
                    Column(
                        modifier = Modifier.padding(top = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        InfoRow(
                            label = stringResource(R.string.profile_version_label),
                            value = stringResource(R.string.profile_version_value, versionName, versionCode),
                        )
                        InfoRow(
                            label = stringResource(R.string.profile_data_label),
                            value = stringResource(R.string.profile_data_value),
                        )
                    }
                }
                SecondaryButton(
                    text = stringResource(R.string.profile_sign_out),
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                    loading = state.isSigningOut,
                )
                if (state.hasError) {
                    Text(
                        text = stringResource(R.string.profile_sign_out_error),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
