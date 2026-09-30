package mx.crnl.clinica.beta.feature.requests

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
import mx.crnl.clinica.beta.domain.access.ProfessionalChangePolicy
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeContext
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeDraft
import mx.crnl.clinica.beta.domain.model.ProfessionalOption
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.ProfessionalChangeRepository
import mx.crnl.clinica.beta.feature.appointments.form.WriteState

/** Por qué no se puede pedir el cambio desde esta pantalla. */
enum class ChangeBlock { NOT_ALLOWED, ALREADY_PENDING }

data class NewProfessionalChangeUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val block: ChangeBlock? = null,
    val context: ProfessionalChangeContext? = null,
    val selectedId: String? = null,
    val reason: String = "",
    val reasonInvalid: Boolean = false,
    val write: WriteState = WriteState.Idle,
    val isSaved: Boolean = false,
) {
    val isSaving: Boolean get() = write is WriteState.Saving

    val candidates: List<ProfessionalOption> get() = context?.candidates.orEmpty()

    val selected: ProfessionalOption? get() = candidates.firstOrNull { it.userId == selectedId }

    val canSubmit: Boolean get() = block == null && selected != null && !isLoading && !loadFailed && !isSaving && !isSaved
}

/** Pide el cambio formal del profesional vigente; nunca lo cambia directamente: lo resuelve otra persona. */
class NewProfessionalChangeViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val changeRepository: ProfessionalChangeRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<AppRoute.NewProfessionalChange>()
    private val patientId = route.patientId
    private val area = ClinicalArea.entries.firstOrNull { it.name == route.area }
    private val _state = MutableStateFlow(NewProfessionalChangeUiState())
    val state: StateFlow<NewProfessionalChangeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onRetryLoad() {
        _state.value = NewProfessionalChangeUiState()
        load()
    }

    fun onSelect(userId: String) {
        _state.update { if (it.isSaving || it.isSaved) it else it.copy(selectedId = userId, write = WriteState.Idle) }
    }

    fun onReasonChange(reason: String) {
        _state.update {
            it.copy(
                reason = reason.take(ProfessionalChangePolicy.MAX_REASON_LENGTH),
                reasonInvalid = false,
                write = if (it.isSaving) it.write else WriteState.Idle,
            )
        }
    }

    fun onSubmit() {
        val current = _state.value
        val target = area
        val professional = current.selected
        if (!current.canSubmit || target == null || professional == null) return
        if (!ProfessionalChangePolicy.isValidReason(current.reason)) {
            _state.update { it.copy(reasonInvalid = true) }
            return
        }
        _state.update { it.copy(write = WriteState.Saving) }
        viewModelScope.launch {
            val result = runCatchingCancellable {
                val actor = authRepository.currentUser.filterNotNull().first()
                changeRepository.create(ProfessionalChangeDraft(patientId, target, professional.userId, current.reason), actor.userId)
            }
            _state.update { state ->
                result.fold(
                    onSuccess = { outcome ->
                        when (outcome) {
                            is OperationResult.Success -> state.copy(write = WriteState.Idle, isSaved = true)
                            is OperationResult.Failure -> state.copy(write = WriteState.Failed(outcome.error))
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
            if (user == null) {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }
            if (area == null) {
                _state.update { it.copy(isLoading = false, block = ChangeBlock.NOT_ALLOWED) }
                return@launch
            }
            val loaded = runCatchingCancellable { changeRepository.getFormContext(patientId, area, user) }
            _state.update { state ->
                loaded.fold(
                    onSuccess = { context ->
                        when {
                            context == null -> state.copy(isLoading = false, block = ChangeBlock.NOT_ALLOWED)
                            context.pendingRequestId != null -> state.copy(isLoading = false, context = context, block = ChangeBlock.ALREADY_PENDING)
                            else -> state.copy(isLoading = false, context = context)
                        }
                    },
                    onFailure = { state.copy(isLoading = false, loadFailed = true) },
                )
            }
        }
    }
}
