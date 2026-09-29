package mx.crnl.clinica.beta.domain.account

import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.domain.text.EmailRules
import mx.crnl.clinica.beta.domain.text.TextNormalizer

enum class AccountRequestField {
    FIRST_NAME,
    PATERNAL_SURNAME,
    MATERNAL_SURNAME,
    EMAIL,
    PASSWORD,
    PASSWORD_CONFIRMATION,
    AREA,
    ROLE,
    LICENSE,
}

enum class AccountRequestIssue { REQUIRED, INVALID, TOO_LONG, TOO_SHORT, MISMATCH }

/** Solicitud de cuenta tal como la captura la persona. La contraseña se conserva sin alterar. */
data class AccountRequest(
    val firstName: String = "",
    val paternalSurname: String = "",
    val maternalSurname: String = "",
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val area: ClinicalArea? = null,
    val role: UserRole? = null,
    val professionalLicense: String = "",
)

object AccountRequestValidator {
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 128

    /** Los administradores no se piden desde el registro: solo se solicitan roles clínicos. */
    val REQUESTABLE_ROLES: List<UserRole> = listOf(UserRole.PROFESSIONAL, UserRole.AREA_COORDINATOR)

    private const val MAX_NAME_LENGTH = 60
    private val NAME_PATTERN = Regex("^\\p{L}[\\p{L}\\p{M}'’. -]*$")
    private val LICENSE_PATTERN = Regex("^\\d{7,8}$")

    fun validate(request: AccountRequest): Map<AccountRequestField, AccountRequestIssue> = buildMap {
        nameIssue(request.firstName, required = true)?.let { put(AccountRequestField.FIRST_NAME, it) }
        nameIssue(request.paternalSurname, required = true)?.let { put(AccountRequestField.PATERNAL_SURNAME, it) }
        nameIssue(request.maternalSurname, required = false)?.let { put(AccountRequestField.MATERNAL_SURNAME, it) }
        emailIssue(request.email)?.let { put(AccountRequestField.EMAIL, it) }
        passwordIssue(request.password)?.let { put(AccountRequestField.PASSWORD, it) }
        if (!containsKey(AccountRequestField.PASSWORD) && request.passwordConfirmation != request.password) {
            put(AccountRequestField.PASSWORD_CONFIRMATION, AccountRequestIssue.MISMATCH)
        }
        if (request.area == null) put(AccountRequestField.AREA, AccountRequestIssue.REQUIRED)
        when (request.role) {
            null -> put(AccountRequestField.ROLE, AccountRequestIssue.REQUIRED)
            !in REQUESTABLE_ROLES -> put(AccountRequestField.ROLE, AccountRequestIssue.INVALID)
            else -> Unit
        }
        licenseIssue(request)?.let { put(AccountRequestField.LICENSE, it) }
    }

    /** La cédula es obligatoria para el rol profesional y opcional para coordinación. */
    fun requiresLicense(role: UserRole?): Boolean = role == UserRole.PROFESSIONAL

    private fun nameIssue(value: String, required: Boolean): AccountRequestIssue? {
        val text = TextNormalizer.tidy(value)
        return when {
            text.isEmpty() -> if (required) AccountRequestIssue.REQUIRED else null
            text.length > MAX_NAME_LENGTH -> AccountRequestIssue.TOO_LONG
            !NAME_PATTERN.matches(text) -> AccountRequestIssue.INVALID
            else -> null
        }
    }

    private fun emailIssue(value: String): AccountRequestIssue? {
        val text = value.trim()
        return when {
            text.isEmpty() -> AccountRequestIssue.REQUIRED
            text.length > EmailRules.MAX_LENGTH -> AccountRequestIssue.TOO_LONG
            !EmailRules.isValid(text) -> AccountRequestIssue.INVALID
            else -> null
        }
    }

    private fun passwordIssue(value: String): AccountRequestIssue? = when {
        value.isBlank() -> AccountRequestIssue.REQUIRED
        value.length < MIN_PASSWORD_LENGTH -> AccountRequestIssue.TOO_SHORT
        value.length > MAX_PASSWORD_LENGTH -> AccountRequestIssue.TOO_LONG
        else -> null
    }

    private fun licenseIssue(request: AccountRequest): AccountRequestIssue? {
        val license = request.professionalLicense.trim()
        return when {
            license.isEmpty() -> if (requiresLicense(request.role)) AccountRequestIssue.REQUIRED else null
            !LICENSE_PATTERN.matches(license) -> AccountRequestIssue.INVALID
            else -> null
        }
    }
}
