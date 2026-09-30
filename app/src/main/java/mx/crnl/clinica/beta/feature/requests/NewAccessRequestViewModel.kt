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
import mx.crnl.clinica.beta.domain.access.InterareaAccessPolicy
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AccessRequestContext
import mx.crnl.clinica.beta.domain.model.AccessRequestDraft
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.repository.AccessRequestRepository
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.feature.appointments.form.WriteState

/** Por qué no se puede pedir acceso desde esta pantalla. */
enum class AccessRequestBlock { NOT_ALLOWED, ALREADY_PENDING }

data class NewAccessRequestUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val block: AccessRequestBlock? = null,
    val context: AccessRequestContext? = null,
    val reason: String = "",
    val reasonInvalid: Boolean = false,
    val write: WriteState = WriteState.Idle,
    val isSaved: Boolean = false,
) {
    val isSaving: Boolean get() = write is WriteState.Saving

    val canSubmit: Boolean get() = block == null && context != null && !isLoading && !loadFailed && !isSaving && !isSaved
}

/** Solicitud de lectura temporal de un área que la persona no ve por su rol. Solo pide: la resuelve otra persona. */
class NewAccessRequestViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val accessRepository: AccessRequestRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<AppRoute.NewAccessRequest>()
    private val patientId = route.patientId
    private val area = ClinicalArea.entries.firstOrNull { it.name == route.area }
    private val _state = MutableStateFlow(NewAccessRequestUiState())
    val state: StateFlow<NewAccessRequestUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onRetryLoad() {
        _state.value = NewAccessRequestUiState()
        load()
    }

    fun onReasonChange(reason: String) {
        _state.update {
            it.copy(
                reason = reason.take(InterareaAccessPolicy.MAX_REASON_LENGTH),
                reasonInvalid = false,
                write = if (it.isSaving) it.write else WriteState.Idle,
            )
        }
    }

    fun onSubmit() {
        val current = _state.value
        if (!current.canSubmit) return
        val target = area ?: return
        if (!InterareaAccessPolicy.isValidReason(current.reason)) {
            _state.update { it.copy(reasonInvalid = true) }
            return
        }
        _state.update { it.copy(write = WriteState.Saving) }
        viewModelScope.launch {
            val result = runCatchingCancellable {
                val actor = authRepository.currentUser.filterNotNull().first()
                accessRepository.create(AccessRequestDraft(patientId, target, current.reason), actor.userId)
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
                _state.update { it.copy(isLoading = false, block = AccessRequestBlock.NOT_ALLOWED) }
                return@launch
            }
            val loaded = runCatchingCancellable { accessRepository.getRequestContext(patientId, area, user) }
            _state.update { state ->
                loaded.fold(
                    onSuccess = { context ->
                        when {
                            context == null -> state.copy(isLoading = false, block = AccessRequestBlock.NOT_ALLOWED)
                            context.alreadyPending -> state.copy(isLoading = false, context = context, block = AccessRequestBlock.ALREADY_PENDING)
                            else -> state.copy(isLoading = false, context = context)
                        }
                    },
                    onFailure = { state.copy(isLoading = false, loadFailed = true) },
                )
            }
        }
    }
}
