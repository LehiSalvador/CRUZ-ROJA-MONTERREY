package mx.crnl.clinica.beta.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.BuildConfig
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.LabeledValue
import mx.crnl.clinica.beta.core.ui.component.SecondaryButton
import mx.crnl.clinica.beta.core.ui.component.SectionHeader
import mx.crnl.clinica.beta.core.ui.component.StatusChip
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.label.tone
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.domain.model.UserAccount

@Composable
fun ProfileRoute(factory: ViewModelProvider.Factory) {
    val viewModel: ProfileViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                state.user?.let { AccountCard(it) }
                ClinicalCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(title = stringResource(R.string.profile_about))
                    Column(
                        modifier = Modifier.padding(top = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        LabeledValue(
                            label = stringResource(R.string.profile_version_label),
                            value = stringResource(R.string.profile_version_value, versionName, versionCode),
                        )
                        LabeledValue(
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
private fun AccountCard(user: UserAccount) {
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = user.fullName,
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            StatusChip(label = stringResource(user.status.labelRes()), tone = user.status.tone())
        }
        Column(
            modifier = Modifier.padding(top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            LabeledValue(stringResource(R.string.profile_email), user.email)
            LabeledValue(
                stringResource(R.string.profile_area),
                user.area?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.profile_area_none),
            )
            LabeledValue(stringResource(R.string.profile_role), stringResource(user.role.labelRes()))
            user.professionalLicense?.let { LabeledValue(stringResource(R.string.profile_license), it) }
        }
    }
}
