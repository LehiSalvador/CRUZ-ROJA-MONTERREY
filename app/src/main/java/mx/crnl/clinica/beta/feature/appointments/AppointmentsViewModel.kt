package mx.crnl.clinica.beta.feature.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.state.asUiState
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.repository.AppointmentRepository

/** Citas ya separadas para mostrarse: primero lo que viene, luego el historial (lo más reciente arriba). */
data class AppointmentsContent(
    val upcoming: List<AppointmentSummary>,
    val history: List<AppointmentSummary>,
) {
    val isEmpty: Boolean get() = upcoming.isEmpty() && history.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentsViewModel(
    private val appointmentRepository: AppointmentRepository,
    private val clock: Clock,
) : ViewModel() {
    private val reloadCount = MutableStateFlow(0)

    val state: StateFlow<UiState<AppointmentsContent>> = reloadCount
        .flatMapLatest {
            appointmentRepository.observeAppointments()
                .map { appointments -> appointments.toContent(now = clock.instant()) }
                .asUiState { it.isEmpty }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    fun retry() {
        reloadCount.update { it + 1 }
    }

    // Una cita en curso todavía cuenta como próxima: solo pasa al historial cuando termina.
    private fun List<AppointmentSummary>.toContent(now: Instant): AppointmentsContent {
        val (concluded, pending) = partition { it.end < now }
        return AppointmentsContent(
            upcoming = pending.sortedBy { it.start },
            history = concluded.sortedByDescending { it.start },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
