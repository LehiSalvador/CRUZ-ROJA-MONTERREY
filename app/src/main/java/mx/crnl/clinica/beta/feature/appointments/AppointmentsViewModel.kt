package mx.crnl.clinica.beta.feature.appointments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.domain.access.BetaClinicalAccessPolicy
import mx.crnl.clinica.beta.domain.appointment.AgendaFilter
import mx.crnl.clinica.beta.domain.appointment.AppointmentAgenda
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.repository.AppointmentRepository
import mx.crnl.clinica.beta.domain.repository.AuthRepository

/**
 * La agenda ya filtrada. [total] cuenta todas las citas que la persona puede ver: distingue «no hay ninguna» de «no
 * hay ninguna en este filtro».
 */
data class AppointmentsContent(
    val filter: AgendaFilter,
    val appointments: List<AppointmentSummary>,
    val total: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentsViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val appointmentRepository: AppointmentRepository,
    private val clock: Clock,
) : ViewModel() {
    private val reloadCount = MutableStateFlow(0)

    /** El filtro se guarda en el estado de la pantalla para sobrevivir a la navegación y a la rotación. */
    val filter: StateFlow<AgendaFilter> = savedStateHandle.getStateFlow(FILTER_KEY, AgendaFilter.UPCOMING.name)
        .map { AgendaFilter.valueOf(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AgendaFilter.UPCOMING)

    val state: StateFlow<UiState<AppointmentsContent>> = reloadCount
        .flatMapLatest {
            authRepository.currentUser
                .filterNotNull()
                .flatMapLatest { user ->
                    combine(appointmentRepository.observeAppointments(user), filter) { appointments, selected ->
                        val shown = AppointmentAgenda.select(appointments, selected, clock.instant(), clock.zone)
                        AppointmentsContent(
                            filter = selected,
                            appointments = shown,
                            total = appointments.size,
                        )
                    }
                }
                .map<AppointmentsContent, UiState<AppointmentsContent>> { content ->
                    if (content.total == 0) UiState.Empty else UiState.Content(content)
                }
                .onStart { emit(UiState.Loading) }
                .catch { emit(UiState.Error(it)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    /** Si su rol puede agendar en alguna área; el botón «Nueva cita» se ofrece también cuando aún no hay citas. */
    val canCreate: StateFlow<Boolean> = authRepository.currentUser
        .map { user -> user != null && BetaClinicalAccessPolicy.manageableAreas(user).isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    fun selectFilter(filter: AgendaFilter) {
        savedStateHandle[FILTER_KEY] = filter.name
    }

    fun retry() {
        reloadCount.update { it + 1 }
    }

    private companion object {
        const val FILTER_KEY = "agenda_filter"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
