package mx.crnl.clinica.beta.core.ui.state

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data object Empty : UiState<Nothing>

    data class Content<T>(val data: T) : UiState<T>

    data class Error(val cause: Throwable) : UiState<Nothing>
}

/** Convierte un flujo de datos en estados de UI: Loading al inicio, Empty/Content por cada emisión, Error si falla. */
fun <T> Flow<T>.asUiState(isEmpty: (T) -> Boolean): Flow<UiState<T>> =
    map<T, UiState<T>> { data -> if (isEmpty(data)) UiState.Empty else UiState.Content(data) }
        .onStart { emit(UiState.Loading) }
        .catch { error -> emit(UiState.Error(error)) }

fun <T> Flow<List<T>>.asListUiState(): Flow<UiState<List<T>>> = asUiState { it.isEmpty() }
