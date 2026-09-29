package mx.crnl.clinica.beta.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.core.demo.AssetSeedFileReader
import mx.crnl.clinica.beta.core.demo.DemoDataInitializer
import mx.crnl.clinica.beta.core.demo.DemoSeedLoader
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.testing.DatabaseTest
import mx.crnl.clinica.beta.testing.FakeSessionRepository
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.fixedClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Repositorios locales sobre Room real, alimentados con el conjunto ficticio completo. */
@RunWith(AndroidJUnit4::class)
class LocalRepositoriesTest : DatabaseTest() {
    private lateinit var patients: LocalPatientRepository
    private lateinit var appointments: LocalAppointmentRepository

    @Before
    fun seed() = runTest {
        DemoDataInitializer(
            seedDao = db.seedDao(),
            sessionRepository = FakeSessionRepository(),
            loader = DemoSeedLoader(AssetSeedFileReader(context.assets)),
            clock = fixedClock(),
        ).initialize()
        patients = LocalPatientRepository(db.patientDao())
        appointments = LocalAppointmentRepository(db.appointmentDao())
    }

    @Test
    fun `lista los pacientes ordenados por apellidos`() = runTest {
        val list = patients.observePatients().first()

        assertEquals(8, list.size)
        assertEquals(list.sortedWith(compareBy({ it.paternalSurname.lowercase() }, { it.maternalSurname?.lowercase() })), list)
    }

    @Test
    fun `mapea un paciente completo al modelo de dominio`() = runTest {
        val patient = patients.getPatient("b0000000-0000-4000-8000-000000000001")!!

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
        val list = appointments.observeAppointments().first()

        assertEquals(12, list.size)
        assertEquals(list.sortedBy { it.start }, list)

        val today = list.first { it.appointmentId == "e0000000-0000-4000-8000-000000000007" }
        assertEquals("Fernanda Guerra Domínguez", today.patientName)
        assertEquals("CRNL-000003", today.patientNumber)
        assertEquals("Rodrigo Villarreal", today.professionalName)
        assertEquals(ClinicalArea.PSYCHOLOGY, today.area)
        assertEquals(AppointmentModality.IN_PERSON, today.modality)
        assertEquals("Consultorio 2", today.location)
        assertEquals(AppointmentStatus.CONFIRMED, today.status)
        assertEquals(TestNow.toInstant(), today.start)
    }
}
