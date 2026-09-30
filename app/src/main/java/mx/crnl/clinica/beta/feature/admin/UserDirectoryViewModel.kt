package mx.crnl.clinica.beta.feature.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.text.Collator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.util.ClinicTime
import mx.crnl.clinica.beta.domain.access.BetaUserAdministrationPolicy
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ManagedAccount
import mx.crnl.clinica.beta.domain.repository.AccountAdministrationRepository
import mx.crnl.clinica.beta.domain.repository.AuthRepository

/** Agrupaciones del directorio de usuarios. */
enum class UserFilter(val statuses: Set<AccountStatus>) {
    PENDING(setOf(AccountStatus.PENDING_APPROVAL)),
    ACTIVE(setOf(AccountStatus.ACTIVE)),
    SUSPENDED_OR_REJECTED(setOf(AccountStatus.SUSPENDED, AccountStatus.REJECTED)),
    INACTIVE(setOf(AccountStatus.INACTIVE)),
}

/**
 * Directorio de usuarios. [allowed] es falso cuando la persona no administra usuarios: en ese caso no se consulta nada
 * y [users] queda vacío, de modo que una ruta abierta sin permiso no muestra datos.
 */
data class UserDirectoryContent(
    val allowed: Boolean,
    val filter: UserFilter,
    val users: List<ManagedAccount>,
    val counts: Map<UserFilter, Int>,
    val canManage: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class UserDirectoryViewModel(
    private val savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    private val accountRepository: AccountAdministrationRepository,
) : ViewModel() {
    private val reloadCount = MutableStateFlow(0)

    val selectedFilter: StateFlow<String> = savedStateHandle.getStateFlow(FILTER_KEY, UserFilter.PENDING.name)

    private val accounts = reloadCount.flatMapLatest {
        authRepository.currentUser
            .filterNotNull()
            .flatMapLatest { viewer ->
                if (BetaUserAdministrationPolicy.canViewUserDirectory(viewer)) {
                    accountRepository.observeAccounts(viewer).map { viewer to it }
                } else {
                    flowOf(viewer to null)
                }
            }
    }

    val state: StateFlow<UiState<UserDirectoryContent>> = combine(accounts, selectedFilter) { (viewer, all), selected ->
        val filter = UserFilter.entries.firstOrNull { it.name == selected } ?: UserFilter.PENDING
        if (all == null) {
            UserDirectoryContent(allowed = false, filter = filter, users = emptyList(), counts = emptyMap(), canManage = false)
        } else {
            UserDirectoryContent(
                allowed = true,
                filter = filter,
                users = all.filter { it.user.status in filter.statuses }.sortedWith(byName),
                counts = UserFilter.entries.associateWith { group -> all.count { it.user.status in group.statuses } },
                canManage = BetaUserAdministrationPolicy.canManageUsers(viewer),
            )
        }
    }
        .map<UserDirectoryContent, UiState<UserDirectoryContent>> { UiState.Content(it) }
        .onStart { emit(UiState.Loading) }
        .catch { emit(UiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    fun selectFilter(filter: UserFilter) {
        savedStateHandle[FILTER_KEY] = filter.name
    }

    fun retry() {
        reloadCount.update { it + 1 }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val FILTER_KEY = "user_filter"

        private val collator: Collator = Collator.getInstance(ClinicTime.locale).apply { strength = Collator.PRIMARY }

        val byName: Comparator<ManagedAccount> = Comparator { first, second ->
            compareValuesBy(
                first,
                second,
                { collator.getCollationKey(it.user.paternalSurname) },
                { collator.getCollationKey(it.user.maternalSurname.orEmpty()) },
                { collator.getCollationKey(it.user.firstName) },
            )
        }
    }
}
