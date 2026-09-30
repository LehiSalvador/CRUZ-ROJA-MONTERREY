package mx.crnl.clinica.beta.feature.encounters

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.EmptyState
import mx.crnl.clinica.beta.core.ui.component.LabeledValue
import mx.crnl.clinica.beta.core.ui.component.UiStateContent
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.model.EncounterDetail

@Composable
fun EncounterDetailRoute(
    factory: ViewModelProvider.Factory,
    onNavigateUp: () -> Unit,
    onOpenAppointment: (String) -> Unit,
) {
    val viewModel: EncounterDetailViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    EncounterDetailScreen(state = state, onNavigateUp = onNavigateUp, onOpenAppointment = onOpenAppointment, onRetry = viewModel::retry)
}

@Composable
fun EncounterDetailScreen(
    state: UiState<EncounterDetail>,
    onNavigateUp: () -> Unit,
    onOpenAppointment: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.encounter_detail_title), onNavigateUp = onNavigateUp) }) { padding ->
        UiStateContent(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.padding(padding),
            empty = {
                EmptyState(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.encounter_not_found_title),
                    message = stringResource(R.string.encounter_not_found_message),
                )
            },
        ) { encounter ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            val summary = encounter.summary
                            LabeledValue(stringResource(R.string.encounter_field_type), stringResource(summary.type.labelRes()))
                            LabeledValue(
                                stringResource(R.string.encounter_detail_event),
                                DateTimeFormats.day(summary.eventAt) + " · " + DateTimeFormats.time(summary.eventAt),
                            )
                            LabeledValue(stringResource(R.string.appointment_area), stringResource(summary.area.labelRes()))
                            LabeledValue(stringResource(R.string.appointment_professional), summary.professionalName)
                            LabeledValue(stringResource(R.string.encounter_detail_status), stringResource(summary.status.labelRes()))
                            LabeledValue(stringResource(R.string.encounter_detail_patient), encounter.patientName + " · " + encounter.patientNumber)
                            encounter.appointmentStart?.let { start ->
                                LabeledValue(
                                    stringResource(R.string.encounter_detail_appointment),
                                    DateTimeFormats.day(start) + " · " + DateTimeFormats.time(start),
                                )
                            }
                            LabeledValue(
                                stringResource(R.string.encounter_detail_recorded),
                                DateTimeFormats.day(encounter.recordedAt) + " · " + DateTimeFormats.time(encounter.recordedAt) + " · " + encounter.createdByName,
                            )
                        }
                    }
                    encounter.summary.appointmentId?.let { appointmentId ->
                        TextButton(onClick = { onOpenAppointment(appointmentId) }) {
                            Text(stringResource(R.string.encounter_open_appointment))
                        }
                    }
                }
            }
        }
    }
}
