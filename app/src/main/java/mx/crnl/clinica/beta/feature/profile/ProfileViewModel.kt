package mx.crnl.clinica.beta.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.crnl.clinica.beta.core.util.runCatchingCancellable
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AuthRepository

data class ProfileUiState(
    val user: UserAccount? = null,
    val isSigningOut: Boolean = false,
    val hasError: Boolean = false,
)

class ProfileViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val signOutState = MutableStateFlow(ProfileUiState())

    val state: StateFlow<ProfileUiState> = combine(authRepository.currentUser, signOutState) { user, signOut ->
        signOut.copy(user = user)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ProfileUiState())

    fun onSignOut() {
        if (signOutState.value.isSigningOut) return
        signOutState.value = ProfileUiState(isSigningOut = true)
        viewModelScope.launch {
            val result = runCatchingCancellable { authRepository.signOut() }
            signOutState.update { it.copy(isSigningOut = false, hasError = result.isFailure) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
