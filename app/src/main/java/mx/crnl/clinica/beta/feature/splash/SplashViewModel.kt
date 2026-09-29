package mx.crnl.clinica.beta.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mx.crnl.clinica.beta.core.demo.LocalDataInitializer
import mx.crnl.clinica.beta.domain.repository.SessionRepository

enum class SplashDestination { LOGIN, MAIN }

sealed interface SplashUiState {
    data object Loading : SplashUiState

    data class Ready(val destination: SplashDestination) : SplashUiState

    data class Failed(val cause: Throwable) : SplashUiState
}

class SplashViewModel(
    private val localData: LocalDataInitializer,
    private val sessionRepository: SessionRepository,
    private val minimumDisplayMillis: Long = DEFAULT_MINIMUM_DISPLAY_MILLIS,
) : ViewModel() {
    private val _state = MutableStateFlow<SplashUiState>(SplashUiState.Loading)
    val state: StateFlow<SplashUiState> = _state.asStateFlow()

    private var startJob: Job? = null

    init {
        start()
    }

    fun retry() {
        if (_state.value is SplashUiState.Failed) start()
    }

    private fun start() {
        startJob?.cancel()
        _state.value = SplashUiState.Loading
        startJob = viewModelScope.launch {
            // Un tiempo mínimo evita que la marca parpadee cuando la inicialización es inmediata.
            val minimumDisplay = launch { delay(minimumDisplayMillis) }
            val result = runCatchingCancellable {
                localData.initialize()
                if (sessionRepository.isSessionActive.first()) SplashDestination.MAIN else SplashDestination.LOGIN
            }
            minimumDisplay.join()
            _state.value = result.fold(
                onSuccess = { SplashUiState.Ready(it) },
                onFailure = { SplashUiState.Failed(it) },
            )
        }
    }

    private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    companion object {
        const val DEFAULT_MINIMUM_DISPLAY_MILLIS = 700L
    }
}
