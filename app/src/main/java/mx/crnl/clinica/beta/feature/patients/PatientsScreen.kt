package mx.crnl.clinica.beta.feature.patients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.EmptyState
import mx.crnl.clinica.beta.core.ui.component.StatusChip
import mx.crnl.clinica.beta.core.ui.component.UiStateContent
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.label.tone
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing

@Composable
fun PatientsRoute(factory: ViewModelProvider.Factory) {
    val viewModel: PatientsViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    PatientsScreen(state = state, onRetry = viewModel::retry)
}

@Composable
fun PatientsScreen(state: UiState<List<PatientListItem>>, onRetry: () -> Unit) {
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.patients_title)) }) { padding ->
        UiStateContent(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.padding(padding),
            empty = {
                EmptyState(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.patients_empty_title),
                    message = stringResource(R.string.patients_empty_message),
                )
            },
        ) { patients ->
            LazyColumn(
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(patients, key = { it.patientId }) { patient -> PatientRow(patient) }
            }
        }
    }
}

@Composable
private fun PatientRow(item: PatientListItem) {
    ClinicalCard(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = item.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                StatusChip(label = stringResource(item.status.labelRes()), tone = item.status.tone())
            }
            Text(
                text = "${item.patientNumber} · " +
                    pluralStringResource(R.plurals.patient_age_years, item.ageYears, item.ageYears),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = listOfNotNull(item.municipality, stringResource(item.populationType.labelRes()))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
