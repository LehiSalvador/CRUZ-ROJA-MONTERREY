package mx.crnl.clinica.beta.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalTextField
import mx.crnl.clinica.beta.core.ui.component.PasswordField
import mx.crnl.clinica.beta.core.ui.component.PrimaryButton
import mx.crnl.clinica.beta.core.ui.component.SecondaryButton
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.ui.theme.isCompactHeight
import mx.crnl.clinica.beta.domain.model.AccountStatus

@Composable
fun LoginRoute(factory: ViewModelProvider.Factory, onSessionStarted: () -> Unit, onRequestAccount: () -> Unit) {
    val viewModel: LoginViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isSessionStarted) {
        if (state.isSessionStarted) onSessionStarted()
    }
    LoginScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::onSubmit,
        onRequestAccount = onRequestAccount,
    )
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRequestAccount: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    // En pantallas de poca altura (teléfonos pequeños, horizontal) la marca y los márgenes se reducen para que todo el formulario quede a la vista.
    val compact = isCompactHeight()
    val sectionGap = if (compact) Spacing.md else Spacing.lg
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = if (compact) Spacing.md else Spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_brand_mark),
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 56.dp else 72.dp),
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(sectionGap))
                Text(
                    text = stringResource(R.string.login_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.login_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(sectionGap))
                ClinicalTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = stringResource(R.string.login_email_label),
                    enabled = !state.isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                )
                Spacer(Modifier.height(Spacing.sm))
                PasswordField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = stringResource(R.string.login_password_label),
                    enabled = !state.isSubmitting,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onSubmit()
                        },
                    ),
                )
                if (state.error != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(state.error.messageRes()),
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                PrimaryButton(
                    text = stringResource(R.string.login_submit),
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    loading = state.isSubmitting,
                )
                Spacer(Modifier.height(sectionGap))
                Text(
                    text = stringResource(R.string.login_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                SecondaryButton(
                    text = stringResource(R.string.login_request_account),
                    onClick = onRequestAccount,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSubmitting,
                )
            }
        }
    }
}

private fun LoginError.messageRes(): Int = when (this) {
    LoginError.MissingFields -> R.string.login_error_missing
    LoginError.InvalidCredentials -> R.string.login_error_invalid
    is LoginError.NotActive -> when (status) {
        AccountStatus.PENDING_APPROVAL -> R.string.login_error_pending
        AccountStatus.SUSPENDED -> R.string.login_error_suspended
        AccountStatus.REJECTED -> R.string.login_error_rejected
        AccountStatus.INACTIVE, AccountStatus.ACTIVE -> R.string.login_error_inactive
    }
    LoginError.Unavailable -> R.string.login_error_unavailable
}
