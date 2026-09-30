package mx.crnl.clinica.beta.feature.appointments.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
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
import mx.crnl.clinica.beta.domain.appointment.AppointmentStatePolicy
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AppointmentAdminUpdate
import mx.crnl.clinica.beta.domain.model.AppointmentDetail
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AppointmentRepository
import mx.crnl.clinica.beta.domain.repository.AuthRepository

data class EditAppointmentUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val notFound: Boolean = false,
    /** La cita existe pero esta persona no puede modificarla o ya terminó su ciclo. */
    val notEditable: Boolean = false,
    val detail: AppointmentDetail? = null,
    val logistics: LogisticsFormState = LogisticsFormState(),
    val initial: LogisticsFormState? = null,
    val write: WriteState = WriteState.Idle,
    val isSaved: Boolean = false,
) {
    val isSaving: Boolean get() = write is WriteState.Saving

    val canEdit: Boolean get() = detail != null && !isLoading && !loadFailed && !notFound && !notEditable

    val hasChanges: Boolean
        get() = initial != null && logistics.copy(issues = emptyMap()) != initial
}

/** Corrige modalidad, ubicación o enlace y notas de una cita abierta; el horario se cambia al reprogramar. */
class EditAppointmentViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val appointmentRepository: AppointmentRepository,
) : ViewModel() {
    private val appointmentId = savedStateHandle.toRoute<AppRoute.EditAppointment>().appointmentId
    private val _state = MutableStateFlow(EditAppointmentUiState())
    val state: StateFlow<EditAppointmentUiState> = _state.asStateFlow()

    private var actor: UserAccount? = null

    init {
        load()
    }

    fun onRetryLoad() {
        _state.value = EditAppointmentUiState()
        load()
    }

    fun onModalityChange(modality: AppointmentModality) = update { it.withModality(modality) }

    fun onLocationChange(value: String) = update { it.withLocation(value) }

    fun onMeetingUrlChange(value: String) = update { it.withMeetingUrl(value) }

    fun onNotesChange(value: String) = update { it.withNotes(value) }

    fun onSave() {
        val current = _state.value
        val user = actor
        val detail = current.detail
        if (user == null || detail == null || !current.canEdit || current.isSaving || current.isSaved) return

        val issues = current.logistics.validate()
        if (issues.isNotEmpty()) {
            _state.update { it.copy(logistics = it.logistics.withIssues(issues), write = WriteState.Idle) }
            return
        }
        val update = AppointmentAdminUpdate(
            modality = requireNotNull(current.logistics.modality),
            location = current.logistics.location,
            meetingUrl = current.logistics.meetingUrl,
            administrativeNotes = current.logistics.notes,
        )
        _state.update { it.copy(write = WriteState.Saving) }
        viewModelScope.launch {
            val result = runCatchingCancellable { appointmentRepository.updateAdministrativeData(appointmentId, update, user.userId) }
            _state.update { state ->
                result.fold(
                    onSuccess = { outcome ->
                        when (outcome) {
                            is OperationResult.Success -> state.copy(write = WriteState.Idle, isSaved = true)
                            is OperationResult.Failure -> when (val error = outcome.error) {
                                is OperationError.InvalidAppointment -> state.copy(
                                    write = WriteState.Idle,
                                    logistics = state.logistics.withIssues(error.issues),
                                )
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
            val editable = BetaClinicalAccessPolicy.canManageAppointment(user, summary.area, summary.professionalId) &&
                AppointmentStatePolicy.canEditAdministrativeData(detail.status)
            val form = LogisticsFormState.from(summary.modality, summary.location, detail.meetingUrl, detail.administrativeNotes)
            _state.update { it.copy(isLoading = false, detail = detail, notEditable = !editable, logistics = form, initial = form) }
        }
    }

    private fun update(transform: (LogisticsFormState) -> LogisticsFormState) {
        _state.update { it.copy(logistics = transform(it.logistics), write = if (it.isSaving) it.write else WriteState.Idle) }
    }
}
