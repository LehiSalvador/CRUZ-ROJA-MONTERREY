package mx.crnl.clinica.beta.feature.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
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
import mx.crnl.clinica.beta.core.ui.component.SectionHeader
import mx.crnl.clinica.beta.core.ui.component.actionModifier
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.model.ProfessionalOption
import mx.crnl.clinica.beta.feature.appointments.form.WriteErrorText

@Composable
fun NewProfessionalChangeRoute(factory: ViewModelProvider.Factory, onClose: () -> Unit, onSent: () -> Unit) {
    val viewModel: NewProfessionalChangeViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSent()
    }
    NewProfessionalChangeScreen(
        state = state,
        onBack = onClose,
        onSelect = viewModel::onSelect,
        onReasonChange = viewModel::onReasonChange,
        onSubmit = viewModel::onSubmit,
        onRetryLoad = viewModel::onRetryLoad,
    )
}

@Composable
fun NewProfessionalChangeScreen(
    state: NewProfessionalChangeUiState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetryLoad: () -> Unit,
) {
    val ready = !state.isLoading && !state.loadFailed && state.block == null
    Scaffold(
        topBar = { ClinicalTopBar(title = stringResource(R.string.change_new_title), onNavigateUp = onBack) },
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
                        text = stringResource(R.string.change_new_submit),
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
                state.block == ChangeBlock.NOT_ALLOWED -> EmptyState(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.change_new_not_allowed_title),
                    message = stringResource(R.string.change_new_not_allowed_message),
                    action = { SecondaryButton(text = stringResource(R.string.action_go_back), onClick = onBack) },
                )
                state.block == ChangeBlock.ALREADY_PENDING -> EmptyState(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.change_new_pending_title),
                    message = stringResource(R.string.change_new_pending_message),
                    action = { SecondaryButton(text = stringResource(R.string.action_go_back), onClick = onBack) },
                )
                else -> FormBody(state, onSelect, onReasonChange)
            }
        }
    }
}

@Composable
private fun FormBody(state: NewProfessionalChangeUiState, onSelect: (String) -> Unit, onReasonChange: (String) -> Unit) {
    val context = state.context ?: return
    Column(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ClinicalCard(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
            Text(text = context.patientName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = context.patientNumber + " · " + stringResource(context.area.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.change_new_current, context.currentAssignment.professionalName) + " · " +
                    stringResource(R.string.detail_assigned_since, DateTimeFormats.date(context.currentAssignment.since)),
                modifier = Modifier.padding(top = Spacing.sm),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (context.upcomingAppointmentCount > 0) {
            InfoBanner(
                title = pluralStringResource(R.plurals.change_upcoming_title, context.upcomingAppointmentCount, context.upcomingAppointmentCount),
                text = stringResource(R.string.change_upcoming_message),
                warning = true,
            )
        }
        SectionHeader(title = stringResource(R.string.change_new_candidates))
        if (state.candidates.isEmpty()) {
            Text(
                text = stringResource(R.string.change_new_no_candidates),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            state.candidates.forEach { professional ->
                CandidateRow(professional, selected = professional.userId == state.selectedId, onSelect = { onSelect(professional.userId) })
            }
        }
        Text(
            text = state.selected?.let { stringResource(R.string.change_new_confirm, it.fullName) }
                ?: stringResource(R.string.change_new_confirm_none),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleSmall,
            color = if (state.selected == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
        ClinicalTextField(
            value = state.reason,
            onValueChange = onReasonChange,
            label = stringResource(R.string.change_new_reason),
            helper = stringResource(R.string.change_new_reason_helper),
            error = if (state.reasonInvalid) stringResource(R.string.error_invalid_reason) else null,
            singleLine = false,
            minLines = 3,
        )
        WriteErrorText(state.write)
    }
}

@Composable
private fun CandidateRow(professional: ProfessionalOption, selected: Boolean, onSelect: () -> Unit) {
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = professional.fullName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = listOfNotNull(
                        stringResource(R.string.role_professional),
                        professional.professionalLicense?.let { stringResource(R.string.assign_license, it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
