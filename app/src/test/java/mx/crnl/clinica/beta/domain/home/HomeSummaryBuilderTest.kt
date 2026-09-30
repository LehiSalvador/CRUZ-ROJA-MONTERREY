package mx.crnl.clinica.beta.domain.home

import java.time.Duration
import java.time.Instant
import mx.crnl.clinica.beta.domain.model.ActiveAssignment
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.PatientRecord
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.domainAppointment
import mx.crnl.clinica.beta.testing.domainPatient
import mx.crnl.clinica.beta.testing.patientRecord
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSummaryBuilderTest {
    private val now: Instant = TestNow.toInstant()
    private fun later(hours: Long): Instant = now.plus(Duration.ofHours(hours))

    private fun record(id: String, professional: String?, area: ClinicalArea = ClinicalArea.PSYCHOLOGY, status: PatientStatus = PatientStatus.ACTIVE, updated: Long = 0): PatientRecord =
        patientRecord(
            domainPatient(id = id, number = "CRNL-00000${id.takeLast(1)}", status = status, updatedAt = Instant.ofEpochSecond(updated)),
            assignments = listOfNotNull(professional?.let { ActiveAssignment(area, it) }),
        )

    private val mariana = userAccount(id = "mariana", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)
    private val coordinator = userAccount(id = "claudia", role = UserRole.AREA_COORDINATOR, area = ClinicalArea.PSYCHOLOGY)
    private val admin = userAccount(id = "hector", role = UserRole.CLINICAL_ADMIN, area = null)

    private val patients = listOf(
        record("p1", professional = "mariana"),
        record("p2", professional = "mariana"),
        record("p3", professional = "rodrigo"),
        record("p4", professional = "paola", area = ClinicalArea.NUTRITION),
        record("p5", professional = null, status = PatientStatus.INACTIVE),
    )

    private fun summary(
        user: UserAccount,
        appointments: List<AppointmentSummary> = emptyList(),
        pending: List<PendingAccessRequest> = emptyList(),
    ) = HomeSummaryBuilder.build(user, patients, appointments, PendingWork(accessRequests = pending), now)

    @Test
    fun `un profesional cuenta los pacientes con una asignacion vigente a su nombre`() {
        val result = summary(mariana)

        assertEquals(PatientMetric.ASSIGNED_TO_USER, result.patientMetric)
        assertEquals(2, result.patientCount)
    }

    @Test
    fun `sin asignaciones el conteo es cero y no un valor inventado`() {
        val result = HomeSummaryBuilder.build(mariana, emptyList(), emptyList(), PendingWork(), now)

        assertEquals(0, result.patientCount)
        assertEquals(emptyList<Any>(), result.recentPatients)
        assertEquals(emptyList<Any>(), result.upcomingAppointments)
    }

    @Test
    fun `coordinacion cuenta los pacientes atendidos en su area`() {
        val result = summary(coordinator)

        assertEquals(PatientMetric.AREA_PATIENTS, result.patientMetric)
        assertEquals(3, result.patientCount)
    }

    @Test
    fun `administracion cuenta los pacientes activos`() {
        val result = summary(admin)

        assertEquals(PatientMetric.ALL_ACTIVE_PATIENTS, result.patientMetric)
        assertEquals(4, result.patientCount)
    }

    @Test
    fun `las proximas citas del profesional excluyen las ajenas, las pasadas y las que ya no van a ocurrir`() {
        val appointments = listOf(
            domainAppointment(id = "mia-manana", professionalId = "mariana", start = later(24)),
            domainAppointment(id = "mia-en-curso", professionalId = "mariana", start = now.minusSeconds(600), minutes = 50),
            domainAppointment(id = "mia-pasada", professionalId = "mariana", start = later(-48)),
            domainAppointment(id = "mia-cancelada", professionalId = "mariana", start = later(30), status = AppointmentStatus.CANCELLED),
            domainAppointment(id = "mia-realizada", professionalId = "mariana", start = later(31), status = AppointmentStatus.COMPLETED),
            domainAppointment(id = "ajena", professionalId = "rodrigo", start = later(25)),
        )

        val result = summary(mariana, appointments)

        assertEquals(2, result.upcomingAppointmentCount)
        assertEquals(listOf("mia-en-curso", "mia-manana"), result.upcomingAppointments.map { it.appointmentId })
    }

    @Test
    fun `una cita reprogramada sigue contando como proxima`() {
        val appointments = listOf(
            domainAppointment(id = "reprogramada", professionalId = "mariana", start = later(48), status = AppointmentStatus.RESCHEDULED),
        )

        assertEquals(listOf("reprogramada"), summary(mariana, appointments).upcomingAppointments.map { it.appointmentId })
    }

    @Test
    fun `el administrador del sistema no recibe citas ni pacientes por su rol tecnico en la agenda`() {
        val systemAdmin = userAccount(id = "sys", role = UserRole.SYSTEM_ADMIN, area = null)
        val appointments = listOf(domainAppointment(id = "cualquiera", start = later(2), professionalId = "mariana"))

        val result = summary(systemAdmin, appointments)

        assertEquals(0, result.upcomingAppointmentCount)
        assertEquals(0, result.todayAppointmentCount)
        assertEquals(emptyList<Any>(), result.upcomingAppointments)
    }

    @Test
    fun `las citas de hoy se cuentan por rol, sin canceladas y en hora de Monterrey`() {
        val appointments = listOf(
            domainAppointment(id = "hoy-1", professionalId = "mariana", start = later(1)), // 11:00
            domainAppointment(id = "hoy-realizada", professionalId = "mariana", start = now.minusSeconds(3_600), status = AppointmentStatus.COMPLETED),
            domainAppointment(id = "hoy-cancelada", professionalId = "mariana", start = later(2), status = AppointmentStatus.CANCELLED),
            domainAppointment(id = "hoy-ajena", professionalId = "rodrigo", start = later(3)),
            domainAppointment(id = "manana", professionalId = "mariana", start = later(24)),
            domainAppointment(id = "ayer", professionalId = "mariana", start = later(-24)),
        )

        assertEquals(2, summary(mariana, appointments).todayAppointmentCount)
        assertEquals(3, summary(coordinator, appointments).todayAppointmentCount)
        assertEquals(3, summary(admin, appointments).todayAppointmentCount)
    }

    @Test
    fun `coordinacion ve las citas de su area y administracion todas`() {
        val appointments = listOf(
            domainAppointment(id = "psico", start = later(1), area = ClinicalArea.PSYCHOLOGY, professionalId = "rodrigo"),
            domainAppointment(id = "nutri", start = later(2), area = ClinicalArea.NUTRITION, professionalId = "paola"),
        )

        assertEquals(listOf("psico"), summary(coordinator, appointments).upcomingAppointments.map { it.appointmentId })
        assertEquals(listOf("psico", "nutri"), summary(admin, appointments).upcomingAppointments.map { it.appointmentId })
    }

    @Test
    fun `solo se listan las tres citas mas cercanas pero se cuentan todas`() {
        val appointments = (1..5).map { domainAppointment(id = "c$it", professionalId = "mariana", start = later(it.toLong())) }

        val result = summary(mariana, appointments)

        assertEquals(5, result.upcomingAppointmentCount)
        assertEquals(listOf("c1", "c2", "c3"), result.upcomingAppointments.map { it.appointmentId })
    }

    @Test
    fun `las solicitudes pendientes se acotan al rol`() {
        val pending = listOf(
            PendingAccessRequest(requesterUserId = "mariana", ownerArea = ClinicalArea.NUTRITION),
            PendingAccessRequest(requesterUserId = "rodrigo", ownerArea = ClinicalArea.PSYCHOLOGY),
            PendingAccessRequest(requesterUserId = "paola", ownerArea = null),
        )

        assertEquals(1, summary(mariana, pending = pending).pendingRequestCount)
        assertEquals(1, summary(coordinator, pending = pending).pendingRequestCount)
        assertEquals(3, summary(admin, pending = pending).pendingRequestCount)
    }

    @Test
    fun `los pacientes recientes son los cinco ultimos modificados, el mas reciente primero`() {
        val many = (1..7).map { record("p$it", professional = null, updated = it.toLong()) }

        val result = HomeSummaryBuilder.build(admin, many, emptyList(), PendingWork(), now)

        assertEquals(listOf("p7", "p6", "p5", "p4", "p3"), result.recentPatients.map { it.patientId })
    }

    @Test
    fun `un paciente recien creado aparece primero entre los recientes`() {
        val created = record("p9", professional = null, updated = 1_000)

        val result = HomeSummaryBuilder.build(mariana, patients + created, emptyList(), PendingWork(), now)

        assertEquals("p9", result.recentPatients.first().patientId)
    }

    // ---------------------------------------------------------------- pendientes por rol (Fase 4)

    private val system = userAccount(id = "system", role = UserRole.SYSTEM_ADMIN, area = null)

    private fun pendingWork() = PendingWork(
        accounts = listOf(
            userAccount(id = "n1", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY, status = mx.crnl.clinica.beta.domain.model.AccountStatus.PENDING_APPROVAL),
            userAccount(id = "n2", role = UserRole.PROFESSIONAL, area = ClinicalArea.NUTRITION, status = mx.crnl.clinica.beta.domain.model.AccountStatus.PENDING_APPROVAL),
            userAccount(id = "n3", role = UserRole.AREA_COORDINATOR, area = ClinicalArea.PSYCHOLOGY, status = mx.crnl.clinica.beta.domain.model.AccountStatus.PENDING_APPROVAL),
        ),
        accessRequests = listOf(
            PendingAccessRequest("mariana", ClinicalArea.NUTRITION),
            PendingAccessRequest("paola", ClinicalArea.PSYCHOLOGY),
        ),
        changeRequests = listOf(
            mx.crnl.clinica.beta.domain.home.PendingChangeRequest("mariana", ClinicalArea.PSYCHOLOGY),
            mx.crnl.clinica.beta.domain.home.PendingChangeRequest("paola", ClinicalArea.NUTRITION),
        ),
    )

    private fun build(user: UserAccount) = HomeSummaryBuilder.build(user, patients, emptyList(), pendingWork(), now)

    @Test
    fun `un profesional cuenta solo lo propio y ninguna cuenta`() {
        val result = build(mariana)

        assertEquals(0, result.pendingAccountCount)
        assertEquals(1, result.pendingAccessCount)
        assertEquals(1, result.pendingChangeCount)
        assertEquals(2, result.pendingRequestCount)
    }

    @Test
    fun `coordinacion cuenta las cuentas de profesionales de su area y las solicitudes de su area`() {
        val result = build(coordinator)

        assertEquals("solo el profesional pendiente de Psicología, no la coordinación ni la de Nutrición", 1, result.pendingAccountCount)
        assertEquals("la solicitud de acceso cuyo area propietaria es Psicología", 1, result.pendingAccessCount)
        assertEquals("el cambio de un profesional de Psicología", 1, result.pendingChangeCount)
        assertEquals(3, result.pendingRequestCount)
    }

    @Test
    fun `administracion clinica cuenta todas las cuentas y solicitudes clinicas`() {
        val result = build(admin)

        assertEquals(3, result.pendingAccountCount)
        assertEquals(2, result.pendingAccessCount)
        assertEquals(2, result.pendingChangeCount)
        assertEquals(7, result.pendingRequestCount)
    }

    @Test
    fun `el administrador del sistema solo cuenta cuentas y ninguna solicitud de pacientes`() {
        val result = build(system)

        assertEquals(3, result.pendingAccountCount)
        assertEquals(0, result.pendingAccessCount)
        assertEquals(0, result.pendingChangeCount)
        assertEquals(3, result.pendingRequestCount)
    }

    @Test
    fun `una cuenta suspendida no cuenta pendientes`() {
        val result = build(admin.copy(status = mx.crnl.clinica.beta.domain.model.AccountStatus.SUSPENDED))

        assertEquals(0, result.pendingRequestCount)
    }
}
