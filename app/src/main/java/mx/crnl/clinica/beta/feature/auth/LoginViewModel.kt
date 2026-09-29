package mx.crnl.clinica.beta.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.crnl.clinica.beta.domain.repository.SessionRepository

data class LoginUiState(
    val isSubmitting: Boolean = false,
    val isSessionStarted: Boolean = false,
    val hasError: Boolean = false,
)

class LoginViewModel(private val sessionRepository: SessionRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onContinue() {
        val current = _state.value
        if (current.isSubmitting || current.isSessionStarted) return
        _state.value = LoginUiState(isSubmitting = true)
        viewModelScope.launch {
            _state.value = try {
                sessionRepository.startSession()
                LoginUiState(isSessionStarted = true)
            } catch (error: IOException) {
                LoginUiState(hasError = true)
            }
        }
    }
}
