package mx.crnl.clinica.beta.feature.patients.form

import java.time.format.DateTimeFormatter
import mx.crnl.clinica.beta.domain.model.PatientDraft
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.domain.patient.FieldIssue
import mx.crnl.clinica.beta.domain.patient.PatientField
import mx.crnl.clinica.beta.domain.patient.PatientFormInput

/** Lo capturado en el formulario de un paciente y los problemas que se muestran junto a cada campo. */
data class PatientFormState(
    val input: PatientFormInput = PatientFormInput(),
    val issues: Map<PatientField, FieldIssue> = emptyMap(),
) {
    /** Editar un campo retira su error; la fecha de nacimiento solo conserva dígitos (máscara dd/mm/aaaa). */
    fun withText(field: PatientField, value: String): PatientFormState {
        val updated = when (field) {
            PatientField.FIRST_NAME -> input.copy(firstName = value)
            PatientField.PATERNAL_SURNAME -> input.copy(paternalSurname = value)
            PatientField.MATERNAL_SURNAME -> input.copy(maternalSurname = value)
            PatientField.BIRTH_DATE -> input.copy(birthDate = value.filter(Char::isDigit).take(BIRTH_DATE_DIGITS))
            PatientField.BIRTH_PLACE -> input.copy(birthPlace = value)
            PatientField.MUNICIPALITY -> input.copy(municipality = value)
            PatientField.PHONE -> input.copy(phone = value)
            PatientField.EMAIL -> input.copy(email = value)
            PatientField.SEX, PatientField.POPULATION_TYPE -> input
        }
        return copy(input = updated, issues = issues - field)
    }

    fun withSex(sex: Sex): PatientFormState = copy(input = input.copy(sex = sex), issues = issues - PatientField.SEX)

    fun withPopulationType(type: PopulationType): PatientFormState =
        copy(input = input.copy(populationType = type), issues = issues - PatientField.POPULATION_TYPE)

    fun withIssues(newIssues: Map<PatientField, FieldIssue>): PatientFormState = copy(issues = newIssues)

    companion object {
        private const val BIRTH_DATE_DIGITS = 8
        private val DIGITS_FORMAT = DateTimeFormatter.ofPattern("ddMMuuuu")

        fun from(draft: PatientDraft): PatientFormState = PatientFormState(
            PatientFormInput(
                firstName = draft.firstName,
                paternalSurname = draft.paternalSurname,
                maternalSurname = draft.maternalSurname.orEmpty(),
                birthDate = draft.birthDate.format(DIGITS_FORMAT),
                birthPlace = draft.birthPlace.orEmpty(),
                sex = draft.sex,
                municipality = draft.municipality.orEmpty(),
                populationType = draft.populationType,
                phone = draft.phone.orEmpty(),
                email = draft.email.orEmpty(),
            ),
        )
    }
}
