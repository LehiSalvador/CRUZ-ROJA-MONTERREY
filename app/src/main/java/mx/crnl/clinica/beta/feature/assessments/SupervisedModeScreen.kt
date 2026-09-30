package mx.crnl.clinica.beta.feature.assessments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ConfirmActionDialog
import mx.crnl.clinica.beta.core.ui.component.ErrorState
import mx.crnl.clinica.beta.core.ui.component.LoadingState
import mx.crnl.clinica.beta.core.ui.component.NotAuthorizedState
import mx.crnl.clinica.beta.core.ui.component.PrimaryButton
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing

@Composable
fun SupervisedModeRoute(factory: ViewModelProvider.Factory, onExit: () -> Unit) {
    val viewModel: SupervisedModeViewModel = viewModel(factory = factory)
    val access by viewModel.access.collectAsStateWithLifecycle()
    SupervisedModeScreen(access = access, onExit = onExit, onRetry = viewModel::retry)
}

/**
 * Pantalla completa y aislada: sin barra inferior, sin datos del paciente y sin contenido del instrumento (no hay
 * reactivos autorizados). Salir pide confirmar para volver al contenido clínico del profesional.
 */
@Composable
fun SupervisedModeScreen(access: SupervisedAccess, onExit: () -> Unit, onRetry: () -> Unit) {
    var confirmingExit by rememberSaveable { mutableStateOf(false) }
    // Atrás no debe abandonar la pantalla sin la confirmación.
    BackHandler(enabled = access == SupervisedAccess.GRANTED) { confirmingExit = true }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.safeDrawingPadding().fillMaxSize(), contentAlignment = Alignment.Center) {
            when (access) {
                SupervisedAccess.CHECKING -> LoadingState()
                SupervisedAccess.FAILED -> ErrorState(onRetry = onRetry)
                SupervisedAccess.DENIED -> NotAuthorizedState(onBack = onExit)
                SupervisedAccess.GRANTED -> Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.supervised_title),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.supervised_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    PrimaryButton(
                        text = stringResource(R.string.supervised_return),
                        onClick = { confirmingExit = true },
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                    )
                }
            }
        }
    }

    if (confirmingExit) {
        ConfirmActionDialog(
            title = stringResource(R.string.supervised_exit_title),
            message = stringResource(R.string.supervised_exit_message),
            confirmLabel = stringResource(R.string.supervised_exit_confirm),
            onConfirm = {
                confirmingExit = false
                onExit()
            },
            onDismiss = { confirmingExit = false },
        )
    }
}
