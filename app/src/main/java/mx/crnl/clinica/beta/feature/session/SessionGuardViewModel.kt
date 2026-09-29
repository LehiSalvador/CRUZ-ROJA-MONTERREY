package mx.crnl.clinica.beta.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AuthRepository

/**
 * Vigila la sesión mientras se usa la shell principal: si termina (cierre de sesión o cuenta que deja de estar
 * activa) la interfaz regresa al acceso sin depender de quién la terminó.
 */
class SessionGuardViewModel(authRepository: AuthRepository) : ViewModel() {
    /** Verdadero con sesión vigente, falso cuando terminó y nulo mientras todavía no se sabe. */
    val hasSession: StateFlow<Boolean?> = authRepository.currentUser
        .map<UserAccount?, Boolean?> { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
