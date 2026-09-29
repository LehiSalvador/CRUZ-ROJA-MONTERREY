package mx.crnl.clinica.beta.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.crnl.clinica.beta.domain.repository.SessionRepository

data class ProfileUiState(
    val isSigningOut: Boolean = false,
    val isSignedOut: Boolean = false,
    val hasError: Boolean = false,
)

class ProfileViewModel(private val sessionRepository: SessionRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun onSignOut() {
        val current = _state.value
        if (current.isSigningOut || current.isSignedOut) return
        _state.value = ProfileUiState(isSigningOut = true)
        viewModelScope.launch {
            _state.value = try {
                sessionRepository.endSession()
                ProfileUiState(isSignedOut = true)
            } catch (error: IOException) {
                ProfileUiState(hasError = true)
            }
        }
    }
}
