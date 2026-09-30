package mx.crnl.clinica.beta.feature.assessments

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
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.repository.AssessmentRepository
import mx.crnl.clinica.beta.domain.repository.AuthRepository

/** La vista previa solo se muestra cuando el repositorio la autorizó: hasta entonces no hay nada que dibujar. */
enum class SupervisedAccess { CHECKING, GRANTED, DENIED, FAILED }

/**
 * Vista previa del modo supervisado. No lee ni guarda datos clínicos: solo pide autorización (y deja constancia de que
 * se abrió) y, si la obtiene, deja mostrar una pantalla sin contenido del instrumento ni datos del paciente.
 */
class SupervisedModeViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val assessmentRepository: AssessmentRepository,
) : ViewModel() {
    private val assessmentId = savedStateHandle.toRoute<AppRoute.SupervisedMode>().assessmentId
    private val _access = MutableStateFlow(SupervisedAccess.CHECKING)
    val access: StateFlow<SupervisedAccess> = _access.asStateFlow()

    init {
        open()
    }

    fun retry() {
        _access.value = SupervisedAccess.CHECKING
        open()
    }

    private fun open() {
        viewModelScope.launch {
            val result = runCatchingCancellable {
                val actor = authRepository.currentUser.filterNotNull().first()
                assessmentRepository.openSupervisedPreview(assessmentId, actor.userId)
            }
            _access.update {
                result.fold(
                    onSuccess = { outcome ->
                        when (outcome) {
                            is OperationResult.Success -> SupervisedAccess.GRANTED
                            is OperationResult.Failure -> SupervisedAccess.DENIED
                        }
                    },
                    onFailure = { SupervisedAccess.FAILED },
                )
            }
        }
    }
}
