package mx.crnl.clinica.beta.feature.admin

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
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.InfoBanner
import mx.crnl.clinica.beta.core.ui.component.LoadingState
import mx.crnl.clinica.beta.core.ui.component.NotAuthorizedState
import mx.crnl.clinica.beta.core.ui.component.PrimaryButton
import mx.crnl.clinica.beta.core.ui.component.SecondaryButton
import mx.crnl.clinica.beta.core.ui.component.SectionHeader
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing

@Composable
fun BetaToolsRoute(factory: ViewModelProvider.Factory, onNavigateUp: () -> Unit) {
    val viewModel: BetaToolsViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    BetaToolsScreen(
        state = state,
        onStart = viewModel::onStart,
        onContinue = viewModel::onContinue,
        onCancel = viewModel::onCancel,
        onConfirm = viewModel::onConfirm,
        onNavigateUp = onNavigateUp,
    )
}

@Composable
fun BetaToolsScreen(
    state: BetaToolsUiState,
    onStart: () -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.tools_title), onNavigateUp = onNavigateUp) }) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (state.allowed) {
                null -> LoadingState()
                false -> NotAuthorizedState(onBack = onNavigateUp)
                true -> Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    ResetCard(state, onStart, onContinue, onCancel, onConfirm)
                    state.summary?.let { summary ->
                        InfoBanner(
                            text = stringResource(
                                R.string.tools_reset_done,
                                summary.users,
                                summary.patients,
                                summary.appointments,
                                summary.assessments,
                            ),
                        )
                    }
                }
            }
        }
    }
}

// Las confirmaciones van en la propia pantalla (dos pasos) y no en un diálogo: quedan visibles con fuentes grandes y con el teclado.
@Composable
private fun ResetCard(
    state: BetaToolsUiState,
    onStart: () -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.tools_reset_title))
        Column(modifier = Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(text = stringResource(R.string.tools_reset_description), style = MaterialTheme.typography.bodyMedium)
            when (state.step) {
                ResetStep.IDLE, ResetStep.FAILED -> {
                    if (state.step == ResetStep.FAILED) {
                        Text(
                            text = stringResource(R.string.tools_reset_failed),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    SecondaryButton(
                        text = stringResource(R.string.tools_reset_action),
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ResetStep.FIRST_CONFIRMATION -> {
                    Text(
                        text = stringResource(R.string.tools_reset_first),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    PrimaryButton(text = stringResource(R.string.action_continue), onClick = onContinue, modifier = Modifier.fillMaxWidth())
                    SecondaryButton(text = stringResource(R.string.action_cancel), onClick = onCancel, modifier = Modifier.fillMaxWidth())
                }
                ResetStep.FINAL_CONFIRMATION -> {
                    Text(
                        text = stringResource(R.string.tools_reset_final),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    PrimaryButton(text = stringResource(R.string.tools_reset_confirm), onClick = onConfirm, modifier = Modifier.fillMaxWidth())
                    SecondaryButton(text = stringResource(R.string.action_cancel), onClick = onCancel, modifier = Modifier.fillMaxWidth())
                }
                ResetStep.RESETTING -> PrimaryButton(
                    text = stringResource(R.string.tools_reset_working),
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    loading = true,
                )
            }
        }
    }
}
