package mx.crnl.clinica.beta.feature.appointments.form

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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.DiscardChangesDialog
import mx.crnl.clinica.beta.core.ui.component.EmptyState
import mx.crnl.clinica.beta.core.ui.component.ErrorState
import mx.crnl.clinica.beta.core.ui.component.FormActionBar
import mx.crnl.clinica.beta.core.ui.component.LoadingState
import mx.crnl.clinica.beta.core.ui.component.PrimaryButton
import mx.crnl.clinica.beta.core.ui.component.SecondaryButton
import mx.crnl.clinica.beta.core.ui.component.actionModifier
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.model.AppointmentDetail

@Composable
fun EditAppointmentRoute(
    factory: ViewModelProvider.Factory,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val viewModel: EditAppointmentViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    val goBack: () -> Unit = { if (state.hasChanges) confirmDiscard = true else onClose() }
    BackHandler(enabled = !state.isSaved, onBack = goBack)

    val callbacks = remember(viewModel) {
        LogisticsCallbacks(viewModel::onModalityChange, viewModel::onLocationChange, viewModel::onMeetingUrlChange, viewModel::onNotesChange)
    }
    EditAppointmentScreen(state = state, callbacks = callbacks, onBack = goBack, onSave = viewModel::onSave, onRetryLoad = viewModel::onRetryLoad)

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
fun EditAppointmentScreen(
    state: EditAppointmentUiState,
    callbacks: LogisticsCallbacks,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRetryLoad: () -> Unit,
) {
    Scaffold(
        topBar = { ClinicalTopBar(title = stringResource(R.string.edit_appointment_title), onNavigateUp = onBack) },
        bottomBar = {
            if (state.canEdit) {
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
                    icon = Icons.Filled.DateRange,
                    title = stringResource(R.string.appointment_not_found_title),
                    message = stringResource(R.string.appointment_not_found_message),
                )
                state.notEditable -> EmptyState(
                    icon = Icons.Filled.DateRange,
                    title = stringResource(R.string.appointment_not_editable_title),
                    message = stringResource(R.string.appointment_not_editable_message),
                )
                else -> Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    state.detail?.let { AppointmentSummaryCard(it) }
                    LogisticsFields(state.logistics, callbacks, enabled = !state.isSaving)
                    WriteErrorText(state.write)
                }
            }
        }
    }
}

/** Qué cita se está modificando: paciente, fecha, área y profesional, sin poder cambiarlos aquí. */
@Composable
fun AppointmentSummaryCard(detail: AppointmentDetail) {
    val summary = detail.summary
    ClinicalCard(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = summary.patientName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${DateTimeFormats.day(summary.start)} · ${DateTimeFormats.timeRange(summary.start, summary.end)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${stringResource(summary.area.labelRes())} · ${summary.professionalName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
