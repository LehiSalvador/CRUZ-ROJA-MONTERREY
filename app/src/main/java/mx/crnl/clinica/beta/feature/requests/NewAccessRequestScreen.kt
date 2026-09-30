package mx.crnl.clinica.beta.feature.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTextField
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.EmptyState
import mx.crnl.clinica.beta.core.ui.component.ErrorState
import mx.crnl.clinica.beta.core.ui.component.FormActionBar
import mx.crnl.clinica.beta.core.ui.component.InfoBanner
import mx.crnl.clinica.beta.core.ui.component.LoadingState
import mx.crnl.clinica.beta.core.ui.component.PrimaryButton
import mx.crnl.clinica.beta.core.ui.component.SecondaryButton
import mx.crnl.clinica.beta.core.ui.component.actionModifier
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.feature.appointments.form.WriteErrorText

@Composable
fun NewAccessRequestRoute(factory: ViewModelProvider.Factory, onClose: () -> Unit, onSent: () -> Unit) {
    val viewModel: NewAccessRequestViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSent()
    }
    NewAccessRequestScreen(
        state = state,
        onBack = onClose,
        onReasonChange = viewModel::onReasonChange,
        onSubmit = viewModel::onSubmit,
        onRetryLoad = viewModel::onRetryLoad,
    )
}

@Composable
fun NewAccessRequestScreen(
    state: NewAccessRequestUiState,
    onBack: () -> Unit,
    onReasonChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetryLoad: () -> Unit,
) {
    val ready = !state.isLoading && !state.loadFailed && state.block == null
    Scaffold(
        topBar = { ClinicalTopBar(title = stringResource(R.string.access_new_title), onNavigateUp = onBack) },
        bottomBar = {
            if (ready) {
                FormActionBar {
                    SecondaryButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onBack,
                        modifier = actionModifier(),
                        enabled = !state.isSaving,
                    )
                    PrimaryButton(
                        text = stringResource(R.string.access_new_submit),
                        onClick = onSubmit,
                        modifier = actionModifier(),
                        enabled = state.canSubmit,
                        loading = state.isSaving,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.isLoading -> LoadingState()
                state.loadFailed -> ErrorState(onRetry = onRetryLoad)
                state.block == AccessRequestBlock.NOT_ALLOWED -> EmptyState(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.access_new_not_allowed_title),
                    message = stringResource(R.string.access_new_not_allowed_message),
                    action = { SecondaryButton(text = stringResource(R.string.action_go_back), onClick = onBack) },
                )
                state.block == AccessRequestBlock.ALREADY_PENDING -> EmptyState(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.access_new_pending_title),
                    message = stringResource(R.string.access_new_pending_message),
                    action = { SecondaryButton(text = stringResource(R.string.action_go_back), onClick = onBack) },
                )
                else -> Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    state.context?.let { context ->
                        ClinicalCard(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                            Text(text = context.patientName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = context.patientNumber + " · " + stringResource(context.ownerArea.labelRes()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    InfoBanner(text = stringResource(R.string.access_new_scope_note))
                    ClinicalTextField(
                        value = state.reason,
                        onValueChange = onReasonChange,
                        label = stringResource(R.string.access_new_reason),
                        helper = stringResource(R.string.access_new_reason_helper),
                        error = if (state.reasonInvalid) stringResource(R.string.error_invalid_reason) else null,
                        singleLine = false,
                        minLines = 3,
                    )
                    WriteErrorText(state.write)
                }
            }
        }
    }
}
