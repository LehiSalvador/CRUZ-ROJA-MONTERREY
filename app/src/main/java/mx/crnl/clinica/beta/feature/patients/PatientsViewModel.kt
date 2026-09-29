package mx.crnl.clinica.beta.feature.patients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.PatientSummary
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.PatientFilter
import mx.crnl.clinica.beta.domain.repository.PatientRepository

enum class PatientsScope { ALL, MINE }

data class PatientListItem(
    val patientId: String,
    val displayName: String,
    val patientNumber: String,
    val ageYears: Int,
    val sex: Sex,
    val status: PatientStatus,
    val areas: List<ClinicalArea>,
)

/** Los pacientes que cumplen una búsqueda y un filtro concretos; la pantalla los usa para no arrastrar la posición de otra lista. */
data class PatientListContent(val query: String, val scope: PatientsScope, val patients: List<PatientListItem>)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PatientsViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val patientRepository: PatientRepository,
    private val clock: Clock,
    private val searchDebounceMillis: Long = DEFAULT_SEARCH_DEBOUNCE_MILLIS,
) : ViewModel() {
    private val reloadCount = MutableStateFlow(0)

    /** Texto y filtro se guardan en el estado de la pantalla para sobrevivir a la navegación y a la rotación. */
    val query: StateFlow<String> = savedStateHandle.getStateFlow(QUERY_KEY, "")

    val scope: StateFlow<PatientsScope> = savedStateHandle.getStateFlow(SCOPE_KEY, PatientsScope.ALL.name)
        .map { PatientsScope.valueOf(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PatientsScope.ALL)

    /** «Mis pacientes» solo tiene sentido para quien atiende pacientes asignados. */
    val canFilterByAssignment: StateFlow<Boolean> = authRepository.currentUser
        .map { it?.role == UserRole.PROFESSIONAL }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    val state: StateFlow<UiState<PatientListContent>> = reloadCount
        .flatMapLatest {
            combine(
                query.debounce { text -> if (text.isBlank()) 0L else searchDebounceMillis },
                scope,
                authRepository.currentUser.filterNotNull(),
            ) { text, scope, user ->
                val filter = if (scope == PatientsScope.MINE && user.role == UserRole.PROFESSIONAL) {
                    PatientFilter.AssignedTo(user.userId)
                } else {
                    PatientFilter.All
                }
                Criteria(text, scope, filter)
            }.flatMapLatest { criteria ->
                patientRepository.observePatients(criteria.query, criteria.filter).map { patients ->
                    val today = LocalDate.now(clock)
                    PatientListContent(criteria.query, criteria.scope, patients.map { it.toListItem(today) })
                }
            }
                .map<PatientListContent, UiState<PatientListContent>> { content ->
                    if (content.patients.isEmpty()) UiState.Empty else UiState.Content(content)
                }
                .onStart { emit(UiState.Loading) }
                .catch { emit(UiState.Error(it)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    fun onQueryChange(text: String) {
        savedStateHandle[QUERY_KEY] = text
    }

    fun onScopeChange(scope: PatientsScope) {
        savedStateHandle[SCOPE_KEY] = scope.name
    }

    fun retry() {
        reloadCount.update { it + 1 }
    }

    private fun PatientSummary.toListItem(today: LocalDate) = PatientListItem(
        patientId = patient.patientId,
        displayName = patient.fullName,
        patientNumber = patient.patientNumber,
        ageYears = patient.ageOn(today),
        sex = patient.sex,
        status = patient.status,
        areas = areas,
    )

    private class Criteria(val query: String, val scope: PatientsScope, val filter: PatientFilter)

    companion object {
        const val DEFAULT_SEARCH_DEBOUNCE_MILLIS = 200L

        private const val QUERY_KEY = "query"
        private const val SCOPE_KEY = "scope"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
