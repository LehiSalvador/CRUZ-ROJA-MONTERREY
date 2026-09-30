package mx.crnl.clinica.beta.feature.appointments.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.crnl.clinica.beta.core.navigation.AppRoute
import mx.crnl.clinica.beta.core.util.runCatchingCancellable
import mx.crnl.clinica.beta.domain.access.BetaClinicalAccessPolicy
import mx.crnl.clinica.beta.domain.appointment.AppointmentAction
import mx.crnl.clinica.beta.domain.appointment.AppointmentStatePolicy
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AppointmentDetail
import mx.crnl.clinica.beta.domain.model.AppointmentReschedule
import mx.crnl.clinica.beta.domain.model.ScheduleConflict
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AppointmentRepository
import mx.crnl.clinica.beta.domain.repository.AuthRepository

data class RescheduleUiState(
    /** Hoy (hora de Monterrey): el selector de fecha no deja elegir días anteriores. */
    val today: LocalDate = LocalDate.ofEpochDay(0),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val notFound: Boolean = false,
    /** La persona no puede reprogramarla o su estado actual no lo permite. */
    val notAllowed: Boolean = false,
    val detail: AppointmentDetail? = null,
    val schedule: ScheduleFormState = ScheduleFormState(),
    val initial: ScheduleFormState? = null,
    val conflicts: List<ScheduleConflict> = emptyList(),
    val write: WriteState = WriteState.Idle,
    val isSaved: Boolean = false,
) {
    val isSaving: Boolean get() = write is WriteState.Saving

    val canEdit: Boolean get() = detail != null && !isLoading && !loadFailed && !notFound && !notAllowed

    val hasChanges: Boolean
        get() = initial != null && schedule.copy(issues = emptyMap()) != initial
}

/** Mueve una cita a otra fecha u hora conservando la misma cita; queda en estado reprogramada. */
class RescheduleAppointmentViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val appointmentRepository: AppointmentRepository,
    private val clock: Clock,
) : ViewModel() {
    private val appointmentId = savedStateHandle.toRoute<AppRoute.RescheduleAppointment>().appointmentId
    private val _state = MutableStateFlow(RescheduleUiState(today = LocalDate.now(clock)))
    val state: StateFlow<RescheduleUiState> = _state.asStateFlow()

    private var actor: UserAccount? = null

    init {
        load()
    }

    fun onRetryLoad() {
        _state.value = RescheduleUiState(today = LocalDate.now(clock))
        load()
    }

    fun onDateChange(digits: String) = update { it.withDate(digits) }

    fun onTimeChange(digits: String) = update { it.withTime(digits) }

    fun onDurationChange(minutes: Int) = update { it.withDuration(minutes) }

    fun onDatePicked(date: LocalDate) = update { it.withPickedDate(date) }

    fun onTimePicked(time: LocalTime) = update { it.withPickedTime(time) }

    fun onSave() {
        val current = _state.value
        val user = actor
        val detail = current.detail
        if (user == null || detail == null || !current.canEdit || current.isSaving || current.isSaved) return

        val schedule = current.schedule.validate(clock.instant())
        if (schedule !is ScheduleInput.Valid) {
            _state.update { it.copy(schedule = it.schedule.withIssues((schedule as ScheduleInput.Invalid).issues), write = WriteState.Idle) }
            return
        }
        _state.update { it.copy(write = WriteState.Saving, conflicts = emptyList()) }
        viewModelScope.launch {
            val result = runCatchingCancellable {
                appointmentRepository.reschedule(
                    appointmentId,
                    AppointmentReschedule(schedule.start, schedule.durationMinutes),
                    expectedStatus = detail.status,
                    actorUserId = user.userId,
                )
            }
            _state.update { state ->
                result.fold(
                    onSuccess = { outcome ->
                        when (outcome) {
                            is OperationResult.Success -> state.copy(write = WriteState.Idle, isSaved = true)
                            is OperationResult.Failure -> when (val error = outcome.error) {
                                is OperationError.InvalidAppointment -> state.copy(write = WriteState.Idle, schedule = state.schedule.withIssues(error.issues))
                                is OperationError.ScheduleConflicts -> state.copy(write = WriteState.Idle, conflicts = error.conflicts)
                                else -> state.copy(write = WriteState.Failed(error))
                            }
                        }
                    },
                    onFailure = { state.copy(write = WriteState.Unavailable(it)) },
                )
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val user = runCatchingCancellable { authRepository.currentUser.filterNotNull().first() }.getOrNull()
            val loaded = user?.let { runCatchingCancellable { appointmentRepository.getAppointment(appointmentId, it) } }
            if (user == null || loaded == null || loaded.isFailure) {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }
            actor = user
            val detail = loaded.getOrNull()
            if (detail == null) {
                _state.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            val summary = detail.summary
            val allowed = BetaClinicalAccessPolicy.canManageAppointment(user, summary.area, summary.professionalId) &&
                AppointmentStatePolicy.canApply(AppointmentAction.RESCHEDULE, detail.status)
            val minutes = Duration.between(summary.start, summary.end).toMinutes().toInt()
            val form = ScheduleFormState.from(summary.start, minutes)
            _state.update { it.copy(isLoading = false, detail = detail, notAllowed = !allowed, schedule = form, initial = form) }
        }
    }

    private fun update(transform: (ScheduleFormState) -> ScheduleFormState) {
        _state.update {
            it.copy(schedule = transform(it.schedule), conflicts = emptyList(), write = if (it.isSaving) it.write else WriteState.Idle)
        }
    }
}
