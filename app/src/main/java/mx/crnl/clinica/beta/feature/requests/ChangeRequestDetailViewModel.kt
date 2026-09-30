package mx.crnl.clinica.beta.feature.requests

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.crnl.clinica.beta.core.navigation.AppRoute
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.util.runCatchingCancellable
import mx.crnl.clinica.beta.domain.access.ProfessionalChangePolicy
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeRequest
import mx.crnl.clinica.beta.domain.model.RequestStatus
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.ProfessionalChangeRepository

/** La solicitud de cambio tal como la ve [viewer]; quien la pidió nunca puede resolverla. */
data class ChangeRequestContent(val viewer: UserAccount, val request: ProfessionalChangeRequest, val canReview: Boolean) {
    val isOwn: Boolean get() = viewer.userId == request.requesterId
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChangeRequestDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val changeRepository: ProfessionalChangeRepository,
) : ViewModel() {
    private val requestId = savedStateHandle.toRoute<AppRoute.ChangeRequestDetail>().requestId
    private val reloadCount = MutableStateFlow(0)
    private val _review = MutableStateFlow<ReviewState>(ReviewState.Idle)
    val review: StateFlow<ReviewState> = _review.asStateFlow()

    private val _rejectReason = MutableStateFlow("")

    /** Razón administrativa opcional al rechazar. */
    val rejectReason: StateFlow<String> = _rejectReason.asStateFlow()

    val state: StateFlow<UiState<ChangeRequestContent>> = reloadCount
        .flatMapLatest {
            authRepository.currentUser
                .filterNotNull()
                .flatMapLatest { viewer ->
                    changeRepository.observeRequest(requestId, viewer).map<_, UiState<ChangeRequestContent>> { request ->
                        if (request == null) {
                            UiState.Empty
                        } else {
                            UiState.Content(
                                ChangeRequestContent(
                                    viewer = viewer,
                                    request = request,
                                    canReview = request.status == RequestStatus.PENDING &&
                                        ProfessionalChangePolicy.canReview(viewer, request.area, request.requesterId),
                                ),
                            )
                        }
                    }
                }
                .onStart { emit(UiState.Loading) }
                .catch { emit(UiState.Error(it)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    fun retry() {
        reloadCount.update { it + 1 }
    }

    fun onRejectReasonChange(reason: String) {
        _rejectReason.value = reason.take(ProfessionalChangePolicy.MAX_REASON_LENGTH)
    }

    fun onApprove() {
        run(ReviewOutcome.CHANGE_APPROVED) { actor -> changeRepository.approve(requestId, actor.userId) }
    }

    fun onReject() {
        val reason = _rejectReason.value
        run(ReviewOutcome.CHANGE_REJECTED) { actor -> changeRepository.reject(requestId, reason, actor.userId) }
    }

    private fun run(outcome: ReviewOutcome, block: suspend (UserAccount) -> OperationResult<ProfessionalChangeRequest>) {
        val content = (state.value as? UiState.Content)?.data ?: return
        if (!content.canReview || _review.value is ReviewState.Working) return
        _review.value = ReviewState.Working
        viewModelScope.launch {
            val result = runCatchingCancellable { block(authRepository.currentUser.filterNotNull().first()) }
            _review.value = result.fold(
                onSuccess = { operation ->
                    when (operation) {
                        is OperationResult.Success -> ReviewState.Done(outcome)
                        is OperationResult.Failure -> ReviewState.Failed(operation.error)
                    }
                },
                onFailure = { ReviewState.Unavailable },
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
