package mx.crnl.clinica.beta.domain.patient

import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import mx.crnl.clinica.beta.domain.model.PatientDraft
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.domain.text.EmailRules
import mx.crnl.clinica.beta.domain.text.TextNormalizer

enum class PatientField {
    FIRST_NAME,
    PATERNAL_SURNAME,
    MATERNAL_SURNAME,
    BIRTH_DATE,
    BIRTH_PLACE,
    SEX,
    MUNICIPALITY,
    POPULATION_TYPE,
    PHONE,
    EMAIL,
}

enum class FieldIssue { REQUIRED, INVALID, TOO_LONG, FUTURE_DATE, TOO_OLD }

/** Lo capturado en el formulario tal como lo escribió la persona; `birthDate` admite dígitos o «dd/mm/aaaa». */
data class PatientFormInput(
    val firstName: String = "",
    val paternalSurname: String = "",
    val maternalSurname: String = "",
    val birthDate: String = "",
    val birthPlace: String = "",
    val sex: Sex? = null,
    val municipality: String = "",
    val populationType: PopulationType? = null,
    val phone: String = "",
    val email: String = "",
)

sealed interface PatientFormResult {
    data class Valid(val draft: PatientDraft) : PatientFormResult

    data class Invalid(val issues: Map<PatientField, FieldIssue>) : PatientFormResult
}

/** Reglas de captura de un paciente: normaliza sin destruir acentos ni nombres legítimos y reporta cada campo. */
class PatientFormValidator(private val clock: Clock) {

    fun validate(input: PatientFormInput): PatientFormResult {
        val issues = issues(input)
        if (issues.isNotEmpty()) return PatientFormResult.Invalid(issues)
        return PatientFormResult.Valid(
            PatientDraft(
                firstName = TextNormalizer.tidy(input.firstName),
                paternalSurname = TextNormalizer.tidy(input.paternalSurname),
                maternalSurname = optional(input.maternalSurname),
                birthDate = requireNotNull(parseBirthDate(input.birthDate)),
                birthPlace = optional(input.birthPlace),
                sex = requireNotNull(input.sex),
                municipality = optional(input.municipality),
                populationType = requireNotNull(input.populationType),
                phone = optional(input.phone),
                email = input.email.trim().lowercase().ifEmpty { null },
            ),
        )
    }

    /** Problemas de los campos indicados; sirve para validar un paso del alta sin exigir los siguientes. */
    fun issues(input: PatientFormInput, fields: Set<PatientField> = PatientField.entries.toSet()): Map<PatientField, FieldIssue> =
        buildMap {
            for (field in fields) {
                val issue = when (field) {
                    PatientField.FIRST_NAME -> nameIssue(input.firstName, required = true)
                    PatientField.PATERNAL_SURNAME -> nameIssue(input.paternalSurname, required = true)
                    PatientField.MATERNAL_SURNAME -> nameIssue(input.maternalSurname, required = false)
                    PatientField.BIRTH_DATE -> birthDateIssue(input.birthDate)
                    PatientField.BIRTH_PLACE -> textIssue(input.birthPlace)
                    PatientField.SEX -> if (input.sex == null) FieldIssue.REQUIRED else null
                    PatientField.MUNICIPALITY -> textIssue(input.municipality)
                    PatientField.POPULATION_TYPE -> if (input.populationType == null) FieldIssue.REQUIRED else null
                    PatientField.PHONE -> phoneIssue(input.phone)
                    PatientField.EMAIL -> emailIssue(input.email)
                }
                if (issue != null) put(field, issue)
            }
        }

    private fun nameIssue(value: String, required: Boolean): FieldIssue? {
        val text = TextNormalizer.tidy(value)
        return when {
            text.isEmpty() -> if (required) FieldIssue.REQUIRED else null
            text.length > MAX_NAME_LENGTH -> FieldIssue.TOO_LONG
            !NAME_PATTERN.matches(text) -> FieldIssue.INVALID
            else -> null
        }
    }

    private fun textIssue(value: String): FieldIssue? =
        if (TextNormalizer.tidy(value).length > MAX_TEXT_LENGTH) FieldIssue.TOO_LONG else null

    private fun phoneIssue(value: String): FieldIssue? {
        val text = TextNormalizer.tidy(value)
        return when {
            text.isEmpty() -> null
            !PHONE_PATTERN.matches(text) -> FieldIssue.INVALID
            text.count(Char::isDigit) !in PHONE_DIGITS -> FieldIssue.INVALID
            else -> null
        }
    }

    private fun emailIssue(value: String): FieldIssue? {
        val text = value.trim()
        return when {
            text.isEmpty() -> null
            text.length > EmailRules.MAX_LENGTH -> FieldIssue.TOO_LONG
            !EmailRules.isValid(text) -> FieldIssue.INVALID
            else -> null
        }
    }

    private fun birthDateIssue(value: String): FieldIssue? {
        if (value.none(Char::isDigit)) return FieldIssue.REQUIRED
        val date = parseBirthDate(value) ?: return FieldIssue.INVALID
        return when {
            date.isAfter(LocalDate.now(clock)) -> FieldIssue.FUTURE_DATE
            date.isBefore(EARLIEST_BIRTH_DATE) -> FieldIssue.TOO_OLD
            else -> null
        }
    }

    private fun optional(value: String): String? = TextNormalizer.tidy(value).ifEmpty { null }

    companion object {
        private const val MAX_NAME_LENGTH = 60
        private const val MAX_TEXT_LENGTH = 100
        private val PHONE_DIGITS = 10..15
        private val EARLIEST_BIRTH_DATE: LocalDate = LocalDate.of(1900, 1, 1)
        private val NAME_PATTERN = Regex("^\\p{L}[\\p{L}\\p{M}'’. -]*$")
        private val PHONE_PATTERN = Regex("^[+\\d\\s().-]+$")

        /** Interpreta ocho dígitos como día, mes y año («15032010» o «15/03/2010»). */
        fun parseBirthDate(text: String): LocalDate? {
            val digits = text.filter(Char::isDigit)
            if (digits.length != BIRTH_DATE_DIGITS) return null
            return try {
                LocalDate.of(digits.substring(4).toInt(), digits.substring(2, 4).toInt(), digits.substring(0, 2).toInt())
            } catch (_: DateTimeException) {
                null
            }
        }

        private const val BIRTH_DATE_DIGITS = 8
    }
}
