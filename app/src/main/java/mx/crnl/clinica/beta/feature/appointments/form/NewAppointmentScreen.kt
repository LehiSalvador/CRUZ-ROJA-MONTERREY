package mx.crnl.clinica.beta.feature.appointments.form

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ChoiceChipGroup
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTextField
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.DiscardChangesDialog
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
import mx.crnl.clinica.beta.domain.appointment.AppointmentField
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.feature.appointments.appointmentIssueText

@Composable
fun NewAppointmentRoute(
    factory: ViewModelProvider.Factory,
    onClose: () -> Unit,
    onCreated: (String) -> Unit,
    onOpenAppointment: (String) -> Unit,
    onAssignProfessional: (patientId: String, area: ClinicalArea) -> Unit,
) {
    val viewModel: NewAppointmentViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.createdAppointmentId) {
        state.createdAppointmentId?.let(onCreated)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }

    val goBack: () -> Unit = { if (state.hasChanges) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.createdAppointmentId == null, onBack = goBack)

    val schedule = remember(viewModel) {
        ScheduleCallbacks(viewModel::onDateChange, viewModel::onTimeChange, viewModel::onDurationChange, viewModel::onDatePicked, viewModel::onTimePicked)
    }
    val logistics = remember(viewModel) {
        LogisticsCallbacks(viewModel::onModalityChange, viewModel::onLocationChange, viewModel::onMeetingUrlChange, viewModel::onNotesChange)
    }
    NewAppointmentScreen(
        state = state,
        schedule = schedule,
        logistics = logistics,
        actions = NewAppointmentActions(
            onBack = goBack,
            onSave = viewModel::onSave,
            onRetryLoad = viewModel::onRetryLoad,
            onQueryChange = viewModel::onQueryChange,
            onPatientSelected = viewModel::onPatientSelected,
            onPatientCleared = viewModel::onPatientCleared,
            onAreaSelected = viewModel::onAreaSelected,
            onOpenAppointment = onOpenAppointment,
            onAssignProfessional = onAssignProfessional,
        ),
    )

    if (confirmDiscard) {
        DiscardChangesDialog(
            title = stringResource(R.string.discard_appointment_title),
            message = stringResource(R.string.discard_message),
            onKeepEditing = { confirmDiscard = false },
            onDiscard = {
                confirmDiscard = false
                onClose()
            },
        )
    }
}

class NewAppointmentActions(
    val onBack: () -> Unit,
    val onSave: () -> Unit,
    val onRetryLoad: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onPatientSelected: (PatientChoice) -> Unit,
    val onPatientCleared: () -> Unit,
    val onAreaSelected: (ClinicalArea) -> Unit,
    val onOpenAppointment: (String) -> Unit,
    val onAssignProfessional: (patientId: String, area: ClinicalArea) -> Unit,
)

@Composable
fun NewAppointmentScreen(
    state: NewAppointmentUiState,
    schedule: ScheduleCallbacks,
    logistics: LogisticsCallbacks,
    actions: NewAppointmentActions,
) {
    val canFill = !state.isLoading && !state.loadFailed && state.areaOptions.isNotEmpty()
    val scrollState = rememberScrollState()
    // El aviso de conflicto o de error queda al final del formulario: se lleva a la vista al aparecer.
    LaunchedEffect(state.conflicts, state.write) {
        if (state.conflicts.isNotEmpty() || state.write is WriteState.Failed || state.write is WriteState.Unavailable) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    Scaffold(
        topBar = { ClinicalTopBar(title = stringResource(R.string.new_appointment_title), onNavigateUp = actions.onBack) },
        bottomBar = {
            if (canFill) {
                FormActionBar {
                    SecondaryButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = actions.onBack,
                        modifier = actionModifier(),
                        enabled = !state.isSaving,
                    )
                    PrimaryButton(
                        text = stringResource(R.string.appointment_save),
                        onClick = actions.onSave,
                        modifier = actionModifier(),
                        loading = state.isSaving,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.isLoading -> LoadingState()
                state.loadFailed -> ErrorState(onRetry = actions.onRetryLoad)
                state.areaOptions.isEmpty() -> EmptyState(
                    icon = Icons.Filled.DateRange,
                    title = stringResource(R.string.appointment_not_allowed_title),
                    message = stringResource(R.string.appointment_not_allowed_message),
                )
                else -> Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    PatientSection(state, actions)
                    AreaSection(state, actions)
                    ProfessionalSection(state, actions)
                    SectionHeader(title = stringResource(R.string.appointment_section_schedule))
                    ScheduleFields(state.schedule, schedule, enabled = !state.isSaving, minDate = state.today)
                    SectionHeader(title = stringResource(R.string.appointment_section_place))
                    LogisticsFields(state.logistics, logistics, enabled = !state.isSaving)
                    ConflictsCard(state.conflicts, onOpenAppointment = actions.onOpenAppointment)
                    WriteErrorText(state.write)
                }
            }
        }
    }
}

@Composable
private fun PatientSection(state: NewAppointmentUiState, actions: NewAppointmentActions) {
    SectionHeader(title = stringResource(R.string.appointment_section_patient))
    val patient = state.patient
    if (patient != null) {
        ClinicalCard(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(text = patient.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = patient.number,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!state.patientLocked) {
                    TextButton(onClick = actions.onPatientCleared, enabled = !state.isSaving) {
                        Text(stringResource(R.string.appointment_change_patient))
                    }
                }
            }
        }
        return
    }
    ClinicalTextField(
        value = state.query,
        onValueChange = actions.onQueryChange,
        label = stringResource(R.string.appointment_patient_search),
        helper = stringResource(R.string.appointment_patient_search_helper),
        error = state.selectionIssues[AppointmentField.PATIENT]?.let { appointmentIssueText(AppointmentField.PATIENT, it) },
    )
    if (state.query.isNotBlank() && state.results.isEmpty()) {
        Text(
            text = stringResource(R.string.appointment_patient_no_results),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.results.forEach { choice ->
        ClinicalCard(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            onClick = { actions.onPatientSelected(choice) },
        ) {
            Text(text = choice.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = choice.number,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AreaSection(state: NewAppointmentUiState, actions: NewAppointmentActions) {
    ChoiceChipGroup(
        label = stringResource(R.string.appointment_section_area),
        options = state.areaOptions,
        selected = state.area,
        onSelected = actions.onAreaSelected,
        optionLabel = { stringResource(it.labelRes()) },
        error = state.selectionIssues[AppointmentField.AREA]?.let { appointmentIssueText(AppointmentField.AREA, it) },
    )
}

@Composable
private fun ProfessionalSection(state: NewAppointmentUiState, actions: NewAppointmentActions) {
    SectionHeader(title = stringResource(R.string.appointment_section_professional))
    when (val lookup = state.lookup) {
        AssignmentLookup.NotChosen -> Text(
            text = stringResource(R.string.appointment_professional_choose_first),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AssignmentLookup.Loading -> Text(
            text = stringResource(R.string.state_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AssignmentLookup.Failed -> Text(
            text = stringResource(R.string.error_lookup_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        is AssignmentLookup.Assigned -> ClinicalCard(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
            Text(text = lookup.assignment.professionalName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.appointment_professional_assigned_since, DateTimeFormats.date(lookup.assignment.since)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is AssignmentLookup.Missing -> ClinicalCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val area = state.area
                Text(
                    text = stringResource(
                        R.string.appointment_professional_missing,
                        area?.let { stringResource(it.labelRes()) }.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val patient = state.patient
                if (lookup.canAssign && patient != null && area != null) {
                    SecondaryButton(
                        text = stringResource(R.string.assign_action),
                        onClick = { actions.onAssignProfessional(patient.patientId, area) },
                    )
                } else {
                    Text(
                        text = stringResource(R.string.appointment_professional_ask_coordination),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
