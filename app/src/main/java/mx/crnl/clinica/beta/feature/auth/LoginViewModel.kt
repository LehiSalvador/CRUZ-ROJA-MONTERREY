package mx.crnl.clinica.beta.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.crnl.clinica.beta.core.util.runCatchingCancellable
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.SignInResult

sealed interface LoginError {
    data object MissingFields : LoginError

    data object InvalidCredentials : LoginError

    data class NotActive(val status: AccountStatus) : LoginError

    data object Unavailable : LoginError
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val isSessionStarted: Boolean = false,
    val error: LoginError? = null,
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun onSubmit() {
        val current = _state.value
        if (current.isSubmitting || current.isSessionStarted) return
        if (current.email.isBlank() || current.password.isEmpty()) {
            _state.update { it.copy(error = LoginError.MissingFields) }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = runCatchingCancellable { authRepository.signIn(current.email, current.password) }
            _state.update { state ->
                result.fold(
                    onSuccess = { signIn ->
                        when (signIn) {
                            is SignInResult.Success -> state.copy(password = "", isSubmitting = false, isSessionStarted = true)
                            SignInResult.InvalidCredentials -> state.failed(LoginError.InvalidCredentials)
                            is SignInResult.AccountNotActive -> state.failed(LoginError.NotActive(signIn.status))
                        }
                    },
                    onFailure = { state.failed(LoginError.Unavailable) },
                )
            }
        }
    }

    private fun LoginUiState.failed(error: LoginError) = copy(isSubmitting = false, error = error)
}
