package mx.crnl.clinica.beta.feature.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import mx.crnl.clinica.beta.domain.access.BetaAdminAccessPolicy
import mx.crnl.clinica.beta.domain.model.AuditCategory
import mx.crnl.clinica.beta.domain.model.AuditFilter
import mx.crnl.clinica.beta.domain.model.AuditPeriod
import mx.crnl.clinica.beta.domain.model.AuditRecord
import mx.crnl.clinica.beta.domain.repository.AuditRepository
import mx.crnl.clinica.beta.domain.repository.AuthRepository

/** La bitácora tal como se consulta. [allowed] es falso si la persona no puede verla: entonces no se lee nada. */
data class AuditContent(val allowed: Boolean, val filter: AuditFilter, val entries: List<AuditRecord>)

@OptIn(ExperimentalCoroutinesApi::class)
class AuditViewModel(
    private val savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    private val auditRepository: AuditRepository,
) : ViewModel() {
    private val reloadCount = MutableStateFlow(0)

    private val filter: StateFlow<AuditFilter> = combine(
        savedStateHandle.getStateFlow(CATEGORY_KEY, ""),
        savedStateHandle.getStateFlow(PERIOD_KEY, AuditPeriod.ALL.name),
    ) { category, period ->
        AuditFilter(
            category = AuditCategory.entries.firstOrNull { it.name == category },
            period = AuditPeriod.entries.firstOrNull { it.name == period } ?: AuditPeriod.ALL,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AuditFilter())

    val state: StateFlow<UiState<AuditContent>> = reloadCount
        .flatMapLatest {
            combine(authRepository.currentUser.filterNotNull(), filter) { viewer, current -> viewer to current }
                .flatMapLatest { (viewer, current) ->
                    if (BetaAdminAccessPolicy.canViewAudit(viewer)) {
                        auditRepository.observeEntries(current, viewer).map { AuditContent(true, current, it) }
                    } else {
                        flowOf(AuditContent(false, current, emptyList()))
                    }
                }
                .map<AuditContent, UiState<AuditContent>> { UiState.Content(it) }
                .onStart { emit(UiState.Loading) }
                .catch { emit(UiState.Error(it)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    fun selectCategory(category: AuditCategory?) {
        savedStateHandle[CATEGORY_KEY] = category?.name.orEmpty()
    }

    fun selectPeriod(period: AuditPeriod) {
        savedStateHandle[PERIOD_KEY] = period.name
    }

    fun retry() {
        reloadCount.update { it + 1 }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val CATEGORY_KEY = "audit_category"
        const val PERIOD_KEY = "audit_period"
    }
}
