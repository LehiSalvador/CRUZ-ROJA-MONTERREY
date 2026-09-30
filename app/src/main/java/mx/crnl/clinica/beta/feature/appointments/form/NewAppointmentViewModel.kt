package mx.crnl.clinica.beta.feature.appointments.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Job
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
import mx.crnl.clinica.beta.domain.appointment.AppointmentField
import mx.crnl.clinica.beta.domain.appointment.AppointmentIssue
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AppointmentDetail
import mx.crnl.clinica.beta.domain.model.AppointmentDraft
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.PatientAssignment
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.ScheduleConflict
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AppointmentRepository
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.PatientRepository
import mx.crnl.clinica.beta.domain.repository.ProfessionalAssignmentRepository

data class PatientChoice(val patientId: String, val name: String, val number: String)

/** El profesional que atenderá la cita sale de la asignación vigente; nunca se crea una asignación desde aquí. */
sealed interface AssignmentLookup {
    data object NotChosen : AssignmentLookup

    data object Loading : AssignmentLookup

    data class Assigned(val assignment: PatientAssignment) : AssignmentLookup

    /** No hay profesional asignado en el área; [canAssign] indica si esta persona puede asignarlo. */
    data class Missing(val canAssign: Boolean) : AssignmentLookup

    data object Failed : AssignmentLookup
}

data class NewAppointmentUiState(
    /** Hoy (hora de Monterrey): el selector de fecha no deja elegir días anteriores. */
    val today: LocalDate = LocalDate.ofEpochDay(0),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** Áreas en las que esta persona puede agendar; vacío significa que su rol no puede crear citas. */
    val areaOptions: List<ClinicalArea> = emptyList(),
    val patientLocked: Boolean = false,
    val patient: PatientChoice? = null,
    val query: String = "",
    val results: List<PatientChoice> = emptyList(),
    val area: ClinicalArea? = null,
    val lookup: AssignmentLookup = AssignmentLookup.NotChosen,
    val schedule: ScheduleFormState = ScheduleFormState(),
    val logistics: LogisticsFormState = LogisticsFormState(),
    /** Faltantes de paciente y área; los de horario y logística viven en sus propios estados. */
    val selectionIssues: Map<AppointmentField, AppointmentIssue> = emptyMap(),
    val conflicts: List<ScheduleConflict> = emptyList(),
    val write: WriteState = WriteState.Idle,
    val createdAppointmentId: String? = null,
) {
    val isSaving: Boolean get() = write is WriteState.Saving

    /** Hay algo capturado que se perdería al salir. */
    val hasChanges: Boolean
        get() = (patient != null && !patientLocked) || schedule.dateDigits.isNotEmpty() || schedule.timeDigits.isNotEmpty() ||
            logistics.location.isNotEmpty() || logistics.meetingUrl.isNotEmpty() || logistics.notes.isNotEmpty()
}

class NewAppointmentViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val patientRepository: PatientRepository,
    private val assignmentRepository: ProfessionalAssignmentRepository,
    private val appointmentRepository: AppointmentRepository,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<AppRoute.NewAppointment>()
    private val _state = MutableStateFlow(NewAppointmentUiState(today = LocalDate.now(clock)))
    val state: StateFlow<NewAppointmentUiState> = _state.asStateFlow()

    private var actor: UserAccount? = null
    private var lookupJob: Job? = null
    private var searchJob: Job? = null

    init {
        load()
    }

    fun onRetryLoad() {
        _state.value = NewAppointmentUiState(today = LocalDate.now(clock))
        load()
    }

    /** Al volver a la pantalla (p. ej. tras asignar un profesional) se consulta de nuevo quién atiende al paciente. */
    fun onScreenResumed() = refreshLookup()

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(results = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            val found = runCatchingCancellable {
                patientRepository.observePatients(query).first()
                    .filter { it.patient.status == PatientStatus.ACTIVE }
                    .take(MAX_RESULTS)
                    .map { PatientChoice(it.patient.patientId, it.patient.fullName, it.patient.patientNumber) }
            }
            _state.update { it.copy(results = found.getOrDefault(emptyList())) }
        }
    }

    fun onPatientSelected(choice: PatientChoice) {
        if (_state.value.patientLocked) return
        _state.update { it.copy(patient = choice, query = "", results = emptyList(), selectionIssues = it.selectionIssues - AppointmentField.PATIENT) }
        refreshLookup()
    }

    fun onPatientCleared() {
        if (_state.value.patientLocked) return
        _state.update { it.copy(patient = null, lookup = AssignmentLookup.NotChosen, conflicts = emptyList()) }
    }

    fun onAreaSelected(area: ClinicalArea) {
        if (area !in _state.value.areaOptions) return
        _state.update { it.copy(area = area, selectionIssues = it.selectionIssues - AppointmentField.AREA, conflicts = emptyList()) }
        refreshLookup()
    }

    fun onDateChange(digits: String) = updateSchedule { it.withDate(digits) }

    fun onTimeChange(digits: String) = updateSchedule { it.withTime(digits) }

    fun onDurationChange(minutes: Int) = updateSchedule { it.withDuration(minutes) }

    fun onDatePicked(date: LocalDate) = updateSchedule { it.withPickedDate(date) }

    fun onTimePicked(time: LocalTime) = updateSchedule { it.withPickedTime(time) }

    fun onModalityChange(modality: AppointmentModality) = updateLogistics { it.withModality(modality) }

    fun onLocationChange(value: String) = updateLogistics { it.withLocation(value) }

    fun onMeetingUrlChange(value: String) = updateLogistics { it.withMeetingUrl(value) }

    fun onNotesChange(value: String) = updateLogistics { it.withNotes(value) }

    fun onSave() {
        val current = _state.value
        val user = actor
        if (user == null || current.isLoading || current.isSaving || current.createdAppointmentId != null) return

        val selection = buildMap {
            if (current.patient == null) put(AppointmentField.PATIENT, AppointmentIssue.REQUIRED)
            if (current.area == null) put(AppointmentField.AREA, AppointmentIssue.REQUIRED)
        }
        val schedule = current.schedule.validate(clock.instant())
        val logisticsIssues = current.logistics.validate()
        val assignment = (current.lookup as? AssignmentLookup.Assigned)?.assignment
        if (selection.isNotEmpty() || schedule !is ScheduleInput.Valid || logisticsIssues.isNotEmpty() || assignment == null) {
            _state.update {
                it.copy(
                    selectionIssues = selection,
                    schedule = it.schedule.withIssues((schedule as? ScheduleInput.Invalid)?.issues.orEmpty()),
                    logistics = it.logistics.withIssues(logisticsIssues),
                    conflicts = emptyList(),
                    // Sin profesional asignado no hay a quién agendar: el aviso del formulario ya lo explica.
                    write = WriteState.Idle,
                )
            }
            return
        }

        val draft = AppointmentDraft(
            patientId = requireNotNull(current.patient).patientId,
            area = requireNotNull(current.area),
            professionalId = assignment.professionalId,
            start = schedule.start,
            durationMinutes = schedule.durationMinutes,
            modality = requireNotNull(current.logistics.modality),
            location = current.logistics.location,
            meetingUrl = current.logistics.meetingUrl,
            administrativeNotes = current.logistics.notes,
        )
        _state.update { it.copy(write = WriteState.Saving, conflicts = emptyList(), selectionIssues = emptyMap()) }
        viewModelScope.launch {
            val result = runCatchingCancellable { appointmentRepository.createAppointment(draft, user.userId) }
            _state.update { state ->
                result.fold(
                    onSuccess = { applyResult(state, it) },
                    onFailure = { state.copy(write = WriteState.Unavailable(it)) },
                )
            }
        }
    }

    private fun applyResult(state: NewAppointmentUiState, result: OperationResult<AppointmentDetail>): NewAppointmentUiState =
        when (result) {
            is OperationResult.Success -> state.copy(write = WriteState.Idle, createdAppointmentId = result.value.appointmentId)
            is OperationResult.Failure -> when (val error = result.error) {
                is OperationError.InvalidAppointment -> state.copy(
                    write = WriteState.Idle,
                    schedule = state.schedule.withIssues(error.issues.filterKeys { it in SCHEDULE_FIELDS }),
                    logistics = state.logistics.withIssues(error.issues.filterKeys { it in LOGISTICS_FIELDS }),
                )
                is OperationError.ScheduleConflicts -> state.copy(write = WriteState.Idle, conflicts = error.conflicts)
                OperationError.NoActiveAssignment -> state.copy(
                    write = WriteState.Idle,
                    lookup = AssignmentLookup.Missing(canAssign = canAssign(state.area)),
                )
                else -> state.copy(write = WriteState.Failed(error))
            }
        }

    private fun load() {
        viewModelScope.launch {
            val user = runCatchingCancellable { authRepository.currentUser.filterNotNull().first() }.getOrNull()
            if (user == null) {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }
            actor = user
            val areas = BetaClinicalAccessPolicy.manageableAreas(user).sorted()
            val initialArea = route.area?.let { code -> areas.firstOrNull { it.name == code } } ?: areas.singleOrNull()
            val patient = route.patientId?.let { id ->
                runCatchingCancellable { patientRepository.getPatient(id) }.getOrNull()?.takeIf { it.status == PatientStatus.ACTIVE }
            }?.let { PatientChoice(it.patientId, it.fullName, it.patientNumber) }
            _state.update {
                it.copy(
                    isLoading = false,
                    areaOptions = areas,
                    area = initialArea,
                    patient = patient,
                    patientLocked = patient != null,
                )
            }
            refreshLookup()
        }
    }

    private fun refreshLookup() {
        lookupJob?.cancel()
        val user = actor ?: return
        val current = _state.value
        val patient = current.patient
        val area = current.area
        if (patient == null || area == null) {
            _state.update { it.copy(lookup = AssignmentLookup.NotChosen) }
            return
        }
        _state.update { it.copy(lookup = AssignmentLookup.Loading) }
        lookupJob = viewModelScope.launch {
            val found = runCatchingCancellable { assignmentRepository.getActiveAssignment(patient.patientId, area, user) }
            _state.update { state ->
                // Si mientras tanto cambió el paciente o el área, esta respuesta ya no aplica.
                if (state.patient?.patientId != patient.patientId || state.area != area) {
                    state
                } else {
                    state.copy(
                        lookup = found.fold(
                            onSuccess = { assignment ->
                                if (assignment != null) AssignmentLookup.Assigned(assignment) else AssignmentLookup.Missing(canAssign(area))
                            },
                            onFailure = { AssignmentLookup.Failed },
                        ),
                    )
                }
            }
        }
    }

    private fun canAssign(area: ClinicalArea?): Boolean =
        area != null && actor?.let { BetaClinicalAccessPolicy.canCreateAssignment(it, area) } == true

    private fun updateSchedule(transform: (ScheduleFormState) -> ScheduleFormState) {
        _state.update { it.copy(schedule = transform(it.schedule), conflicts = emptyList(), write = it.write.settled()) }
    }

    private fun updateLogistics(transform: (LogisticsFormState) -> LogisticsFormState) {
        _state.update { it.copy(logistics = transform(it.logistics), write = it.write.settled()) }
    }

    // Un error de guardado se limpia en cuanto la persona corrige algo; un guardado en curso no.
    private fun WriteState.settled(): WriteState = if (this is WriteState.Saving) this else WriteState.Idle

    private companion object {
        const val MAX_RESULTS = 6
        val SCHEDULE_FIELDS = setOf(AppointmentField.DATE, AppointmentField.TIME, AppointmentField.DURATION)
        val LOGISTICS_FIELDS = setOf(AppointmentField.MODALITY, AppointmentField.LOCATION, AppointmentField.MEETING_URL, AppointmentField.NOTES)
    }
}
