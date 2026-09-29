package mx.crnl.clinica.beta.feature.patients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.state.asListUiState
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.repository.PatientRepository

data class PatientListItem(
    val patientId: String,
    val displayName: String,
    val patientNumber: String,
    val ageYears: Int,
    val municipality: String?,
    val populationType: PopulationType,
    val status: PatientStatus,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PatientsViewModel(
    private val patientRepository: PatientRepository,
    private val clock: Clock,
) : ViewModel() {
    private val reloadCount = MutableStateFlow(0)

    val state: StateFlow<UiState<List<PatientListItem>>> = reloadCount
        .flatMapLatest {
            patientRepository.observePatients()
                .map { patients ->
                    val today = LocalDate.now(clock)
                    patients.map { it.toListItem(today) }
                }
                .asListUiState()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    fun retry() {
        reloadCount.update { it + 1 }
    }

    private fun Patient.toListItem(today: LocalDate) = PatientListItem(
        patientId = patientId,
        displayName = fullName,
        patientNumber = patientNumber,
        ageYears = ageOn(today),
        municipality = municipality,
        populationType = populationType,
        status = status,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
