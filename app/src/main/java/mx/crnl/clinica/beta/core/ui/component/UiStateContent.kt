package mx.crnl.clinica.beta.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import mx.crnl.clinica.beta.core.ui.state.UiState

/** Resuelve qué mostrar para cada estado asíncrono; nunca deja la pantalla en blanco. */
@Composable
fun <T> UiStateContent(
    state: UiState<T>,
    onRetry: () -> Unit,
    empty: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when (state) {
            UiState.Loading -> LoadingState()
            UiState.Empty -> empty()
            is UiState.Content -> content(state.data)
            is UiState.Error -> ErrorState(onRetry = onRetry)
        }
    }
}
