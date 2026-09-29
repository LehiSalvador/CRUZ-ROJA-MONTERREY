package mx.crnl.clinica.beta.feature.appointments

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.EmptyState
import mx.crnl.clinica.beta.core.ui.component.SectionHeader
import mx.crnl.clinica.beta.core.ui.component.UiStateContent
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.domain.model.AppointmentSummary

@Composable
fun AppointmentsRoute(factory: ViewModelProvider.Factory) {
    val viewModel: AppointmentsViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppointmentsScreen(state = state, onRetry = viewModel::retry)
}

@Composable
fun AppointmentsScreen(state: UiState<AppointmentsContent>, onRetry: () -> Unit) {
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.appointments_title)) }) { padding ->
        UiStateContent(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.padding(padding),
            empty = {
                EmptyState(
                    icon = Icons.Filled.DateRange,
                    title = stringResource(R.string.appointments_empty_title),
                    message = stringResource(R.string.appointments_empty_message),
                )
            },
        ) { content ->
            LazyColumn(
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                appointmentSection(R.string.appointments_section_upcoming, content.upcoming)
                appointmentSection(R.string.appointments_section_history, content.history)
            }
        }
    }
}

private fun LazyListScope.appointmentSection(
    @StringRes titleRes: Int,
    appointments: List<AppointmentSummary>,
) {
    if (appointments.isEmpty()) return
    item(key = "header-$titleRes") { SectionHeader(title = stringResource(titleRes)) }
    items(appointments, key = { it.appointmentId }) { appointment -> AppointmentCard(appointment) }
}
