package mx.crnl.clinica.beta.feature.requests

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.util.runCatchingCancellable
import mx.crnl.clinica.beta.domain.access.AccountAction
import mx.crnl.clinica.beta.domain.access.BetaUserAdministrationPolicy
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AccountAdministrationRepository
import mx.crnl.clinica.beta.domain.repository.AuthRepository

/** Una cuenta tal como la ve quien revisa, con las acciones que la política le permite hoy. */
data class AccountDetailContent(val user: UserAccount, val requestedAt: Instant, val actions: Set<AccountAction>)

/**
 * Detalle de una cuenta para aprobarla, rechazarla, suspenderla o reactivarla. Sirve a la bandeja de Solicitudes y al
 * directorio de usuarios: solo necesita el argumento `userId`, que ambas rutas comparten.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val accountRepository: AccountAdministrationRepository,
) : ViewModel() {
    private val userId: String = savedStateHandle.get<String>(USER_ID_KEY).orEmpty()
    private val reloadCount = MutableStateFlow(0)
    private val _review = MutableStateFlow<ReviewState>(ReviewState.Idle)
    val review: StateFlow<ReviewState> = _review.asStateFlow()

    /** Vacío significa que la cuenta no existe o que la persona no puede consultarla: nada se muestra. */
    val state: StateFlow<UiState<AccountDetailContent>> = reloadCount
        .flatMapLatest {
            authRepository.currentUser
                .filterNotNull()
                .flatMapLatest { viewer ->
                    accountRepository.observeAccount(userId, viewer).map<_, UiState<AccountDetailContent>> { account ->
                        if (account == null) {
                            UiState.Empty
                        } else {
                            UiState.Content(
                                AccountDetailContent(
                                    user = account.user,
                                    requestedAt = account.requestedAt,
                                    actions = BetaUserAdministrationPolicy.availableActions(viewer, account.user),
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

    fun onApply(action: AccountAction) {
        val content = (state.value as? UiState.Content)?.data ?: return
        if (_review.value is ReviewState.Working || action !in content.actions) return
        _review.value = ReviewState.Working
        viewModelScope.launch {
            val result = runCatchingCancellable {
                val actor = authRepository.currentUser.filterNotNull().first()
                accountRepository.apply(userId, action, content.user.status, actor.userId)
            }
            _review.value = result.fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is OperationResult.Success -> ReviewState.Done(action.outcome())
                        is OperationResult.Failure -> ReviewState.Failed(outcome.error)
                    }
                },
                onFailure = { ReviewState.Unavailable },
            )
        }
    }

    private fun AccountAction.outcome(): ReviewOutcome = when (this) {
        AccountAction.APPROVE -> ReviewOutcome.ACCOUNT_APPROVED
        AccountAction.REJECT -> ReviewOutcome.ACCOUNT_REJECTED
        AccountAction.SUSPEND -> ReviewOutcome.ACCOUNT_SUSPENDED
        AccountAction.REACTIVATE -> ReviewOutcome.ACCOUNT_REACTIVATED
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val USER_ID_KEY = "userId"
    }
}
