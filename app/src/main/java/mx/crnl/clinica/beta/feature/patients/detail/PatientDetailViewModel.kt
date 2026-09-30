package mx.crnl.clinica.beta.feature.patients.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import mx.crnl.clinica.beta.domain.clinical.AreaCapabilities
import mx.crnl.clinica.beta.domain.clinical.AreaCapabilitiesResolver
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.PatientDetail
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.PatientRepository

/**
 * El expediente tal como lo ve [viewer] (ya recortado por el repositorio) y lo que puede hacer en cada área.
 * [initialArea] es el área cuya pestaña se abre primero, si la ruta la indicó.
 */
data class PatientDetailContent(
    val detail: PatientDetail,
    val today: LocalDate,
    val now: Instant,
    val viewer: UserAccount,
    val initialArea: ClinicalArea? = null,
) {
    fun capabilities(area: ClinicalArea): AreaCapabilities =
        AreaCapabilitiesResolver.resolve(viewer, area, detail.activeAssignment(area))

    /** Hay al menos un área donde se puede agendar una cita para este paciente. */
    val canSchedule: Boolean
        get() = ClinicalArea.entries.any { capabilities(it).canSchedule }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PatientDetailViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val patientRepository: PatientRepository,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<AppRoute.PatientDetail>()
    private val patientId = route.patientId
    private val initialArea = route.area?.let { code -> ClinicalArea.entries.firstOrNull { it.name == code } }
    private val reloadCount = MutableStateFlow(0)

    /**
     * Pestaña abierta del expediente (nombre de la sección). Vive aquí y no en la pantalla porque al volver de un
     * formulario la pantalla se recompone desde cero y la persona debe regresar a la sección donde estaba.
     */
    val selectedTab: StateFlow<String> = savedStateHandle.getStateFlow(TAB_KEY, initialArea?.name ?: DEFAULT_TAB)

    /** Vacío significa que el paciente no existe. */
    val state: StateFlow<UiState<PatientDetailContent>> = reloadCount
        .flatMapLatest {
            authRepository.currentUser
                .filterNotNull()
                .flatMapLatest { viewer ->
                    patientRepository.observePatientDetail(patientId, viewer)
                        .map<PatientDetail?, UiState<PatientDetailContent>> { detail ->
                            if (detail == null) {
                                UiState.Empty
                            } else {
                                UiState.Content(PatientDetailContent(detail, LocalDate.now(clock), clock.instant(), viewer, initialArea))
                            }
                        }
                }
                .onStart { emit(UiState.Loading) }
                .catch { emit(UiState.Error(it)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    init {
        recordView()
    }

    fun selectTab(tab: String) {
        savedStateHandle[TAB_KEY] = tab
    }

    fun retry() {
        reloadCount.update { it + 1 }
    }

    // Una sola vez por entrada al expediente: el ViewModel sobrevive a la rotación y a volver desde la edición.
    // Un fallo al auditar no debe impedir mostrar el expediente.
    private fun recordView() {
        viewModelScope.launch {
            runCatchingCancellable {
                val actor = authRepository.currentUser.filterNotNull().first()
                patientRepository.observePatientDetail(patientId, actor).filterNotNull().first()
                patientRepository.recordPatientViewed(patientId, actor.userId)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TAB_KEY = "detail_tab"
        const val DEFAULT_TAB = "SUMMARY"
    }
}
