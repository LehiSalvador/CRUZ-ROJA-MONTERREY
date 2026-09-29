package mx.crnl.clinica.beta.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.Period

data class Patient(
    val patientId: String,
    val patientNumber: String,
    val firstName: String,
    val paternalSurname: String,
    val maternalSurname: String?,
    val birthDate: LocalDate,
    val birthPlace: String?,
    val sex: Sex,
    val municipality: String?,
    val populationType: PopulationType,
    val status: PatientStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val fullName: String
        get() = listOfNotNull(firstName, paternalSurname, maternalSurname).joinToString(" ")

    /** La edad nunca se persiste: siempre se deriva de la fecha de nacimiento. */
    fun ageOn(date: LocalDate): Int = Period.between(birthDate, date).years.coerceAtLeast(0)
}
