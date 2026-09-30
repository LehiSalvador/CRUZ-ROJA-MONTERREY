package mx.crnl.clinica.beta.feature.encounters

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.crnl.clinica.beta.core.navigation.AppRoute
import mx.crnl.clinica.beta.core.util.runCatchingCancellable
import mx.crnl.clinica.beta.domain.appointment.AppointmentField
import mx.crnl.clinica.beta.domain.appointment.AppointmentIssue
import mx.crnl.clinica.beta.domain.appointment.AppointmentScheduleParser
import mx.crnl.clinica.beta.domain.appointment.ScheduleParseResult
import mx.crnl.clinica.beta.domain.clinical.EncounterField
import mx.crnl.clinica.beta.domain.clinical.EncounterIssue
import mx.crnl.clinica.beta.domain.clinical.EncounterRules
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.EncounterDraft
import mx.crnl.clinica.beta.domain.model.EncounterFormContext
import mx.crnl.clinica.beta.domain.model.EncounterType
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.EncounterRepository
import mx.crnl.clinica.beta.feature.appointments.form.ScheduleFormState
import mx.crnl.clinica.beta.feature.appointments.form.WriteState

data class NewEncounterUiState(
    /** Hoy (hora de Monterrey): una atención ya ocurrida no puede tener fecha posterior. */
    val today: LocalDate = LocalDate.ofEpochDay(0),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val context: EncounterFormContext? = null,
    val type: EncounterType? = null,
    val appointmentId: String? = null,
    /** Cuándo ocurrió la atención (día y hora de Monterrey, solo dígitos). */
    val moment: ScheduleFormState = ScheduleFormState(),
    val issues: Map<EncounterField, EncounterIssue> = emptyMap(),
    val write: WriteState = WriteState.Idle,
    val savedPatientId: String? = null,
    val savedArea: ClinicalArea? = null,
) {
    val isSaving: Boolean get() = write is WriteState.Saving

    val isSaved: Boolean get() = savedPatientId != null

    val canFill: Boolean get() = context?.canCreate == true && !isLoading && !loadFailed

    val hasChanges: Boolean get() = type != null
}

/** Registra que hubo atención: tipo, cuándo ocurrió y, si aplica, de qué cita. No captura contenido clínico. */
class NewEncounterViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val encounterRepository: EncounterRepository,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<AppRoute.NewEncounter>()
    private val patientId = route.patientId
    private val area = ClinicalArea.entries.firstOrNull { it.name == route.area }
    private val _state = MutableStateFlow(NewEncounterUiState(today = LocalDate.now(clock)))
    val state: StateFlow<NewEncounterUiState> = _state.asStateFlow()

    private var actor: UserAccount? = null

    init {
        load()
    }

    fun onRetryLoad() {
        _state.value = NewEncounterUiState(today = LocalDate.now(clock))
        load()
    }

    fun onTypeChange(type: EncounterType) {
        _state.update { it.copy(type = type, issues = it.issues - EncounterField.TYPE, write = it.settled()) }
    }

    fun onDateChange(digits: String) = updateWhen { it.withDate(digits) }

    fun onTimeChange(digits: String) = updateWhen { it.withTime(digits) }

    fun onDatePicked(date: LocalDate) = updateWhen { it.withPickedDate(date) }

    fun onTimePicked(time: LocalTime) = updateWhen { it.withPickedTime(time) }

    /** Vincula (o desvincula, con nulo) una cita; al vincular, el momento sugerido pasa a ser el de esa cita. */
    fun onAppointmentChange(appointmentId: String?) {
        _state.update { state ->
            val appointment = state.context?.linkableAppointments?.firstOrNull { it.appointmentId == appointmentId }
            state.copy(
                appointmentId = appointment?.appointmentId,
                moment = if (appointment != null) suggestedMoment(appointment.start) else state.moment,
                issues = state.issues - EncounterField.APPOINTMENT,
                write = state.settled(),
            )
        }
    }

    fun onSave() {
        val current = _state.value
        val user = actor
        val context = current.context
        val professionalId = context?.assignment?.professionalId
        if (user == null || context == null || professionalId == null || !current.canFill || current.isSaving || current.isSaved) return

        val now = clock.instant()
        val parsed = AppointmentScheduleParser.parse(current.moment.dateDigits, current.moment.timeDigits)
        val issues = buildMap {
            if (current.type == null) put(EncounterField.TYPE, EncounterIssue.REQUIRED)
            when (parsed) {
                is ScheduleParseResult.Invalid -> {
                    parsed.issues[AppointmentField.DATE]?.let { put(EncounterField.DATE, it.toEncounterIssue()) }
                    parsed.issues[AppointmentField.TIME]?.let { put(EncounterField.TIME, it.toEncounterIssue()) }
                }
                is ScheduleParseResult.Parsed -> putAll(
                    EncounterRules.issues(current.type ?: EncounterType.OTHER, parsed.start, now).filterKeys { it == EncounterField.DATE },
                )
            }
        }
        if (issues.isNotEmpty() || parsed !is ScheduleParseResult.Parsed || current.type == null) {
            _state.update { it.copy(issues = issues, write = WriteState.Idle) }
            return
        }
        val draft = EncounterDraft(patientId, context.area, professionalId, current.appointmentId, current.type, parsed.start)
        _state.update { it.copy(write = WriteState.Saving, issues = emptyMap()) }
        viewModelScope.launch {
            val result = runCatchingCancellable { encounterRepository.createEncounter(draft, user.userId) }
            _state.update { state ->
                result.fold(
                    onSuccess = { outcome ->
                        when (outcome) {
                            is OperationResult.Success -> state.copy(write = WriteState.Idle, savedPatientId = patientId, savedArea = context.area)
                            is OperationResult.Failure -> when (val error = outcome.error) {
                                is OperationError.InvalidEncounter -> state.copy(write = WriteState.Idle, issues = error.issues)
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
            val loaded = if (user != null && area != null) {
                runCatchingCancellable { encounterRepository.getFormContext(patientId, area, user) }
            } else {
                null
            }
            if (user == null || loaded == null || loaded.isFailure) {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }
            actor = user
            val context = loaded.getOrNull()
            val linked = context?.linkableAppointments?.firstOrNull { it.appointmentId == route.appointmentId }
            _state.update {
                it.copy(
                    isLoading = false,
                    context = context,
                    appointmentId = linked?.appointmentId,
                    moment = suggestedMoment(linked?.start),
                )
            }
        }
    }

    /** Un encuentro ya ocurrió: si la cita todavía no empieza, el momento sugerido es ahora. */
    private fun suggestedMoment(appointmentStart: Instant?): ScheduleFormState {
        val now = clock.instant().truncatedTo(ChronoUnit.MINUTES)
        val moment = if (appointmentStart != null && appointmentStart < now) appointmentStart else now
        return ScheduleFormState.from(moment, durationMinutes = 0)
    }

    private fun updateWhen(transform: (ScheduleFormState) -> ScheduleFormState) {
        _state.update { it.copy(moment = transform(it.moment), issues = it.issues - EncounterField.DATE - EncounterField.TIME, write = it.settled()) }
    }

    private fun NewEncounterUiState.settled(): WriteState = if (isSaving) write else WriteState.Idle

    private fun AppointmentIssue.toEncounterIssue(): EncounterIssue =
        if (this == AppointmentIssue.REQUIRED) EncounterIssue.REQUIRED else EncounterIssue.INVALID
}
