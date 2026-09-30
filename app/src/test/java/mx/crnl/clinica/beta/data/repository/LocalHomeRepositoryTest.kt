package mx.crnl.clinica.beta.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.appointment.AppointmentAction
import mx.crnl.clinica.beta.domain.home.HomeSummary
import mx.crnl.clinica.beta.domain.home.PatientMetric
import mx.crnl.clinica.beta.domain.model.AppointmentDraft
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.BetaAccounts
import mx.crnl.clinica.beta.testing.RepositoryTest
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.appointmentEntity
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.observeAround
import mx.crnl.clinica.beta.testing.patientDraft
import mx.crnl.clinica.beta.testing.success
import mx.crnl.clinica.beta.testing.testInstant
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Los indicadores de Inicio salen de Room: cambian cuando cambian los datos, no son valores fijos. */
@RunWith(AndroidJUnit4::class)
class LocalHomeRepositoryTest : RepositoryTest() {

    private val mariana = userAccount(id = BetaAccounts.PSYCHOLOGIST_ID, role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)
    private val rodrigo = userAccount(id = BetaAccounts.SECOND_PSYCHOLOGIST_ID, role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)
    private val coordinator = userAccount(id = BetaAccounts.COORDINATOR_ID, role = UserRole.AREA_COORDINATOR, area = ClinicalArea.PSYCHOLOGY)
    private val admin = userAccount(id = BetaAccounts.ADMIN_ID, role = UserRole.CLINICAL_ADMIN, area = null)

    private suspend fun summaryFor(user: UserAccount): HomeSummary = home.observeSummary(user).first()

    @Test
    fun `un profesional ve sus pacientes asignados y sus proximas citas`() = runTest {
        val summary = summaryFor(mariana)

        assertEquals(PatientMetric.ASSIGNED_TO_USER, summary.patientMetric)
        assertEquals(1, summary.patientCount)
        assertEquals(1, summary.upcomingAppointmentCount)
        assertEquals(listOf("e0000000-0000-4000-8000-00000000000a"), summary.upcomingAppointments.map { it.appointmentId })
        assertEquals(0, summary.pendingRequestCount)
    }

    @Test
    fun `otro profesional ve lo suyo, incluida la cita en curso`() = runTest {
        val summary = summaryFor(rodrigo)

        assertEquals(3, summary.patientCount)
        // La cita en curso de hoy y la reprogramada de dentro de una semana (reprogramada sigue por ocurrir).
        assertEquals(
            listOf("e0000000-0000-4000-8000-000000000007", "e0000000-0000-4000-8000-00000000000b"),
            summary.upcomingAppointments.map { it.appointmentId },
        )
        assertEquals(1, summary.todayAppointmentCount)
    }

    @Test
    fun `coordinacion ve los pacientes y citas de su area`() = runTest {
        val summary = summaryFor(coordinator)

        assertEquals(PatientMetric.AREA_PATIENTS, summary.patientMetric)
        assertEquals(4, summary.patientCount)
        assertEquals(3, summary.upcomingAppointmentCount)
        assertEquals(1, summary.todayAppointmentCount)
    }

    @Test
    fun `administracion ve los pacientes activos y todas las citas proximas`() = runTest {
        val summary = summaryFor(admin)

        assertEquals(PatientMetric.ALL_ACTIVE_PATIENTS, summary.patientMetric)
        assertEquals(7, summary.patientCount)
        assertEquals(6, summary.upcomingAppointmentCount)
        assertEquals(3, summary.upcomingAppointments.size)
        assertEquals(2, summary.todayAppointmentCount)
    }

    @Test
    fun `el administrador del sistema no ve citas en Inicio`() = runTest {
        val summary = summaryFor(insertSystemAdmin())

        assertEquals(0, summary.upcomingAppointmentCount)
        assertEquals(0, summary.todayAppointmentCount)
        assertEquals(emptyList<Any>(), summary.upcomingAppointments)
    }

    @Test
    fun `crear y cancelar una cita actualiza el resumen del profesional sin reiniciar`() = runTest {
        val draft = AppointmentDraft(
            "b0000000-0000-4000-8000-000000000001", ClinicalArea.PSYCHOLOGY, BetaAccounts.PSYCHOLOGIST_ID,
            testInstant(0, 15), 50, AppointmentModality.IN_PERSON, "Consultorio 3", null, null,
        )
        var createdId = ""
        val afterCreate = home.observeSummary(mariana).observeAround(
            change = { createdId = appointments.createAppointment(draft, BetaAccounts.COORDINATOR_ID).success().appointmentId },
            until = { it.upcomingAppointmentCount == 2 && it.todayAppointmentCount == 1 },
        )
        assertEquals(1, afterCreate.first().upcomingAppointmentCount)
        assertEquals(0, afterCreate.first().todayAppointmentCount)

        val afterCancel = home.observeSummary(mariana).observeAround(
            change = {
                appointments.applyAction(
                    createdId, AppointmentAction.CANCEL,
                    AppointmentStatus.SCHEDULED, null, BetaAccounts.COORDINATOR_ID,
                )
            },
            until = { it.upcomingAppointmentCount == 1 && it.todayAppointmentCount == 0 },
        )
        assertEquals(2, afterCancel.first().upcomingAppointmentCount)
        assertEquals(1, afterCancel.last().upcomingAppointmentCount)
    }

    @Test
    fun `los pacientes recientes son los ultimos cinco modificados`() = runTest {
        val recent = summaryFor(admin).recentPatients.map { it.patientNumber }

        assertEquals(listOf("CRNL-000008", "CRNL-000007", "CRNL-000006", "CRNL-000005", "CRNL-000004"), recent)
    }

    @Test
    fun `dar de alta un paciente actualiza el resumen sin reiniciar`() = runTest {
        val seen = home.observeSummary(admin).observeAround(
            change = { patients.createPatient(patientDraft(), BetaAccounts.ADMIN_ID) },
            until = { it.patientCount == 8 },
        )

        assertEquals(7, seen.first().patientCount)
        assertEquals("CRNL-000009", seen.last().recentPatients.first().patientNumber)
    }

    @Test
    fun `un alta aparece entre los recientes de un profesional sin cambiar sus pacientes asignados`() = runTest {
        patients.createPatient(patientDraft(), BetaAccounts.PSYCHOLOGIST_ID)

        val summary = summaryFor(mariana)

        assertEquals("CRNL-000009", summary.recentPatients.first().patientNumber)
        assertEquals(1, summary.patientCount)
    }

    @Test
    fun `editar un paciente lo sube a los recientes`() = runTest {
        val later = fixedClock(TestNow.plusDays(1))
        val laterPatients = LocalPatientRepository(db, AuditRecorder(db.auditDao(), later, newId), later, newId)
        val jose = "b0000000-0000-4000-8000-000000000004"

        laterPatients.updatePatient(jose, laterPatients.getPatientDraft(jose)!!.copy(municipality = "Guadalupe"), BetaAccounts.ADMIN_ID)

        assertEquals("CRNL-000004", summaryFor(admin).recentPatients.first().patientNumber)
    }

    @Test
    fun `una cita nueva del profesional se refleja al instante`() = runTest {
        val seen = home.observeSummary(mariana).observeAround(
            change = {
                db.appointmentDao().insert(
                    appointmentEntity(
                        id = "cita-nueva",
                        patientId = "b0000000-0000-4000-8000-000000000001",
                        professionalId = BetaAccounts.PSYCHOLOGIST_ID,
                        start = TestNow.plusDays(2).toInstant(),
                    ),
                )
            },
            until = { it.upcomingAppointmentCount == 2 },
        )

        assertEquals(1, seen.first().upcomingAppointmentCount)
        assertEquals(2, seen.last().upcomingAppointmentCount)
    }

    @Test
    fun `las solicitudes pendientes se cuentan por rol y solo las pendientes`() = runTest {
        val insert = "INSERT INTO interarea_access_requests VALUES (?, 'b0000000-0000-4000-8000-000000000003', ?, ?, ?, 'motivo', 'READ', 1, ?, NULL, NULL, NULL)"
        db.openHelper.writableDatabase.apply {
            execSQL(insert, arrayOf("r1", BetaAccounts.PSYCHOLOGIST_ID, "PSYCHOLOGY", "NUTRITION", "PENDING"))
            execSQL(insert, arrayOf("r2", BetaAccounts.PSYCHOLOGIST_ID, "PSYCHOLOGY", "NUTRITION", "APPROVED"))
        }

        assertEquals(1, summaryFor(mariana).pendingRequestCount)
        assertEquals(0, summaryFor(rodrigo).pendingRequestCount)
        assertEquals(0, summaryFor(coordinator).pendingRequestCount)
        assertEquals(1, summaryFor(admin).pendingRequestCount)
    }
}
