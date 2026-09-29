package mx.crnl.clinica.beta.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.crnl.clinica.beta.core.util.runCatchingCancellable
import mx.crnl.clinica.beta.domain.account.AccountRequest
import mx.crnl.clinica.beta.domain.account.AccountRequestField
import mx.crnl.clinica.beta.domain.account.AccountRequestIssue
import mx.crnl.clinica.beta.domain.account.AccountRequestValidator
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.domain.repository.AccountRequestResult
import mx.crnl.clinica.beta.domain.repository.AuthRepository

data class RequestAccountUiState(
    val form: AccountRequest = AccountRequest(),
    val issues: Map<AccountRequestField, AccountRequestIssue> = emptyMap(),
    val emailTaken: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val hasError: Boolean = false,
)

class RequestAccountViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(RequestAccountUiState())
    val state: StateFlow<RequestAccountUiState> = _state.asStateFlow()

    fun onTextChange(field: AccountRequestField, value: String) = edit(field) { form ->
        when (field) {
            AccountRequestField.FIRST_NAME -> form.copy(firstName = value)
            AccountRequestField.PATERNAL_SURNAME -> form.copy(paternalSurname = value)
            AccountRequestField.MATERNAL_SURNAME -> form.copy(maternalSurname = value)
            AccountRequestField.EMAIL -> form.copy(email = value)
            AccountRequestField.PASSWORD -> form.copy(password = value)
            AccountRequestField.PASSWORD_CONFIRMATION -> form.copy(passwordConfirmation = value)
            AccountRequestField.LICENSE -> form.copy(professionalLicense = value)
            AccountRequestField.AREA, AccountRequestField.ROLE -> form
        }
    }

    fun onAreaChange(area: ClinicalArea) = edit(AccountRequestField.AREA) { it.copy(area = area) }

    fun onRoleChange(role: UserRole) = edit(AccountRequestField.ROLE) { it.copy(role = role) }

    fun onSubmit() {
        val current = _state.value
        if (current.isSubmitting || current.isSubmitted) return
        val issues = AccountRequestValidator.validate(current.form)
        if (issues.isNotEmpty()) {
            _state.update { it.copy(issues = issues, hasError = false) }
            return
        }
        _state.update { it.copy(issues = emptyMap(), isSubmitting = true, hasError = false) }
        viewModelScope.launch {
            val result = runCatchingCancellable { authRepository.requestAccount(current.form) }
            _state.update { state ->
                result.fold(
                    onSuccess = { outcome ->
                        when (outcome) {
                            AccountRequestResult.Submitted -> state.copy(
                                form = AccountRequest(),
                                isSubmitting = false,
                                isSubmitted = true,
                            )
                            is AccountRequestResult.Invalid -> state.copy(isSubmitting = false, issues = outcome.issues)
                            AccountRequestResult.EmailAlreadyRegistered -> state.copy(isSubmitting = false, emailTaken = true)
                        }
                    },
                    onFailure = { state.copy(isSubmitting = false, hasError = true) },
                )
            }
        }
    }

    private fun edit(field: AccountRequestField, transform: (AccountRequest) -> AccountRequest) {
        val cleared = if (field == AccountRequestField.PASSWORD) {
            setOf(field, AccountRequestField.PASSWORD_CONFIRMATION)
        } else {
            setOf(field)
        }
        _state.update { state ->
            state.copy(
                form = transform(state.form),
                issues = state.issues - cleared,
                emailTaken = state.emailTaken && field != AccountRequestField.EMAIL,
                hasError = false,
            )
        }
    }
}
