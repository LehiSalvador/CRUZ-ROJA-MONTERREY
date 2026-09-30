package mx.crnl.clinica.beta.feature.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalFilterChip
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.EmptyState
import mx.crnl.clinica.beta.core.ui.component.UiStateContent
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.domain.appointment.AgendaFilter

@Composable
fun AppointmentsRoute(
    factory: ViewModelProvider.Factory,
    onOpenAppointment: (String) -> Unit,
    onNewAppointment: () -> Unit,
) {
    val viewModel: AppointmentsViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val canCreate by viewModel.canCreate.collectAsStateWithLifecycle()
    AppointmentsScreen(
        state = state,
        filter = filter,
        canCreate = canCreate,
        onFilterChange = viewModel::selectFilter,
        onOpenAppointment = onOpenAppointment,
        onNewAppointment = onNewAppointment,
        onRetry = viewModel::retry,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppointmentsScreen(
    state: UiState<AppointmentsContent>,
    filter: AgendaFilter,
    canCreate: Boolean,
    onFilterChange: (AgendaFilter) -> Unit,
    onOpenAppointment: (String) -> Unit,
    onNewAppointment: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.appointments_title)) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Sin ninguna cita todavía no hay nada que filtrar: el estado vacío ofrece agendar la primera.
            if (state is UiState.Content) {
                // Con fuentes grandes las tres opciones no caben en una línea: se acomodan en varias sin partir ninguna palabra.
                FlowRow(
                    modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    AgendaFilter.entries.forEach { option ->
                        ClinicalFilterChip(
                            selected = option == filter,
                            onClick = { onFilterChange(option) },
                            label = stringResource(option.labelRes()),
                        )
                    }
                }
            }
            // El botón flotante vive dentro de la columna de contenido para alinearse con las tarjetas en pantallas anchas.
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
                    UiStateContent(
                        state = state,
                        onRetry = onRetry,
                        empty = {
                            EmptyState(
                                icon = Icons.Filled.DateRange,
                                title = stringResource(R.string.appointments_empty_title),
                                message = stringResource(R.string.appointments_empty_message),
                            )
                        },
                    ) { content ->
                        if (content.appointments.isEmpty()) {
                            EmptyState(
                                icon = Icons.Filled.DateRange,
                                title = stringResource(content.filter.emptyTitleRes()),
                                message = stringResource(content.filter.emptyMessageRes()),
                            )
                        } else {
                            // Cada filtro conserva su propia posición al volver desde el detalle de una cita.
                            val listState = key(content.filter) { rememberLazyListState() }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = LIST_END_PADDING),
                                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                            ) {
                                items(content.appointments, key = { it.appointmentId }) { appointment ->
                                    AppointmentCard(appointment, onClick = { onOpenAppointment(appointment.appointmentId) })
                                }
                            }
                        }
                    }
                    if (canCreate) {
                        ExtendedFloatingActionButton(
                            onClick = onNewAppointment,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.md),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(Spacing.sm))
                            Text(stringResource(R.string.appointments_new))
                        }
                    }
                }
            }
        }
    }
}

private fun AgendaFilter.labelRes(): Int = when (this) {
    AgendaFilter.UPCOMING -> R.string.appointments_filter_upcoming
    AgendaFilter.TODAY -> R.string.appointments_filter_today
    AgendaFilter.HISTORY -> R.string.appointments_filter_history
}

private fun AgendaFilter.emptyTitleRes(): Int = when (this) {
    AgendaFilter.UPCOMING -> R.string.appointments_empty_upcoming_title
    AgendaFilter.TODAY -> R.string.appointments_empty_today_title
    AgendaFilter.HISTORY -> R.string.appointments_empty_history_title
}

private fun AgendaFilter.emptyMessageRes(): Int = when (this) {
    AgendaFilter.UPCOMING -> R.string.appointments_empty_upcoming_message
    AgendaFilter.TODAY -> R.string.appointments_empty_today_message
    AgendaFilter.HISTORY -> R.string.appointments_empty_history_message
}

private val LIST_END_PADDING = 96.dp
