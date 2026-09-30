package mx.crnl.clinica.beta.feature.requests

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import java.time.Clock
import java.time.Instant
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
import mx.crnl.clinica.beta.domain.access.InterareaAccessPolicy
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AccessGrantStatus
import mx.crnl.clinica.beta.domain.model.AccessRequest
import mx.crnl.clinica.beta.domain.model.RequestStatus
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AccessRequestRepository
import mx.crnl.clinica.beta.domain.repository.AuthRepository

/** La solicitud tal como la ve [viewer], con lo que puede hacer con ella hoy. */
data class AccessRequestContent(
    val viewer: UserAccount,
    val request: AccessRequest,
    val now: Instant,
    val canReview: Boolean,
    val canRevoke: Boolean,
) {
    val grantStatus: AccessGrantStatus? get() = request.grant?.statusAt(now)

    /** Quien la pidió no puede resolverla: la revisa otra persona. */
    val isOwn: Boolean get() = viewer.userId == request.requesterId
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccessRequestDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val accessRepository: AccessRequestRepository,
    private val clock: Clock,
) : ViewModel() {
    private val requestId = savedStateHandle.toRoute<AppRoute.AccessRequestDetail>().requestId
    private val reloadCount = MutableStateFlow(0)
    private val _review = MutableStateFlow<ReviewState>(ReviewState.Idle)
    val review: StateFlow<ReviewState> = _review.asStateFlow()

    private val _durationDays = MutableStateFlow(InterareaAccessPolicy.DEFAULT_DURATION_DAYS)

    /** Vigencia elegida al aprobar; la pantalla propone [InterareaAccessPolicy.DEFAULT_DURATION_DAYS] y se puede cambiar. */
    val durationDays: StateFlow<Int> = _durationDays.asStateFlow()

    val state: StateFlow<UiState<AccessRequestContent>> = reloadCount
        .flatMapLatest {
            authRepository.currentUser
                .filterNotNull()
                .flatMapLatest { viewer ->
                    accessRepository.observeRequest(requestId, viewer).map<_, UiState<AccessRequestContent>> { request ->
                        if (request == null) {
                            UiState.Empty
                        } else {
                            val now = clock.instant()
                            UiState.Content(
                                AccessRequestContent(
                                    viewer = viewer,
                                    request = request,
                                    now = now,
                                    canReview = request.status == RequestStatus.PENDING &&
                                        InterareaAccessPolicy.canReview(viewer, request.ownerArea, request.requesterId),
                                    canRevoke = request.grant?.statusAt(now) == AccessGrantStatus.ACTIVE &&
                                        InterareaAccessPolicy.canRevoke(viewer, request.ownerArea),
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

    fun onDurationSelected(days: Int) {
        if (InterareaAccessPolicy.isValidDuration(days)) _durationDays.value = days
    }

    fun onApprove() {
        val content = current() ?: return
        if (!content.canReview) return
        run(ReviewOutcome.ACCESS_APPROVED) { actor -> accessRepository.approve(requestId, _durationDays.value, actor.userId) }
    }

    fun onReject() {
        val content = current() ?: return
        if (!content.canReview) return
        run(ReviewOutcome.ACCESS_REJECTED) { actor -> accessRepository.reject(requestId, actor.userId) }
    }

    fun onRevoke() {
        val content = current() ?: return
        val grantId = content.request.grant?.grantId ?: return
        if (!content.canRevoke) return
        run(ReviewOutcome.GRANT_REVOKED) { actor -> accessRepository.revokeGrant(grantId, actor.userId) }
    }

    private fun current(): AccessRequestContent? =
        (state.value as? UiState.Content)?.data?.takeIf { _review.value !is ReviewState.Working }

    private fun run(outcome: ReviewOutcome, block: suspend (UserAccount) -> OperationResult<AccessRequest>) {
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
