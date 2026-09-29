package mx.crnl.clinica.beta.domain.account

import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRequestValidatorTest {
    private val valid = AccountRequest(
        firstName = "Valeria",
        paternalSurname = "Ramos",
        maternalSurname = "Cisneros",
        email = "valeria.ramos@example.org",
        password = "clave-segura-1",
        passwordConfirmation = "clave-segura-1",
        area = ClinicalArea.NUTRITION,
        role = UserRole.PROFESSIONAL,
        professionalLicense = "12345678",
    )

    private fun issues(request: AccountRequest) = AccountRequestValidator.validate(request)

    @Test
    fun `una solicitud completa no tiene problemas`() {
        assertEquals(emptyMap<AccountRequestField, AccountRequestIssue>(), issues(valid))
    }

    @Test
    fun `nombre, apellido paterno, correo, contrasena, area y rol son obligatorios`() {
        val result = issues(AccountRequest())

        assertEquals(AccountRequestIssue.REQUIRED, result[AccountRequestField.FIRST_NAME])
        assertEquals(AccountRequestIssue.REQUIRED, result[AccountRequestField.PATERNAL_SURNAME])
        assertEquals(AccountRequestIssue.REQUIRED, result[AccountRequestField.EMAIL])
        assertEquals(AccountRequestIssue.REQUIRED, result[AccountRequestField.PASSWORD])
        assertEquals(AccountRequestIssue.REQUIRED, result[AccountRequestField.AREA])
        assertEquals(AccountRequestIssue.REQUIRED, result[AccountRequestField.ROLE])
        assertNull("el apellido materno es opcional", result[AccountRequestField.MATERNAL_SURNAME])
    }

    @Test
    fun `el correo debe tener forma de correo`() {
        assertEquals(AccountRequestIssue.INVALID, issues(valid.copy(email = "no-es-correo"))[AccountRequestField.EMAIL])
        assertEquals(AccountRequestIssue.INVALID, issues(valid.copy(email = "a@b"))[AccountRequestField.EMAIL])
        assertNull(issues(valid.copy(email = "  VALERIA@Example.org "))[AccountRequestField.EMAIL])
    }

    @Test
    fun `la contrasena exige una longitud minima razonable y nada mas`() {
        assertEquals(AccountRequestIssue.TOO_SHORT, issues(valid.copy(password = "corta12", passwordConfirmation = "corta12"))[AccountRequestField.PASSWORD])
        assertNull("sin reglas de complejidad", issues(valid.copy(password = "todaslasletras", passwordConfirmation = "todaslasletras"))[AccountRequestField.PASSWORD])
        assertEquals(AccountRequestIssue.TOO_LONG, issues(valid.copy(password = "x".repeat(129), passwordConfirmation = "x".repeat(129)))[AccountRequestField.PASSWORD])
        assertEquals(AccountRequestIssue.REQUIRED, issues(valid.copy(password = "        ", passwordConfirmation = "        "))[AccountRequestField.PASSWORD])
    }

    @Test
    fun `la contrasena se toma tal como se escribio sin recortar espacios`() {
        val spaced = "  clave 1  "

        assertNull(issues(valid.copy(password = spaced, passwordConfirmation = spaced))[AccountRequestField.PASSWORD])
        assertEquals(AccountRequestIssue.MISMATCH, issues(valid.copy(password = spaced, passwordConfirmation = spaced.trim()))[AccountRequestField.PASSWORD_CONFIRMATION])
    }

    @Test
    fun `la confirmacion debe coincidir`() {
        val result = issues(valid.copy(passwordConfirmation = "otra-clave-1"))

        assertEquals(AccountRequestIssue.MISMATCH, result[AccountRequestField.PASSWORD_CONFIRMATION])
        assertNull(result[AccountRequestField.PASSWORD])
    }

    @Test
    fun `no se puede solicitar un rol administrativo`() {
        UserRole.entries.filterNot { it in AccountRequestValidator.REQUESTABLE_ROLES }.forEach { role ->
            assertEquals("$role", AccountRequestIssue.INVALID, issues(valid.copy(role = role))[AccountRequestField.ROLE])
        }
        assertEquals(listOf(UserRole.PROFESSIONAL, UserRole.AREA_COORDINATOR), AccountRequestValidator.REQUESTABLE_ROLES)
    }

    @Test
    fun `la cedula es obligatoria para un profesional y opcional para coordinacion`() {
        assertEquals(AccountRequestIssue.REQUIRED, issues(valid.copy(professionalLicense = ""))[AccountRequestField.LICENSE])
        assertNull(issues(valid.copy(role = UserRole.AREA_COORDINATOR, professionalLicense = ""))[AccountRequestField.LICENSE])
        assertTrue(AccountRequestValidator.requiresLicense(UserRole.PROFESSIONAL))
        assertFalse(AccountRequestValidator.requiresLicense(UserRole.AREA_COORDINATOR))
    }

    @Test
    fun `una cedula capturada debe tener siete u ocho digitos`() {
        assertNull(issues(valid.copy(professionalLicense = "1234567"))[AccountRequestField.LICENSE])
        assertEquals(AccountRequestIssue.INVALID, issues(valid.copy(professionalLicense = "123456"))[AccountRequestField.LICENSE])
        assertEquals(AccountRequestIssue.INVALID, issues(valid.copy(professionalLicense = "123456789"))[AccountRequestField.LICENSE])
        assertEquals(AccountRequestIssue.INVALID, issues(valid.copy(professionalLicense = "12A45678"))[AccountRequestField.LICENSE])
        assertEquals(
            AccountRequestIssue.INVALID,
            issues(valid.copy(role = UserRole.AREA_COORDINATOR, professionalLicense = "abc"))[AccountRequestField.LICENSE],
        )
    }

    @Test
    fun `los nombres no aceptan digitos`() {
        assertEquals(AccountRequestIssue.INVALID, issues(valid.copy(firstName = "Val3ria"))[AccountRequestField.FIRST_NAME])
        assertEquals(AccountRequestIssue.TOO_LONG, issues(valid.copy(paternalSurname = "R".repeat(61)))[AccountRequestField.PATERNAL_SURNAME])
    }
}
