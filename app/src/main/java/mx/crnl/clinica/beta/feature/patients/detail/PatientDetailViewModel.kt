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
import mx.crnl.clinica.beta.domain.model.PatientDetail
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.PatientRepository

data class PatientDetailContent(val detail: PatientDetail, val today: LocalDate, val now: Instant)

@OptIn(ExperimentalCoroutinesApi::class)
class PatientDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val patientRepository: PatientRepository,
    private val clock: Clock,
) : ViewModel() {
    private val patientId = savedStateHandle.toRoute<AppRoute.PatientDetail>().patientId
    private val reloadCount = MutableStateFlow(0)

    /** Vacío significa que el paciente no existe. */
    val state: StateFlow<UiState<PatientDetailContent>> = reloadCount
        .flatMapLatest {
            patientRepository.observePatientDetail(patientId)
                .map<PatientDetail?, UiState<PatientDetailContent>> { detail ->
                    if (detail == null) {
                        UiState.Empty
                    } else {
                        UiState.Content(PatientDetailContent(detail, LocalDate.now(clock), clock.instant()))
                    }
                }
                .onStart { emit(UiState.Loading) }
                .catch { emit(UiState.Error(it)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    init {
        recordView()
    }

    fun retry() {
        reloadCount.update { it + 1 }
    }

    // Una sola vez por entrada al expediente: el ViewModel sobrevive a la rotación y a volver desde la edición.
    // Un fallo al auditar no debe impedir mostrar el expediente.
    private fun recordView() {
        viewModelScope.launch {
            runCatchingCancellable {
                patientRepository.observePatientDetail(patientId).filterNotNull().first()
                val actor = authRepository.currentUser.filterNotNull().first()
                patientRepository.recordPatientViewed(patientId, actor.userId)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
