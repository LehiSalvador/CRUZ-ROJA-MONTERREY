package mx.crnl.clinica.beta.feature.assignments

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
fun AssignProfessionalRoute(
    factory: ViewModelProvider.Factory,
    onClose: () -> Unit,
    onAssigned: (patientId: String) -> Unit,
) {
    val viewModel: AssignProfessionalViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isAssigned) {
        if (state.isAssigned) state.patient?.let { onAssigned(it.patientId) }
    }

    AssignProfessionalScreen(
        state = state,
        onBack = onClose,
        onSelect = viewModel::onSelect,
        onReasonChange = viewModel::onReasonChange,
        onConfirm = viewModel::onConfirm,
        onRetryLoad = viewModel::onRetryLoad,
    )
}

@Composable
fun AssignProfessionalScreen(
    state: AssignProfessionalUiState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onRetryLoad: () -> Unit,
) {
    val ready = !state.isLoading && !state.loadFailed && state.block == null
    Scaffold(
        topBar = { ClinicalTopBar(title = stringResource(R.string.assign_title), onNavigateUp = onBack) },
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
                        text = stringResource(R.string.assign_submit),
                        onClick = onConfirm,
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
                state.block != null -> BlockedState(state)
                else -> Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    state.patient?.let { patient ->
                        ClinicalCard(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                            Text(text = patient.fullName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = patient.patientNumber + " · " + stringResource(state.area?.labelRes() ?: R.string.assign_area_unknown),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SectionHeader(title = stringResource(R.string.assign_section_professionals))
                    if (state.professionals.isEmpty()) {
                        Text(
                            text = stringResource(R.string.assign_no_professionals),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        state.professionals.forEach { professional ->
                            ProfessionalRow(professional, selected = professional.userId == state.selectedId, onSelect = { onSelect(professional.userId) })
                        }
                    }
                    // La confirmación dice a quién se asigna; el botón es corto para que quepa con fuentes grandes.
                    Text(
                        text = state.selected?.let { stringResource(R.string.assign_confirm, it.fullName) }
                            ?: stringResource(R.string.assign_confirm_none),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (state.selected == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                    ClinicalTextField(
                        value = state.reason,
                        onValueChange = onReasonChange,
                        label = stringResource(R.string.assign_reason),
                        helper = stringResource(R.string.assign_reason_helper),
                    )
                    WriteErrorText(state.write)
                }
            }
        }
    }
}

@Composable
private fun ProfessionalRow(professional: ProfessionalOption, selected: Boolean, onSelect: () -> Unit) {
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

@Composable
private fun BlockedState(state: AssignProfessionalUiState) {
    val current = state.current
    when (state.block) {
        AssignBlock.NOT_AUTHORIZED -> EmptyState(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.assign_not_allowed_title),
            message = stringResource(R.string.assign_not_allowed_message),
        )
        AssignBlock.PATIENT_NOT_FOUND -> EmptyState(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.detail_not_found_title),
            message = stringResource(R.string.detail_not_found_message),
        )
        AssignBlock.ALREADY_ASSIGNED -> EmptyState(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.assign_already_title),
            message = if (current != null) {
                stringResource(R.string.assign_already_message, current.professionalName, DateTimeFormats.date(current.since))
            } else {
                stringResource(R.string.assign_already_message_generic)
            },
        )
        null -> Unit
    }
}
