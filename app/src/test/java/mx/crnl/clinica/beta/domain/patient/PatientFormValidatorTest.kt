package mx.crnl.clinica.beta.domain.patient

import java.time.LocalDate
import mx.crnl.clinica.beta.domain.model.PatientDraft
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.testing.fixedClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientFormValidatorTest {
    private val validator = PatientFormValidator(fixedClock())

    private val valid = PatientFormInput(
        firstName = "José María",
        paternalSurname = "Ñandú",
        maternalSurname = "O'Connor",
        birthDate = "15/03/2010",
        birthPlace = "Monterrey, Nuevo León",
        sex = Sex.MALE,
        municipality = "San Nicolás de los Garza",
        populationType = PopulationType.STUDENT,
        phone = "81 2345 6789",
        email = "Jose.Maria@Example.org",
    )

    private fun issues(input: PatientFormInput) = validator.issues(input)

    private fun draft(input: PatientFormInput): PatientDraft =
        (validator.validate(input) as PatientFormResult.Valid).draft

    @Test
    fun `un formulario completo produce un borrador normalizado sin perder acentos ni apostrofes`() {
        val draft = draft(valid)

        assertEquals("José María", draft.firstName)
        assertEquals("Ñandú", draft.paternalSurname)
        assertEquals("O'Connor", draft.maternalSurname)
        assertEquals(LocalDate.of(2010, 3, 15), draft.birthDate)
        assertEquals("Monterrey, Nuevo León", draft.birthPlace)
        assertEquals(Sex.MALE, draft.sex)
        assertEquals("San Nicolás de los Garza", draft.municipality)
        assertEquals(PopulationType.STUDENT, draft.populationType)
        assertEquals("81 2345 6789", draft.phone)
        assertEquals("jose.maria@example.org", draft.email)
    }

    @Test
    fun `los campos opcionales vacios quedan nulos y el apellido materno ausente se conserva ausente`() {
        val draft = draft(valid.copy(maternalSurname = "   ", birthPlace = "", municipality = "  ", phone = "", email = ""))

        assertNull(draft.maternalSurname)
        assertNull(draft.birthPlace)
        assertNull(draft.municipality)
        assertNull(draft.phone)
        assertNull(draft.email)
    }

    @Test
    fun `recorta y colapsa los espacios de los textos`() {
        val draft = draft(valid.copy(firstName = "  Ana    Lucía ", paternalSurname = " Cavazos  ", municipality = " San   Pedro "))

        assertEquals("Ana Lucía", draft.firstName)
        assertEquals("Cavazos", draft.paternalSurname)
        assertEquals("San Pedro", draft.municipality)
    }

    @Test
    fun `nombre y apellido paterno son obligatorios y no aceptan solo espacios`() {
        val result = issues(valid.copy(firstName = "   ", paternalSurname = ""))

        assertEquals(FieldIssue.REQUIRED, result[PatientField.FIRST_NAME])
        assertEquals(FieldIssue.REQUIRED, result[PatientField.PATERNAL_SURNAME])
        assertNull(result[PatientField.MATERNAL_SURNAME])
    }

    @Test
    fun `los nombres no admiten digitos ni simbolos ajenos a un nombre`() {
        assertEquals(FieldIssue.INVALID, issues(valid.copy(firstName = "Ana2"))[PatientField.FIRST_NAME])
        assertEquals(FieldIssue.INVALID, issues(valid.copy(paternalSurname = "@dmin"))[PatientField.PATERNAL_SURNAME])
        assertEquals(FieldIssue.INVALID, issues(valid.copy(maternalSurname = "12"))[PatientField.MATERNAL_SURNAME])
        assertNull(issues(valid.copy(paternalSurname = "De la Garza-Villarreal Jr."))[PatientField.PATERNAL_SURNAME])
    }

    @Test
    fun `un nombre demasiado largo se rechaza`() {
        assertEquals(FieldIssue.TOO_LONG, issues(valid.copy(firstName = "A".repeat(61)))[PatientField.FIRST_NAME])
    }

    @Test
    fun `la fecha de nacimiento se lee con o sin separadores`() {
        assertEquals(LocalDate.of(2010, 3, 15), PatientFormValidator.parseBirthDate("15032010"))
        assertEquals(LocalDate.of(2010, 3, 15), PatientFormValidator.parseBirthDate("15/03/2010"))
        assertNull(PatientFormValidator.parseBirthDate("1503201"))
        assertNull(PatientFormValidator.parseBirthDate("31022000"))
        assertNull(PatientFormValidator.parseBirthDate("00012000"))
    }

    @Test
    fun `la fecha de nacimiento es obligatoria, valida y no futura`() {
        assertEquals(FieldIssue.REQUIRED, issues(valid.copy(birthDate = ""))[PatientField.BIRTH_DATE])
        assertEquals(FieldIssue.INVALID, issues(valid.copy(birthDate = "3103"))[PatientField.BIRTH_DATE])
        assertEquals(FieldIssue.INVALID, issues(valid.copy(birthDate = "31/02/2000"))[PatientField.BIRTH_DATE])
        assertEquals(FieldIssue.FUTURE_DATE, issues(valid.copy(birthDate = "30/09/2026"))[PatientField.BIRTH_DATE])
        assertEquals(FieldIssue.TOO_OLD, issues(valid.copy(birthDate = "31/12/1899"))[PatientField.BIRTH_DATE])
    }

    @Test
    fun `hoy es una fecha valida y manana no`() {
        assertNull(issues(valid.copy(birthDate = "29/09/2026"))[PatientField.BIRTH_DATE])
        assertEquals(FieldIssue.FUTURE_DATE, issues(valid.copy(birthDate = "30/09/2026"))[PatientField.BIRTH_DATE])
    }

    @Test
    fun `sexo y poblacion son obligatorios`() {
        val result = issues(valid.copy(sex = null, populationType = null))

        assertEquals(FieldIssue.REQUIRED, result[PatientField.SEX])
        assertEquals(FieldIssue.REQUIRED, result[PatientField.POPULATION_TYPE])
    }

    @Test
    fun `el telefono es opcional pero debe ser razonable si se captura`() {
        assertNull(issues(valid.copy(phone = ""))[PatientField.PHONE])
        assertNull(issues(valid.copy(phone = "+52 (81) 2345-6789"))[PatientField.PHONE])
        assertEquals(FieldIssue.INVALID, issues(valid.copy(phone = "12345"))[PatientField.PHONE])
        assertEquals(FieldIssue.INVALID, issues(valid.copy(phone = "8123456789 ext"))[PatientField.PHONE])
        assertEquals(FieldIssue.INVALID, issues(valid.copy(phone = "1".repeat(16)))[PatientField.PHONE])
    }

    @Test
    fun `el correo es opcional pero debe ser valido si se captura`() {
        assertNull(issues(valid.copy(email = ""))[PatientField.EMAIL])
        assertEquals(FieldIssue.INVALID, issues(valid.copy(email = "sin-arroba"))[PatientField.EMAIL])
        assertEquals(FieldIssue.INVALID, issues(valid.copy(email = "a@b"))[PatientField.EMAIL])
        assertEquals(FieldIssue.INVALID, issues(valid.copy(email = "con espacio@example.org"))[PatientField.EMAIL])
        assertEquals(FieldIssue.TOO_LONG, issues(valid.copy(email = "a".repeat(250) + "@example.org"))[PatientField.EMAIL])
    }

    @Test
    fun `se puede validar solo los campos de un paso`() {
        val identityOnly = validator.issues(PatientFormInput(), setOf(PatientField.FIRST_NAME, PatientField.SEX))

        assertEquals(setOf(PatientField.FIRST_NAME, PatientField.SEX), identityOnly.keys)
    }

    @Test
    fun `un formulario invalido reporta todos los problemas a la vez`() {
        val result = validator.validate(PatientFormInput(email = "roto")) as PatientFormResult.Invalid

        assertTrue(
            result.issues.keys.containsAll(
                listOf(
                    PatientField.FIRST_NAME,
                    PatientField.PATERNAL_SURNAME,
                    PatientField.BIRTH_DATE,
                    PatientField.SEX,
                    PatientField.POPULATION_TYPE,
                    PatientField.EMAIL,
                ),
            ),
        )
    }
}
