package mx.crnl.clinica.beta.feature.assignments

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
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.model.PatientAssignment
import mx.crnl.clinica.beta.domain.model.ProfessionalOption
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.PatientRepository
import mx.crnl.clinica.beta.domain.repository.ProfessionalAssignmentRepository
import mx.crnl.clinica.beta.feature.appointments.form.WriteState

/** Por qué no se puede asignar desde esta pantalla. */
enum class AssignBlock { NOT_AUTHORIZED, ALREADY_ASSIGNED, PATIENT_NOT_FOUND }

data class AssignProfessionalUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val block: AssignBlock? = null,
    val area: ClinicalArea? = null,
    val patient: Patient? = null,
    /** Cuando ya hay un profesional vigente: quién es (no se cambia en esta versión). */
    val current: PatientAssignment? = null,
    val professionals: List<ProfessionalOption> = emptyList(),
    val selectedId: String? = null,
    val reason: String = "",
    val write: WriteState = WriteState.Idle,
    val isAssigned: Boolean = false,
) {
    val isSaving: Boolean get() = write is WriteState.Saving

    val selected: ProfessionalOption? get() = professionals.firstOrNull { it.userId == selectedId }

    val canSubmit: Boolean get() = block == null && !isLoading && !loadFailed && selected != null && !isSaving && !isAssigned
}

/** Asignación inicial de un profesional del área; solo aparece cuando el paciente aún no tiene uno vigente. */
class AssignProfessionalViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val patientRepository: PatientRepository,
    private val assignmentRepository: ProfessionalAssignmentRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<AppRoute.AssignProfessional>()
    private val patientId = route.patientId
    private val area = ClinicalArea.entries.firstOrNull { it.name == route.area }
    private val _state = MutableStateFlow(AssignProfessionalUiState(area = area))
    val state: StateFlow<AssignProfessionalUiState> = _state.asStateFlow()

    private var actor: UserAccount? = null

    init {
        load()
    }

    fun onRetryLoad() {
        _state.value = AssignProfessionalUiState(area = area)
        load()
    }

    fun onSelect(userId: String) {
        _state.update { if (it.isSaving || it.isAssigned) it else it.copy(selectedId = userId, write = WriteState.Idle) }
    }

    fun onReasonChange(reason: String) {
        _state.update { it.copy(reason = reason.take(MAX_REASON_LENGTH), write = if (it.isSaving) it.write else WriteState.Idle) }
    }

    fun onConfirm() {
        val current = _state.value
        val user = actor
        val target = current.area
        val professional = current.selected
        if (user == null || target == null || professional == null || !current.canSubmit) return
        _state.update { it.copy(write = WriteState.Saving) }
        viewModelScope.launch {
            val result = runCatchingCancellable {
                assignmentRepository.createInitialAssignment(patientId, target, professional.userId, current.reason, user.userId)
            }
            _state.update { state ->
                result.fold(
                    onSuccess = { outcome ->
                        when (outcome) {
                            is OperationResult.Success -> state.copy(write = WriteState.Idle, isAssigned = true)
                            is OperationResult.Failure -> when (outcome.error) {
                                OperationError.AssignmentAlreadyActive -> state.copy(write = WriteState.Idle, block = AssignBlock.ALREADY_ASSIGNED)
                                OperationError.NotAuthorized -> state.copy(write = WriteState.Idle, block = AssignBlock.NOT_AUTHORIZED)
                                else -> state.copy(write = WriteState.Failed(outcome.error))
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
            if (user == null || area == null) {
                _state.update { it.copy(isLoading = false, loadFailed = user == null) }
                return@launch
            }
            actor = user
            if (!BetaClinicalAccessPolicy.canCreateAssignment(user, area)) {
                _state.update { it.copy(isLoading = false, block = AssignBlock.NOT_AUTHORIZED) }
                return@launch
            }
            val loaded = runCatchingCancellable {
                Triple(
                    patientRepository.getPatient(patientId),
                    assignmentRepository.getActiveAssignment(patientId, area, user),
                    assignmentRepository.listAssignableProfessionals(area, user),
                )
            }
            _state.update { state ->
                loaded.fold(
                    onSuccess = { (patient, active, professionals) ->
                        when {
                            patient == null -> state.copy(isLoading = false, block = AssignBlock.PATIENT_NOT_FOUND)
                            active != null -> state.copy(isLoading = false, patient = patient, current = active, block = AssignBlock.ALREADY_ASSIGNED)
                            else -> state.copy(isLoading = false, patient = patient, professionals = professionals)
                        }
                    },
                    onFailure = { state.copy(isLoading = false, loadFailed = true) },
                )
            }
        }
    }

    private companion object {
        const val MAX_REASON_LENGTH = 200
    }
}
