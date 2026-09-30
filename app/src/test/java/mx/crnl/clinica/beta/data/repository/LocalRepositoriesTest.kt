package mx.crnl.clinica.beta.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.testing.RepositoryTest
import mx.crnl.clinica.beta.testing.SeedIds
import mx.crnl.clinica.beta.testing.TestNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Repositorios locales sobre Room real, alimentados con el conjunto ficticio completo. */
@RunWith(AndroidJUnit4::class)
class LocalRepositoriesTest : RepositoryTest() {

    @Test
    fun `lista los pacientes ordenados por apellidos sin distinguir acentos ni mayusculas`() = runTest {
        val folios = patients.observePatients().first().map { it.patient.patientNumber }

        // Cavazos, Guerra, Mireles, Molina, Ochoa, Quiroga, Sepúlveda, Treviño
        assertEquals(
            listOf("CRNL-000001", "CRNL-000003", "CRNL-000006", "CRNL-000008", "CRNL-000004", "CRNL-000005", "CRNL-000007", "CRNL-000002"),
            folios,
        )
    }

    @Test
    fun `cada paciente lista las areas donde tiene una asignacion vigente`() = runTest {
        val byFolio = patients.observePatients().first().associateBy { it.patient.patientNumber }

        assertEquals(listOf(ClinicalArea.PSYCHOLOGY, ClinicalArea.NUTRITION), byFolio.getValue("CRNL-000003").areas)
        assertEquals(listOf(ClinicalArea.NUTRITION, ClinicalArea.GENERAL_MEDICINE), byFolio.getValue("CRNL-000006").areas)
        // Su asignación de Psicología terminó: no tiene ninguna vigente.
        assertEquals(emptyList<ClinicalArea>(), byFolio.getValue("CRNL-000008").areas)
    }

    @Test
    fun `mapea un paciente completo al modelo de dominio`() = runTest {
        val patient = patients.getPatient(PATIENT_ANA)!!

        assertEquals("CRNL-000001", patient.patientNumber)
        assertEquals("Ana Lucía Cavazos Ibarra", patient.fullName)
        assertEquals(LocalDate.of(1998, 5, 14), patient.birthDate)
        assertEquals(Sex.FEMALE, patient.sex)
        assertEquals(PopulationType.STUDENT, patient.populationType)
        assertEquals(PatientStatus.ACTIVE, patient.status)
        assertEquals("Monterrey", patient.municipality)
        assertEquals(28, patient.ageOn(LocalDate.of(2026, 9, 29)))
    }

    @Test
    fun `un paciente inexistente devuelve nulo`() = runTest {
        assertNull(patients.getPatient("no-existe"))
    }

    @Test
    fun `lista las citas cronologicamente con los nombres resueltos`() = runTest {
        val list = appointments.observeAppointments(account(SeedIds.HECTOR)).first()

        assertEquals(12, list.size)
        assertEquals(list.sortedBy { it.start }, list)

        val today = list.first { it.appointmentId == "e0000000-0000-4000-8000-000000000007" }
        assertEquals("Fernanda Guerra Domínguez", today.patientName)
        assertEquals("CRNL-000003", today.patientNumber)
        assertEquals("Rodrigo Villarreal", today.professionalName)
        assertEquals("a0000000-0000-4000-8000-000000000004", today.professionalId)
        assertEquals(ClinicalArea.PSYCHOLOGY, today.area)
        assertEquals(AppointmentModality.IN_PERSON, today.modality)
        assertEquals("Consultorio 2", today.location)
        assertEquals(AppointmentStatus.CONFIRMED, today.status)
        assertEquals(TestNow.toInstant(), today.start)
    }

    private companion object {
        const val PATIENT_ANA = "b0000000-0000-4000-8000-000000000001"
    }
}
