package mx.crnl.clinica.beta.feature.patients.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.crnl.clinica.beta.core.navigation.AppRoute
import mx.crnl.clinica.beta.core.util.runCatchingCancellable
import mx.crnl.clinica.beta.domain.model.DuplicateCandidate
import mx.crnl.clinica.beta.domain.model.PatientDraft
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.domain.patient.DuplicateDetector
import mx.crnl.clinica.beta.domain.patient.PatientField
import mx.crnl.clinica.beta.domain.patient.PatientFormInput
import mx.crnl.clinica.beta.domain.patient.PatientFormResult
import mx.crnl.clinica.beta.domain.patient.PatientFormValidator
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.PatientRepository
import mx.crnl.clinica.beta.feature.patients.form.PatientFormState

data class EditPatientUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val notFound: Boolean = false,
    val form: PatientFormState = PatientFormState(),
    val initial: PatientFormInput? = null,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    /** No nulo mientras se muestra la advertencia de posibles duplicados antes de guardar. */
    val duplicateWarning: List<DuplicateCandidate>? = null,
    val isSaved: Boolean = false,
) {
    val hasChanges: Boolean
        get() = initial != null && form.input != initial
}

class EditPatientViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val patientRepository: PatientRepository,
    private val validator: PatientFormValidator,
) : ViewModel() {
    private val patientId = savedStateHandle.toRoute<AppRoute.EditPatient>().patientId
    private val _state = MutableStateFlow(EditPatientUiState())
    val state: StateFlow<EditPatientUiState> = _state.asStateFlow()

    private var original: PatientDraft? = null

    init {
        load()
    }

    fun onTextChange(field: PatientField, value: String) = updateForm { it.withText(field, value) }

    fun onSexChange(sex: Sex) = updateForm { it.withSex(sex) }

    fun onPopulationTypeChange(type: PopulationType) = updateForm { it.withPopulationType(type) }

    fun onRetryLoad() {
        _state.value = EditPatientUiState()
        load()
    }

    fun onSave() {
        val current = _state.value
        if (current.isLoading || current.isSaving || current.isSaved || current.duplicateWarning != null) return
        val draft = when (val result = validator.validate(current.form.input)) {
            is PatientFormResult.Valid -> result.draft
            is PatientFormResult.Invalid -> {
                _state.update { it.copy(form = it.form.withIssues(result.issues), saveFailed = false) }
                return
            }
        }
        val before = original
        when {
            before == null -> return
            draft == before -> _state.update { it.copy(isSaved = true) }
            DuplicateDetector.changesIdentity(before, draft) -> checkDuplicatesThenSave(draft)
            else -> persist(draft)
        }
    }

    /** Guarda pese a las coincidencias, tras la confirmación explícita de la persona. */
    fun onConfirmDuplicates() {
        val current = _state.value
        if (current.duplicateWarning == null || current.isSaving) return
        val draft = (validator.validate(current.form.input) as? PatientFormResult.Valid)?.draft ?: return
        _state.update { it.copy(duplicateWarning = null) }
        persist(draft)
    }

    fun onDismissDuplicates() {
        _state.update { it.copy(duplicateWarning = null) }
    }

    private fun load() {
        viewModelScope.launch {
            val draft = runCatchingCancellable { patientRepository.getPatientDraft(patientId) }
            _state.update { state ->
                draft.fold(
                    onSuccess = { loaded ->
                        original = loaded
                        if (loaded == null) {
                            state.copy(isLoading = false, notFound = true)
                        } else {
                            val form = PatientFormState.from(loaded)
                            state.copy(isLoading = false, form = form, initial = form.input)
                        }
                    },
                    onFailure = { state.copy(isLoading = false, loadFailed = true) },
                )
            }
        }
    }

    private fun checkDuplicatesThenSave(draft: PatientDraft) {
        _state.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            val candidates = runCatchingCancellable { patientRepository.findDuplicateCandidates(draft, excludePatientId = patientId) }
            candidates.fold(
                onSuccess = { found ->
                    if (found.isEmpty()) {
                        persistNow(draft)
                    } else {
                        _state.update { it.copy(isSaving = false, duplicateWarning = found) }
                    }
                },
                onFailure = { _state.update { it.copy(isSaving = false, saveFailed = true) } },
            )
        }
    }

    private fun persist(draft: PatientDraft) {
        _state.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch { persistNow(draft) }
    }

    private suspend fun persistNow(draft: PatientDraft) {
        val outcome = runCatchingCancellable {
            val actor = authRepository.currentUser.filterNotNull().first()
            patientRepository.updatePatient(patientId, draft, actor.userId)
        }
        _state.update { state ->
            outcome.fold(
                onSuccess = { state.copy(isSaving = false, isSaved = true) },
                onFailure = { state.copy(isSaving = false, saveFailed = true) },
            )
        }
    }

    private fun updateForm(transform: (PatientFormState) -> PatientFormState) {
        _state.update { it.copy(form = transform(it.form), saveFailed = false) }
    }
}
