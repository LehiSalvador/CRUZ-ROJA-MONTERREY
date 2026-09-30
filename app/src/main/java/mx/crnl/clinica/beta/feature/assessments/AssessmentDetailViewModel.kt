package mx.crnl.clinica.beta.feature.assessments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mx.crnl.clinica.beta.core.navigation.AppRoute
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.domain.model.AssessmentDetail
import mx.crnl.clinica.beta.domain.repository.AssessmentRepository
import mx.crnl.clinica.beta.domain.repository.AuthRepository

/** Detalle de solo lectura de una aplicación; vacío si no existe o si la persona no puede leer su área. */
@OptIn(ExperimentalCoroutinesApi::class)
class AssessmentDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val assessmentRepository: AssessmentRepository,
) : ViewModel() {
    private val assessmentId = savedStateHandle.toRoute<AppRoute.AssessmentDetail>().assessmentId
    private val reloadCount = MutableStateFlow(0)

    val state: StateFlow<UiState<AssessmentDetail>> = reloadCount
        .flatMapLatest {
            authRepository.currentUser
                .filterNotNull()
                .flatMapLatest { viewer -> assessmentRepository.observeAssessment(assessmentId, viewer) }
                .map<AssessmentDetail?, UiState<AssessmentDetail>> { detail -> if (detail == null) UiState.Empty else UiState.Content(detail) }
                .onStart { emit(UiState.Loading) }
                .catch { emit(UiState.Error(it)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    fun retry() {
        reloadCount.update { it + 1 }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
