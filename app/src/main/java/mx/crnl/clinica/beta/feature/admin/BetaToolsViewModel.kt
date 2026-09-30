package mx.crnl.clinica.beta.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.crnl.clinica.beta.core.util.runCatchingCancellable
import mx.crnl.clinica.beta.domain.access.BetaAdminAccessPolicy
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.BetaMaintenanceRepository
import mx.crnl.clinica.beta.domain.repository.BetaResetSummary

/** Pasos del restablecimiento: dos confirmaciones explícitas antes de tocar nada. */
enum class ResetStep { IDLE, FIRST_CONFIRMATION, FINAL_CONFIRMATION, RESETTING, FAILED }

data class BetaToolsUiState(
    /** Nulo mientras se comprueba quién es la persona; falso si no puede usar las herramientas: entonces no se ofrece nada. */
    val allowed: Boolean? = null,
    val step: ResetStep = ResetStep.IDLE,
    val summary: BetaResetSummary? = null,
)

/** Herramientas de la Beta local. Hoy: volver al conjunto de datos ficticios inicial. */
class BetaToolsViewModel(
    private val authRepository: AuthRepository,
    private val maintenanceRepository: BetaMaintenanceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BetaToolsUiState())
    val state: StateFlow<BetaToolsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val user = runCatchingCancellable { authRepository.currentUser.filterNotNull().first() }.getOrNull()
            _state.update { it.copy(allowed = user != null && BetaAdminAccessPolicy.canResetBetaData(user)) }
        }
    }

    fun onStart() = advance(from = setOf(ResetStep.IDLE, ResetStep.FAILED), to = ResetStep.FIRST_CONFIRMATION)

    fun onContinue() = advance(from = setOf(ResetStep.FIRST_CONFIRMATION), to = ResetStep.FINAL_CONFIRMATION)

    fun onCancel() = advance(from = setOf(ResetStep.FIRST_CONFIRMATION, ResetStep.FINAL_CONFIRMATION), to = ResetStep.IDLE)

    fun onConfirm() {
        val current = _state.value
        if (current.allowed != true || current.step != ResetStep.FINAL_CONFIRMATION) return
        _state.update { it.copy(step = ResetStep.RESETTING) }
        viewModelScope.launch {
            val result = runCatchingCancellable {
                val actor = authRepository.currentUser.filterNotNull().first()
                maintenanceRepository.resetBetaData(actor.userId)
            }
            // Al terminar la sesión se cierra y la aplicación vuelve a la pantalla de acceso; este estado es solo el respaldo.
            _state.update { state ->
                result.fold(
                    onSuccess = { outcome ->
                        when (outcome) {
                            is OperationResult.Success -> state.copy(step = ResetStep.IDLE, summary = outcome.value)
                            is OperationResult.Failure -> state.copy(step = ResetStep.FAILED)
                        }
                    },
                    onFailure = { state.copy(step = ResetStep.FAILED) },
                )
            }
        }
    }

    private fun advance(from: Set<ResetStep>, to: ResetStep) {
        _state.update { if (it.allowed == true && it.step in from) it.copy(step = to) else it }
    }
}
