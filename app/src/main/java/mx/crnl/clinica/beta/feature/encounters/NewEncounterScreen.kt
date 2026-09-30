package mx.crnl.clinica.beta.feature.encounters

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.LocalTime
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ChoiceChipGroup
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.DateInputField
import mx.crnl.clinica.beta.core.ui.component.DiscardChangesDialog
import mx.crnl.clinica.beta.core.ui.component.EmptyState
import mx.crnl.clinica.beta.core.ui.component.ErrorState
import mx.crnl.clinica.beta.core.ui.component.FormActionBar
import mx.crnl.clinica.beta.core.ui.component.LoadingState
import mx.crnl.clinica.beta.core.ui.component.PrimaryButton
import mx.crnl.clinica.beta.core.ui.component.SecondaryButton
import mx.crnl.clinica.beta.core.ui.component.SectionHeader
import mx.crnl.clinica.beta.core.ui.component.TimeInputField
import mx.crnl.clinica.beta.core.ui.component.actionModifier
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.clinical.EncounterField
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.EncounterType
import mx.crnl.clinica.beta.feature.appointments.encounterIssueText
import mx.crnl.clinica.beta.feature.appointments.form.WriteErrorText

@Composable
fun NewEncounterRoute(
    factory: ViewModelProvider.Factory,
    onClose: () -> Unit,
    onSaved: (patientId: String, area: ClinicalArea) -> Unit,
) {
    val viewModel: NewEncounterViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.savedPatientId) {
        val patientId = state.savedPatientId
        val area = state.savedArea
        if (patientId != null && area != null) onSaved(patientId, area)
    }

    val goBack: () -> Unit = { if (state.hasChanges) confirmDiscard = true else onClose() }
    BackHandler(enabled = !state.isSaved, onBack = goBack)

    NewEncounterScreen(
        state = state,
        onBack = goBack,
        onSave = viewModel::onSave,
        onRetryLoad = viewModel::onRetryLoad,
        onTypeChange = viewModel::onTypeChange,
        onDateChange = viewModel::onDateChange,
        onTimeChange = viewModel::onTimeChange,
        onDatePicked = viewModel::onDatePicked,
        onTimePicked = viewModel::onTimePicked,
        onAppointmentChange = viewModel::onAppointmentChange,
    )

    if (confirmDiscard) {
        DiscardChangesDialog(
            title = stringResource(R.string.discard_encounter_title),
            message = stringResource(R.string.discard_message),
            onKeepEditing = { confirmDiscard = false },
            onDiscard = {
                confirmDiscard = false
                onClose()
            },
        )
    }
}

@Composable
fun NewEncounterScreen(
    state: NewEncounterUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRetryLoad: () -> Unit,
    onTypeChange: (EncounterType) -> Unit,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onDatePicked: (LocalDate) -> Unit,
    onTimePicked: (LocalTime) -> Unit,
    onAppointmentChange: (String?) -> Unit,
) {
    Scaffold(
        topBar = { ClinicalTopBar(title = stringResource(R.string.encounter_register), onNavigateUp = onBack) },
        bottomBar = {
            if (state.canFill) {
                FormActionBar {
                    SecondaryButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onBack,
                        modifier = actionModifier(),
                        enabled = !state.isSaving,
                    )
                    PrimaryButton(
                        text = stringResource(R.string.encounter_save),
                        onClick = onSave,
                        modifier = actionModifier(),
                        loading = state.isSaving,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val context = state.context
            when {
                state.isLoading -> LoadingState()
                state.loadFailed -> ErrorState(onRetry = onRetryLoad)
                context == null -> EmptyState(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.detail_not_found_title),
                    message = stringResource(R.string.detail_not_found_message),
                )
                !context.canCreate -> EmptyState(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.encounter_not_allowed_title),
                    message = stringResource(
                        if (context.assignment == null) R.string.encounter_no_assignment_message else R.string.encounter_not_allowed_message,
                    ),
                )
                else -> Column(
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
                            text = context.patientNumber + " · " + stringResource(context.area.labelRes()) +
                                context.assignment?.let { " · " + it.professionalName }.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.encounter_scope_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChoiceChipGroup(
                        label = stringResource(R.string.encounter_field_type),
                        options = EncounterType.entries,
                        selected = state.type,
                        onSelected = onTypeChange,
                        optionLabel = { stringResource(it.labelRes()) },
                        error = state.issues[EncounterField.TYPE]?.let { encounterIssueText(EncounterField.TYPE, it) },
                    )
                    DateInputField(
                        digits = state.moment.dateDigits,
                        onDigitsChange = onDateChange,
                        onPicked = onDatePicked,
                        label = stringResource(R.string.encounter_field_date),
                        error = state.issues[EncounterField.DATE]?.let { encounterIssueText(EncounterField.DATE, it) },
                        enabled = !state.isSaving,
                        maxDate = state.today,
                    )
                    TimeInputField(
                        digits = state.moment.timeDigits,
                        onDigitsChange = onTimeChange,
                        onPicked = onTimePicked,
                        label = stringResource(R.string.encounter_field_time),
                        error = state.issues[EncounterField.TIME]?.let { encounterIssueText(EncounterField.TIME, it) },
                        enabled = !state.isSaving,
                    )
                    if (context.linkableAppointments.isNotEmpty()) {
                        SectionHeader(title = stringResource(R.string.encounter_field_appointment))
                        ChoiceChipGroup(
                            label = stringResource(R.string.encounter_field_appointment_hint),
                            options = listOf<AppointmentSummary?>(null) + context.linkableAppointments,
                            selected = context.linkableAppointments.firstOrNull { it.appointmentId == state.appointmentId },
                            onSelected = { onAppointmentChange(it?.appointmentId) },
                            optionLabel = { appointment ->
                                if (appointment == null) {
                                    stringResource(R.string.encounter_no_appointment)
                                } else {
                                    DateTimeFormats.day(appointment.start) + " " + DateTimeFormats.timeRange(appointment.start, appointment.end)
                                }
                            },
                        )
                    }
                    WriteErrorText(state.write)
                }
            }
        }
    }
}
