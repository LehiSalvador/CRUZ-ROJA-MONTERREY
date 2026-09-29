package mx.crnl.clinica.beta.feature.patients.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
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
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.feature.patients.form.DuplicateCandidateCard
import mx.crnl.clinica.beta.feature.patients.form.PatientContactFields
import mx.crnl.clinica.beta.feature.patients.form.PatientFormCallbacks
import mx.crnl.clinica.beta.feature.patients.form.PatientIdentityFields
import mx.crnl.clinica.beta.feature.patients.form.PatientPopulationFields

@Composable
fun EditPatientRoute(
    factory: ViewModelProvider.Factory,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    onOpenPatient: (String) -> Unit,
) {
    val viewModel: EditPatientViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    val goBack: () -> Unit = { if (state.hasChanges) confirmDiscard = true else onClose() }
    BackHandler(enabled = !state.isSaved, onBack = goBack)

    val callbacks = remember(viewModel) {
        PatientFormCallbacks(viewModel::onTextChange, viewModel::onSexChange, viewModel::onPopulationTypeChange)
    }
    EditPatientScreen(
        state = state,
        callbacks = callbacks,
        onBack = goBack,
        onSave = viewModel::onSave,
        onRetryLoad = viewModel::onRetryLoad,
    )

    state.duplicateWarning?.let { candidates ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissDuplicates,
            title = { Text(stringResource(R.string.edit_duplicates_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(text = stringResource(R.string.edit_duplicates_message))
                    candidates.forEach { candidate ->
                        DuplicateCandidateCard(
                            candidate = candidate,
                            onOpen = {
                                viewModel.onDismissDuplicates()
                                onOpenPatient(candidate.patient.patientId)
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmDuplicates) {
                    Text(stringResource(R.string.edit_duplicates_save_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissDuplicates) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (confirmDiscard) {
        DiscardChangesDialog(
            title = stringResource(R.string.edit_discard_title),
            message = stringResource(R.string.edit_discard_message),
            onKeepEditing = { confirmDiscard = false },
            onDiscard = {
                confirmDiscard = false
                onClose()
            },
        )
    }
}

@Composable
fun EditPatientScreen(
    state: EditPatientUiState,
    callbacks: PatientFormCallbacks,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRetryLoad: () -> Unit,
) {
    val canEdit = !state.isLoading && !state.loadFailed && !state.notFound
    Scaffold(
        topBar = { ClinicalTopBar(title = stringResource(R.string.edit_patient_title), onNavigateUp = onBack) },
        bottomBar = {
            if (canEdit) {
                FormActionBar {
                    SecondaryButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onBack,
                        modifier = actionModifier(),
                        enabled = !state.isSaving,
                    )
                    PrimaryButton(
                        text = stringResource(R.string.edit_save),
                        onClick = onSave,
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
                state.loadFailed -> ErrorState(onRetry = onRetryLoad)
                state.notFound -> EmptyState(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.detail_not_found_title),
                    message = stringResource(R.string.detail_not_found_message),
                )
                else -> Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    SectionHeader(title = stringResource(R.string.step_identity))
                    PatientIdentityFields(state.form, callbacks)
                    SectionHeader(title = stringResource(R.string.step_contact))
                    PatientContactFields(state.form, callbacks)
                    SectionHeader(title = stringResource(R.string.step_population))
                    PatientPopulationFields(state.form, callbacks)
                    if (state.saveFailed) {
                        Text(
                            text = stringResource(R.string.edit_save_error),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
