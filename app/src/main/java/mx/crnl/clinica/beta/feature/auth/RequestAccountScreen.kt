package mx.crnl.clinica.beta.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ChoiceChipGroup
import mx.crnl.clinica.beta.core.ui.component.ClinicalTextField
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.PasswordField
import mx.crnl.clinica.beta.core.ui.component.PrimaryButton
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.domain.account.AccountRequestField
import mx.crnl.clinica.beta.domain.account.AccountRequestIssue
import mx.crnl.clinica.beta.domain.account.AccountRequestValidator
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserRole

@Composable
fun RequestAccountRoute(factory: ViewModelProvider.Factory, onBackToLogin: () -> Unit) {
    val viewModel: RequestAccountViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    RequestAccountScreen(
        state = state,
        onTextChange = viewModel::onTextChange,
        onAreaChange = viewModel::onAreaChange,
        onRoleChange = viewModel::onRoleChange,
        onSubmit = viewModel::onSubmit,
        onBackToLogin = onBackToLogin,
    )
}

@Composable
fun RequestAccountScreen(
    state: RequestAccountUiState,
    onTextChange: (AccountRequestField, String) -> Unit,
    onAreaChange: (ClinicalArea) -> Unit,
    onRoleChange: (UserRole) -> Unit,
    onSubmit: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    Scaffold(
        topBar = { ClinicalTopBar(title = stringResource(R.string.request_title), onNavigateUp = onBackToLogin) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
            if (state.isSubmitted) {
                SubmittedContent(onBackToLogin)
            } else {
                RequestForm(state, onTextChange, onAreaChange, onRoleChange, onSubmit)
            }
        }
    }
}

@Composable
private fun RequestForm(
    state: RequestAccountUiState,
    onTextChange: (AccountRequestField, String) -> Unit,
    onAreaChange: (ClinicalArea) -> Unit,
    onRoleChange: (UserRole) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val form = state.form
    val moveDown = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    val nameOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false, imeAction = ImeAction.Next)

    @Composable
    fun message(field: AccountRequestField): String? {
        if (field == AccountRequestField.EMAIL && state.emailTaken) return stringResource(R.string.request_issue_email_taken)
        val issue = state.issues[field] ?: return null
        val isChoice = field == AccountRequestField.AREA || field == AccountRequestField.ROLE
        return if (issue == AccountRequestIssue.REQUIRED && isChoice) stringResource(R.string.issue_select) else issue.message()
    }

    Column(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.request_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ClinicalTextField(
            value = form.firstName,
            onValueChange = { onTextChange(AccountRequestField.FIRST_NAME, it) },
            label = stringResource(R.string.request_first_name),
            error = message(AccountRequestField.FIRST_NAME),
            keyboardOptions = nameOptions,
            keyboardActions = moveDown,
        )
        ClinicalTextField(
            value = form.paternalSurname,
            onValueChange = { onTextChange(AccountRequestField.PATERNAL_SURNAME, it) },
            label = stringResource(R.string.request_paternal_surname),
            error = message(AccountRequestField.PATERNAL_SURNAME),
            keyboardOptions = nameOptions,
            keyboardActions = moveDown,
        )
        ClinicalTextField(
            value = form.maternalSurname,
            onValueChange = { onTextChange(AccountRequestField.MATERNAL_SURNAME, it) },
            label = stringResource(R.string.request_maternal_surname),
            error = message(AccountRequestField.MATERNAL_SURNAME),
            keyboardOptions = nameOptions,
            keyboardActions = moveDown,
        )
        ClinicalTextField(
            value = form.email,
            onValueChange = { onTextChange(AccountRequestField.EMAIL, it) },
            label = stringResource(R.string.request_email),
            error = message(AccountRequestField.EMAIL),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = moveDown,
        )
        PasswordField(
            value = form.password,
            onValueChange = { onTextChange(AccountRequestField.PASSWORD, it) },
            label = stringResource(R.string.request_password),
            error = message(AccountRequestField.PASSWORD),
            helper = pluralStringResource(
                R.plurals.request_password_hint,
                AccountRequestValidator.MIN_PASSWORD_LENGTH,
                AccountRequestValidator.MIN_PASSWORD_LENGTH,
            ),
            keyboardActions = moveDown,
        )
        PasswordField(
            value = form.passwordConfirmation,
            onValueChange = { onTextChange(AccountRequestField.PASSWORD_CONFIRMATION, it) },
            label = stringResource(R.string.request_password_confirmation),
            error = message(AccountRequestField.PASSWORD_CONFIRMATION),
            keyboardActions = moveDown,
        )
        ChoiceChipGroup(
            label = stringResource(R.string.request_area),
            options = ClinicalArea.entries,
            selected = form.area,
            onSelected = onAreaChange,
            optionLabel = { stringResource(it.labelRes()) },
            error = message(AccountRequestField.AREA),
        )
        ChoiceChipGroup(
            label = stringResource(R.string.request_role),
            options = AccountRequestValidator.REQUESTABLE_ROLES,
            selected = form.role,
            onSelected = onRoleChange,
            optionLabel = { stringResource(it.labelRes()) },
            error = message(AccountRequestField.ROLE),
        )
        ClinicalTextField(
            value = form.professionalLicense,
            onValueChange = { onTextChange(AccountRequestField.LICENSE, it) },
            label = stringResource(
                if (AccountRequestValidator.requiresLicense(form.role)) R.string.request_license else R.string.request_license_optional,
            ),
            error = message(AccountRequestField.LICENSE),
            helper = stringResource(R.string.request_license_hint),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
        if (state.hasError) {
            Text(
                text = stringResource(R.string.request_error),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        PrimaryButton(
            text = stringResource(R.string.request_submit),
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            loading = state.isSubmitting,
        )
    }
}

@Composable
private fun SubmittedContent(onBackToLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.padding(bottom = Spacing.xs),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Text(
            text = stringResource(R.string.request_success_title),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.request_success_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(
            text = stringResource(R.string.request_back_to_login),
            onClick = onBackToLogin,
            modifier = Modifier.widthIn(min = 220.dp),
        )
    }
}

@Composable
private fun AccountRequestIssue.message(): String = when (this) {
    AccountRequestIssue.REQUIRED -> stringResource(R.string.request_issue_required)
    AccountRequestIssue.INVALID -> stringResource(R.string.request_issue_invalid)
    AccountRequestIssue.TOO_LONG -> stringResource(R.string.request_issue_too_long)
    AccountRequestIssue.TOO_SHORT -> pluralStringResource(
        R.plurals.request_issue_too_short,
        AccountRequestValidator.MIN_PASSWORD_LENGTH,
        AccountRequestValidator.MIN_PASSWORD_LENGTH,
    )
    AccountRequestIssue.MISMATCH -> stringResource(R.string.request_issue_mismatch)
}
