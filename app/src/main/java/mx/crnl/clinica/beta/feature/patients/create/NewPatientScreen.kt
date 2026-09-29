package mx.crnl.clinica.beta.feature.patients.create

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.DiscardChangesDialog
import mx.crnl.clinica.beta.core.ui.component.FormActionBar
import mx.crnl.clinica.beta.core.ui.component.LabeledValue
import mx.crnl.clinica.beta.core.ui.component.PrimaryButton
import mx.crnl.clinica.beta.core.ui.component.SecondaryButton
import mx.crnl.clinica.beta.core.ui.component.actionModifier
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.patient.PatientFormValidator
import mx.crnl.clinica.beta.feature.patients.form.DuplicateCandidateCard
import mx.crnl.clinica.beta.feature.patients.form.PatientContactFields
import mx.crnl.clinica.beta.feature.patients.form.PatientFormCallbacks
import mx.crnl.clinica.beta.feature.patients.form.PatientFormState
import mx.crnl.clinica.beta.feature.patients.form.PatientIdentityFields
import mx.crnl.clinica.beta.feature.patients.form.PatientPopulationFields

@Composable
fun NewPatientRoute(
    factory: ViewModelProvider.Factory,
    onClose: () -> Unit,
    onOpenPatient: (String) -> Unit,
    onCreated: (CreatedPatient) -> Unit,
) {
    val viewModel: NewPatientViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.created) {
        state.created?.let(onCreated)
    }

    val goBack: () -> Unit = {
        when {
            state.step != NewPatientStep.IDENTITY -> viewModel.onBack()
            state.hasProgress -> confirmDiscard = true
            else -> onClose()
        }
    }
    BackHandler(enabled = state.created == null, onBack = goBack)

    val callbacks = remember(viewModel) {
        PatientFormCallbacks(viewModel::onTextChange, viewModel::onSexChange, viewModel::onPopulationTypeChange)
    }
    NewPatientScreen(
        state = state,
        callbacks = callbacks,
        onBack = goBack,
        onNext = viewModel::onNext,
        onEditData = viewModel::onEditData,
        onCreateAnyway = viewModel::onCreateAnyway,
        onRetryDuplicateCheck = viewModel::onRetryDuplicateCheck,
        onOpenPatient = onOpenPatient,
    )

    if (confirmDiscard) {
        DiscardChangesDialog(
            title = stringResource(R.string.discard_title),
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
fun NewPatientScreen(
    state: NewPatientUiState,
    callbacks: PatientFormCallbacks,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onEditData: () -> Unit,
    onCreateAnyway: () -> Unit,
    onRetryDuplicateCheck: () -> Unit,
    onOpenPatient: (String) -> Unit,
) {
    var confirmCreateAnyway by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { ClinicalTopBar(title = stringResource(R.string.new_patient_title), onNavigateUp = onBack) },
        bottomBar = {
            WizardActions(
                state = state,
                onBack = onBack,
                onNext = onNext,
                onEditData = onEditData,
                onCreateAnyway = { confirmCreateAnyway = true },
                onRetryDuplicateCheck = onRetryDuplicateCheck,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                StepHeader(state.step)
                when (state.step) {
                    NewPatientStep.IDENTITY -> PatientIdentityFields(state.form, callbacks)
                    NewPatientStep.CONTACT -> PatientContactFields(state.form, callbacks)
                    NewPatientStep.POPULATION -> PatientPopulationFields(state.form, callbacks)
                    NewPatientStep.CONSENT -> ConsentStep()
                    NewPatientStep.DUPLICATES -> DuplicatesStep(state.duplicates, onOpenPatient)
                    NewPatientStep.CONFIRMATION -> ConfirmationStep(state.form, state.saveFailed)
                }
            }
        }
    }

    if (confirmCreateAnyway) {
        AlertDialog(
            onDismissRequest = { confirmCreateAnyway = false },
            title = { Text(stringResource(R.string.duplicates_confirm_title)) },
            text = { Text(stringResource(R.string.duplicates_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCreateAnyway = false
                        onCreateAnyway()
                    },
                ) { Text(stringResource(R.string.duplicates_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCreateAnyway = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun StepHeader(step: NewPatientStep) {
    val total = NewPatientStep.entries.size
    val number = step.ordinal + 1
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.wizard_step_progress, number, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(step.titleRes()),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        LinearProgressIndicator(
            progress = { number / total.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun ConsentStep() {
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = stringResource(R.string.consent_message), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DuplicatesStep(check: DuplicateCheck, onOpenPatient: (String) -> Unit) {
    when (check) {
        DuplicateCheck.Checking -> Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.padding(Spacing.xs))
            Text(text = stringResource(R.string.duplicates_checking), style = MaterialTheme.typography.bodyLarge)
        }
        DuplicateCheck.Clear -> StepMessage(
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
            title = stringResource(R.string.duplicates_none_title),
            message = stringResource(R.string.duplicates_none_message),
        )
        DuplicateCheck.Failed -> StepMessage(
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = stringResource(R.string.state_error_title),
            message = stringResource(R.string.duplicates_failed),
        )
        is DuplicateCheck.Found -> {
            StepMessage(
                icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = pluralStringResource(R.plurals.duplicates_found_title, check.candidates.size, check.candidates.size),
                message = stringResource(R.string.duplicates_found_message),
            )
            check.candidates.forEach { candidate ->
                DuplicateCandidateCard(candidate, onOpen = { onOpenPatient(candidate.patient.patientId) })
            }
        }
    }
}

@Composable
private fun StepMessage(icon: @Composable () -> Unit, title: String, message: String) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        icon()
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfirmationStep(form: PatientFormState, saveFailed: Boolean) {
    val input = form.input
    val noData = stringResource(R.string.confirm_no_data)
    val birthDate = PatientFormValidator.parseBirthDate(input.birthDate)?.let(DateTimeFormats::birthDate) ?: noData
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LabeledValue(
                label = stringResource(R.string.confirm_full_name),
                value = listOf(input.firstName, input.paternalSurname, input.maternalSurname)
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .joinToString(" "),
            )
            LabeledValue(stringResource(R.string.field_birth_date), birthDate)
            LabeledValue(stringResource(R.string.field_sex), input.sex?.let { stringResource(it.labelRes()) } ?: noData)
            LabeledValue(stringResource(R.string.field_population), input.populationType?.let { stringResource(it.labelRes()) } ?: noData)
            LabeledValue(stringResource(R.string.detail_municipality), input.municipality.ifBlank { noData })
            LabeledValue(stringResource(R.string.detail_phone), input.phone.ifBlank { noData })
            LabeledValue(stringResource(R.string.detail_email), input.email.ifBlank { noData })
        }
    }
    if (saveFailed) {
        Text(
            text = stringResource(R.string.confirm_save_error),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun WizardActions(
    state: NewPatientUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onEditData: () -> Unit,
    onCreateAnyway: () -> Unit,
    onRetryDuplicateCheck: () -> Unit,
) {
    FormActionBar {
        when (state.step) {
            NewPatientStep.IDENTITY -> PrimaryButton(
                text = stringResource(R.string.action_continue),
                onClick = onNext,
                modifier = actionModifier(),
            )
            NewPatientStep.DUPLICATES -> DuplicateActions(state.duplicates, onBack, onNext, onEditData, onCreateAnyway, onRetryDuplicateCheck)
            NewPatientStep.CONFIRMATION -> {
                SecondaryButton(
                    text = stringResource(R.string.action_back),
                    onClick = onBack,
                    modifier = actionModifier(),
                    enabled = !state.isSaving,
                )
                PrimaryButton(
                    text = stringResource(R.string.confirm_save),
                    onClick = onNext,
                    modifier = actionModifier(),
                    loading = state.isSaving,
                )
            }
            else -> {
                SecondaryButton(text = stringResource(R.string.action_back), onClick = onBack, modifier = actionModifier())
                PrimaryButton(text = stringResource(R.string.action_continue), onClick = onNext, modifier = actionModifier())
            }
        }
    }
}

@Composable
private fun RowScope.DuplicateActions(
    check: DuplicateCheck,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onEditData: () -> Unit,
    onCreateAnyway: () -> Unit,
    onRetryDuplicateCheck: () -> Unit,
) {
    when (check) {
        is DuplicateCheck.Found -> {
            SecondaryButton(text = stringResource(R.string.duplicates_create_anyway), onClick = onCreateAnyway, modifier = actionModifier())
            PrimaryButton(text = stringResource(R.string.duplicates_back_to_edit), onClick = onEditData, modifier = actionModifier())
        }
        DuplicateCheck.Failed -> {
            SecondaryButton(text = stringResource(R.string.action_back), onClick = onBack, modifier = actionModifier())
            PrimaryButton(text = stringResource(R.string.action_retry), onClick = onRetryDuplicateCheck, modifier = actionModifier())
        }
        else -> {
            SecondaryButton(text = stringResource(R.string.action_back), onClick = onBack, modifier = actionModifier())
            PrimaryButton(
                text = stringResource(R.string.action_continue),
                onClick = onNext,
                modifier = actionModifier(),
                enabled = check == DuplicateCheck.Clear,
            )
        }
    }
}

private fun NewPatientStep.titleRes(): Int = when (this) {
    NewPatientStep.IDENTITY -> R.string.step_identity
    NewPatientStep.CONTACT -> R.string.step_contact
    NewPatientStep.POPULATION -> R.string.step_population
    NewPatientStep.CONSENT -> R.string.step_consent
    NewPatientStep.DUPLICATES -> R.string.step_duplicates
    NewPatientStep.CONFIRMATION -> R.string.step_confirmation
}
