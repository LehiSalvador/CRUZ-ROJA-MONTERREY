package mx.crnl.clinica.beta.domain

import java.time.Instant
import java.time.LocalDate
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Test

class PatientTest {
    private fun patient(
        birthDate: LocalDate = LocalDate.of(1998, 5, 14),
        maternalSurname: String? = "Ibarra",
    ) = Patient(
        patientId = "p1",
        patientNumber = "CRNL-000001",
        firstName = "Ana Lucía",
        paternalSurname = "Cavazos",
        maternalSurname = maternalSurname,
        birthDate = birthDate,
        birthPlace = null,
        sex = Sex.FEMALE,
        municipality = null,
        populationType = PopulationType.STUDENT,
        status = PatientStatus.ACTIVE,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `calcula la edad cumplida a partir de la fecha de nacimiento`() {
        assertEquals(28, patient().ageOn(LocalDate.of(2026, 9, 29)))
    }

    @Test
    fun `el dia del cumpleanos ya cuenta el nuevo año`() {
        assertEquals(28, patient().ageOn(LocalDate.of(2026, 5, 14)))
    }

    @Test
    fun `un dia antes del cumpleanos todavia no lo cuenta`() {
        assertEquals(27, patient().ageOn(LocalDate.of(2026, 5, 13)))
    }

    @Test
    fun `una fecha anterior al nacimiento nunca da edad negativa`() {
        assertEquals(0, patient().ageOn(LocalDate.of(1990, 1, 1)))
    }

    @Test
    fun `el nombre completo incluye ambos apellidos cuando existen`() {
        assertEquals("Ana Lucía Cavazos Ibarra", patient().fullName)
    }

    @Test
    fun `el nombre completo omite el apellido materno ausente`() {
        assertEquals("Ana Lucía Cavazos", patient(maternalSurname = null).fullName)
    }
}
