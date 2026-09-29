package mx.crnl.clinica.beta.feature.patients.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.crnl.clinica.beta.core.util.runCatchingCancellable
import mx.crnl.clinica.beta.domain.model.DuplicateCandidate
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.domain.patient.PatientField
import mx.crnl.clinica.beta.domain.patient.PatientFormResult
import mx.crnl.clinica.beta.domain.patient.PatientFormValidator
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.PatientRepository
import mx.crnl.clinica.beta.feature.patients.form.PatientFormState

/** Pasos del alta. Los tres primeros validan sus campos antes de avanzar. */
enum class NewPatientStep(val fields: Set<PatientField>) {
    IDENTITY(
        setOf(
            PatientField.FIRST_NAME,
            PatientField.PATERNAL_SURNAME,
            PatientField.MATERNAL_SURNAME,
            PatientField.BIRTH_DATE,
            PatientField.BIRTH_PLACE,
            PatientField.SEX,
        ),
    ),
    CONTACT(setOf(PatientField.PHONE, PatientField.EMAIL, PatientField.MUNICIPALITY)),
    POPULATION(setOf(PatientField.POPULATION_TYPE)),
    CONSENT(emptySet()),
    DUPLICATES(emptySet()),
    CONFIRMATION(emptySet()),
}

sealed interface DuplicateCheck {
    data object Checking : DuplicateCheck

    data object Clear : DuplicateCheck

    data class Found(val candidates: List<DuplicateCandidate>) : DuplicateCheck

    data object Failed : DuplicateCheck
}

data class CreatedPatient(val patientId: String, val patientNumber: String)

data class NewPatientUiState(
    val step: NewPatientStep = NewPatientStep.IDENTITY,
    val form: PatientFormState = PatientFormState(),
    val duplicates: DuplicateCheck = DuplicateCheck.Checking,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val created: CreatedPatient? = null,
) {
    /** Hay algo capturado que se perdería al abandonar el alta. */
    val hasProgress: Boolean
        get() = form.input != PatientFormState().input
}

class NewPatientViewModel(
    private val authRepository: AuthRepository,
    private val patientRepository: PatientRepository,
    private val validator: PatientFormValidator,
) : ViewModel() {
    private val _state = MutableStateFlow(NewPatientUiState())
    val state: StateFlow<NewPatientUiState> = _state.asStateFlow()

    private var duplicateCheckJob: Job? = null

    fun onTextChange(field: PatientField, value: String) = updateForm { it.withText(field, value) }

    fun onSexChange(sex: Sex) = updateForm { it.withSex(sex) }

    fun onPopulationTypeChange(type: PopulationType) = updateForm { it.withPopulationType(type) }

    fun onNext() {
        val current = _state.value
        if (current.isSaving || current.created != null) return
        when (current.step) {
            NewPatientStep.IDENTITY, NewPatientStep.CONTACT, NewPatientStep.POPULATION -> advanceIfValid(current)
            NewPatientStep.CONSENT -> startDuplicateCheck()
            NewPatientStep.DUPLICATES -> if (current.duplicates == DuplicateCheck.Clear) moveTo(NewPatientStep.CONFIRMATION)
            NewPatientStep.CONFIRMATION -> save()
        }
    }

    fun onBack() {
        val current = _state.value
        if (current.isSaving || current.created != null) return
        val previous = NewPatientStep.entries.getOrNull(current.step.ordinal - 1) ?: return
        duplicateCheckJob?.cancel()
        moveTo(previous)
    }

    /** Regresa al primer paso para corregir los datos capturados. */
    fun onEditData() {
        val current = _state.value
        if (current.isSaving || current.created != null) return
        duplicateCheckJob?.cancel()
        moveTo(NewPatientStep.IDENTITY)
    }

    /** Continúa a la confirmación pese a las coincidencias; la interfaz pide una confirmación explícita antes. */
    fun onCreateAnyway() {
        val current = _state.value
        if (current.step == NewPatientStep.DUPLICATES && current.duplicates is DuplicateCheck.Found) {
            moveTo(NewPatientStep.CONFIRMATION)
        }
    }

    fun onRetryDuplicateCheck() {
        if (_state.value.step == NewPatientStep.DUPLICATES) startDuplicateCheck()
    }

    private fun advanceIfValid(current: NewPatientUiState) {
        val issues = validator.issues(current.form.input, current.step.fields)
        if (issues.isNotEmpty()) {
            _state.update { it.copy(form = it.form.withIssues(it.form.issues + issues)) }
            return
        }
        moveTo(NewPatientStep.entries[current.step.ordinal + 1])
    }

    private fun startDuplicateCheck() {
        val draft = when (val result = validator.validate(_state.value.form.input)) {
            is PatientFormResult.Valid -> result.draft
            is PatientFormResult.Invalid -> {
                // Solo ocurre si se saltó un paso: se regresa al primero con problemas.
                val firstInvalid = NewPatientStep.entries.first { step -> step.fields.any { it in result.issues } }
                _state.update { it.copy(step = firstInvalid, form = it.form.withIssues(result.issues)) }
                return
            }
        }
        duplicateCheckJob?.cancel()
        _state.update { it.copy(step = NewPatientStep.DUPLICATES, duplicates = DuplicateCheck.Checking) }
        duplicateCheckJob = viewModelScope.launch {
            val outcome = runCatchingCancellable { patientRepository.findDuplicateCandidates(draft) }
            _state.update { state ->
                state.copy(
                    duplicates = outcome.fold(
                        onSuccess = { if (it.isEmpty()) DuplicateCheck.Clear else DuplicateCheck.Found(it) },
                        onFailure = { DuplicateCheck.Failed },
                    ),
                )
            }
        }
    }

    private fun save() {
        val draft = (validator.validate(_state.value.form.input) as? PatientFormResult.Valid)?.draft ?: return
        _state.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            val outcome = runCatchingCancellable {
                val actor = authRepository.currentUser.filterNotNull().first()
                patientRepository.createPatient(draft, actor.userId)
            }
            _state.update { state ->
                outcome.fold(
                    onSuccess = { patient ->
                        state.copy(isSaving = false, created = CreatedPatient(patient.patientId, patient.patientNumber))
                    },
                    onFailure = { state.copy(isSaving = false, saveFailed = true) },
                )
            }
        }
    }

    private fun moveTo(step: NewPatientStep) {
        _state.update { it.copy(step = step, saveFailed = false) }
    }

    private fun updateForm(transform: (PatientFormState) -> PatientFormState) {
        _state.update { it.copy(form = transform(it.form)) }
    }
}
