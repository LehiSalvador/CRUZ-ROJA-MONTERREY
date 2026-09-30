package mx.crnl.clinica.beta.feature.requests

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.domain.model.AccessRequest
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ManagedAccount
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeRequest
import mx.crnl.clinica.beta.domain.model.RequestStatus
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AccessRequestRepository
import mx.crnl.clinica.beta.domain.repository.AccountAdministrationRepository
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.ProfessionalChangeRepository
import mx.crnl.clinica.beta.domain.request.RequestCategories
import mx.crnl.clinica.beta.domain.request.RequestCategory

/** Todo lo que la persona ve en la pestaña Solicitudes, con lo pendiente primero y el historial después. */
data class RequestsContent(
    val user: UserAccount,
    val categories: List<RequestCategory>,
    val accounts: List<ManagedAccount>,
    val access: List<AccessRequest>,
    val changes: List<ProfessionalChangeRequest>,
    val now: Instant,
) {
    fun pendingCount(category: RequestCategory): Int = when (category) {
        RequestCategory.ACCOUNTS -> accounts.count { it.user.status == AccountStatus.PENDING_APPROVAL }
        RequestCategory.ACCESS -> access.count { it.status == RequestStatus.PENDING }
        RequestCategory.CHANGES -> changes.count { it.status == RequestStatus.PENDING }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val accountRepository: AccountAdministrationRepository,
    private val accessRepository: AccessRequestRepository,
    private val changeRepository: ProfessionalChangeRepository,
    private val clock: Clock,
) : ViewModel() {
    private val reloadCount = MutableStateFlow(0)

    /** Bandeja abierta (nombre de la categoría); vive aquí para conservarse al volver de un detalle. */
    val selectedCategory: StateFlow<String> = savedStateHandle.getStateFlow(TAB_KEY, "")

    val state: StateFlow<UiState<RequestsContent>> = reloadCount
        .flatMapLatest {
            authRepository.currentUser
                .filterNotNull()
                .flatMapLatest { viewer ->
                    combine(
                        accountRepository.observeAccounts(viewer),
                        accessRepository.observeRequests(viewer),
                        changeRepository.observeRequests(viewer),
                    ) { accounts, access, changes ->
                        RequestsContent(
                            user = viewer,
                            categories = RequestCategories.forUser(viewer),
                            accounts = accounts
                                .filter { it.user.status in RequestCategories.ACCOUNT_STATUSES }
                                .pendingFirst { it.user.status == AccountStatus.PENDING_APPROVAL },
                            access = access.pendingFirst { it.status == RequestStatus.PENDING },
                            changes = changes.pendingFirst { it.status == RequestStatus.PENDING },
                            now = clock.instant(),
                        )
                    }
                }
                .map<RequestsContent, UiState<RequestsContent>> { UiState.Content(it) }
                .onStart { emit(UiState.Loading) }
                .catch { emit(UiState.Error(it)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    fun selectCategory(category: RequestCategory) {
        savedStateHandle[TAB_KEY] = category.name
    }

    fun retry() {
        reloadCount.update { it + 1 }
    }

    // Estable: dentro de cada grupo se conserva el orden que trae el repositorio (la más reciente primero).
    private fun <T> List<T>.pendingFirst(isPending: (T) -> Boolean): List<T> = filter(isPending) + filterNot(isPending)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TAB_KEY = "requests_tab"
    }
}
