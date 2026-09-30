package mx.crnl.clinica.beta.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.appointment.AppointmentAction
import mx.crnl.clinica.beta.domain.appointment.AppointmentField
import mx.crnl.clinica.beta.domain.appointment.AppointmentIssue
import mx.crnl.clinica.beta.domain.appointment.ConflictKind
import mx.crnl.clinica.beta.domain.common.EntityKind
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.model.AppointmentAdminUpdate
import mx.crnl.clinica.beta.domain.model.AppointmentDraft
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentReschedule
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.testing.RepositoryTest
import mx.crnl.clinica.beta.testing.SeedIds
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.failure
import mx.crnl.clinica.beta.testing.observeAround
import mx.crnl.clinica.beta.testing.success
import mx.crnl.clinica.beta.testing.testInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAppointmentRepositoryTest : RepositoryTest() {
    // Andrés (sin asignación vigente) está inactivo en el conjunto ficticio: se activa para probar la falta de profesional.
    @Before
    fun activateAndres() = setPatientStatus(SeedIds.ANDRES, "ACTIVE")

    private val now: Instant = TestNow.toInstant()

    private fun draft(
        patientId: String = SeedIds.ANA,
        area: ClinicalArea = ClinicalArea.PSYCHOLOGY,
        professionalId: String = SeedIds.MARIANA,
        start: Instant = testInstant(1, 11),
        minutes: Int = 50,
        modality: AppointmentModality = AppointmentModality.IN_PERSON,
        location: String? = "Consultorio 3",
        meetingUrl: String? = null,
        notes: String? = null,
    ) = AppointmentDraft(patientId, area, professionalId, start, minutes, modality, location, meetingUrl, notes)

    private suspend fun create(draft: AppointmentDraft = draft(), actor: String = SeedIds.CLAUDIA) =
        appointments.createAppointment(draft, actor)

    private fun appointmentCount() = scalar("SELECT COUNT(*) FROM appointments").toInt()

    private fun statusOf(id: String) = text("SELECT status FROM appointments WHERE appointmentId = '$id'")

    // ---------------------------------------------------------------- crear

    @Test
    fun `coordinacion crea una cita para el profesional asignado y queda persistida`() = runTest {
        val created = create().success()

        assertEquals("generated-1", created.appointmentId)
        assertEquals(AppointmentStatus.SCHEDULED, created.status)
        assertEquals(testInstant(1, 11), created.summary.start)
        assertEquals(testInstant(1, 11, 50), created.summary.end)
        assertEquals("Consultorio 3", created.summary.location)
        assertEquals("Ana Lucía Cavazos Ibarra", created.summary.patientName)
        assertEquals("Mariana Elizondo", created.summary.professionalName)
        assertEquals("Claudia Benavides Rangel", created.createdByName)
        assertEquals(now, created.createdAt)
        assertEquals(now, created.updatedAt)
        assertEquals(13, appointmentCount())
        assertEquals(SeedIds.CLAUDIA, text("SELECT createdBy FROM appointments WHERE appointmentId = 'generated-1'"))
        assertTrue(created.encounters.isEmpty())
    }

    @Test
    fun `un profesional agenda sus propias citas`() = runTest {
        create(actor = SeedIds.MARIANA).success()

        assertEquals(SeedIds.MARIANA, text("SELECT createdBy FROM appointments WHERE appointmentId = 'generated-1'"))
    }

    @Test
    fun `administracion clinica agenda en cualquier area`() = runTest {
        create(draft(SeedIds.LUIS, ClinicalArea.NUTRITION, SeedIds.PAOLA), actor = SeedIds.HECTOR).success()
    }

    @Test
    fun `crear una cita deja la constancia de auditoria con identificadores y sin datos personales`() = runTest {
        create(draft(notes = "Traer identificación")).success()

        val row = auditRows("APPOINTMENT_CREATED").single()
        assertEquals(SeedIds.CLAUDIA, row.actor)
        assertEquals("APPOINTMENT", row.entityType)
        assertEquals("generated-1", row.entityId)
        assertEquals(SeedIds.ANA, row.patientId)
        assertEquals("PSYCHOLOGY", row.areaCode)
        val metadata = row.metadata!!
        assertTrue(metadata.contains(SeedIds.MARIANA))
        assertTrue(metadata.contains("SCHEDULED"))
        assertFalse(metadata.contains("Traer identificación"))
        assertFalse(metadata.contains("Consultorio"))
        assertFalse(metadata.contains("Ana"))
    }

    @Test
    fun `un profesional no agenda citas de otro profesional`() = runTest {
        create(draft(SeedIds.DIEGO, professionalId = SeedIds.RODRIGO), actor = SeedIds.MARIANA).failure<OperationError.NotAuthorized>()

        assertEquals(12, appointmentCount())
        assertTrue(auditRows("APPOINTMENT_CREATED").isEmpty())
    }

    @Test
    fun `coordinacion no agenda en otra area`() = runTest {
        create(draft(SeedIds.LUIS, ClinicalArea.NUTRITION, SeedIds.PAOLA), actor = SeedIds.CLAUDIA).failure<OperationError.NotAuthorized>()

        assertEquals(12, appointmentCount())
    }

    @Test
    fun `el administrador del sistema no agenda por ser administrador`() = runTest {
        val system = insertSystemAdmin()

        create(actor = system.userId).failure<OperationError.NotAuthorized>()
    }

    @Test
    fun `una cuenta suspendida o inactiva no agenda`() = runTest {
        setUserStatus(SeedIds.CLAUDIA, "SUSPENDED")
        create(actor = SeedIds.CLAUDIA).failure<OperationError.NotAuthorized>()

        setUserStatus(SeedIds.MARIANA, "INACTIVE")
        create(actor = SeedIds.MARIANA).failure<OperationError.NotAuthorized>()
    }

    @Test
    fun `un actor inexistente no agenda`() = runTest {
        create(actor = "fantasma").failure<OperationError.NotAuthorized>()
    }

    @Test
    fun `sin profesional asignado en el area no se crea la cita ni una asignacion en silencio`() = runTest {
        val assignmentsBefore = scalar("SELECT COUNT(*) FROM professional_assignments")

        create(draft(SeedIds.ANDRES, professionalId = SeedIds.MARIANA)).failure<OperationError.NoActiveAssignment>()

        assertEquals(12, appointmentCount())
        assertEquals(assignmentsBefore, scalar("SELECT COUNT(*) FROM professional_assignments"))
    }

    @Test
    fun `la cita debe ser con el profesional que el paciente tiene asignado`() = runTest {
        create(draft(SeedIds.DIEGO, professionalId = SeedIds.MARIANA)).failure<OperationError.ProfessionalNotAssigned>()
    }

    @Test
    fun `un profesional que ya no esta activo no puede recibir citas`() = runTest {
        setUserStatus(SeedIds.MARIANA, "SUSPENDED")

        create().failure<OperationError.ProfessionalNotAvailable>()
        assertEquals(12, appointmentCount())
    }

    @Test
    fun `un paciente inexistente o no activo no recibe citas`() = runTest {
        create(draft(patientId = "no-existe")).failure<OperationError.NotFound>().also { assertEquals(EntityKind.PATIENT, it.entity) }

        setPatientStatus(SeedIds.ANA, "INACTIVE")
        create().failure<OperationError.PatientNotActive>()
    }

    @Test
    fun `una cita invalida se rechaza con cada problema y no deja rastro`() = runTest {
        val error = create(draft(minutes = 0, location = "   ")).failure<OperationError.InvalidAppointment>()

        assertEquals(AppointmentIssue.TOO_SHORT, error.issues[AppointmentField.DURATION])
        assertEquals(AppointmentIssue.REQUIRED, error.issues[AppointmentField.LOCATION])
        assertEquals(12, appointmentCount())
        assertTrue(auditRows("APPOINTMENT_CREATED").isEmpty())
    }

    @Test
    fun `una cita nueva en un pasado evidente se rechaza`() = runTest {
        val error = create(draft(start = testInstant(-1, 11))).failure<OperationError.InvalidAppointment>()

        assertEquals(AppointmentIssue.IN_THE_PAST, error.issues[AppointmentField.DATE])
    }

    @Test
    fun `una cita en linea guarda el enlace y descarta la ubicacion`() = runTest {
        val created = create(
            draft(modality = AppointmentModality.ONLINE, location = "Consultorio 3", meetingUrl = " https://meet.example.org/abc "),
        ).success()

        assertEquals("https://meet.example.org/abc", created.meetingUrl)
        assertNull(created.summary.location)
    }

    // ---------------------------------------------------------------- conflictos

    @Test
    fun `dos citas del mismo profesional que se solapan no se guardan y se explica con cual chocan`() = runTest {
        // Rodrigo tiene hoy 10:00–10:50 con Fernanda.
        val error = create(draft(SeedIds.DIEGO, professionalId = SeedIds.RODRIGO, start = testInstant(0, 10, 30))).failure<OperationError.ScheduleConflicts>()

        val conflict = error.conflicts.single()
        assertEquals(ConflictKind.PROFESSIONAL, conflict.kind)
        assertEquals(SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA, conflict.appointmentId)
        assertEquals("Fernanda Guerra Domínguez", conflict.patientName)
        assertEquals(testInstant(0, 10), conflict.start)
        assertEquals(12, appointmentCount())
    }

    @Test
    fun `el mismo paciente no puede tener dos citas a la vez`() = runTest {
        // Ana ya tiene una cita pendiente en tres días a las 11:00 con Mariana.
        val error = create(draft(start = testInstant(3, 11, 20))).failure<OperationError.ScheduleConflicts>()

        assertTrue(error.conflicts.any { it.kind == ConflictKind.PATIENT && it.appointmentId == SeedIds.CITA_ANA_PENDIENTE })
    }

    @Test
    fun `un conflicto en un area ajena solo informa que el horario esta ocupado`() = runTest {
        // Fernanda tiene hoy 10:00 en Psicología (Rodrigo); Paola (Nutrición) intenta agendarla a las 10:15.
        val error = create(
            draft(SeedIds.FERNANDA, ClinicalArea.NUTRITION, SeedIds.PAOLA, start = testInstant(0, 10, 15), minutes = 30),
            actor = SeedIds.PAOLA,
        ).failure<OperationError.ScheduleConflicts>()

        val conflict = error.conflicts.single()
        assertEquals(ConflictKind.PATIENT, conflict.kind)
        assertNull(conflict.appointmentId)
        assertNull(conflict.patientName)
        assertNull(conflict.professionalName)
        assertEquals(testInstant(0, 10), conflict.start)
    }

    @Test
    fun `citas contiguas no chocan`() = runTest {
        // La cita de Rodrigo termina hoy a las 10:50.
        create(draft(SeedIds.DIEGO, professionalId = SeedIds.RODRIGO, start = testInstant(0, 10, 50))).success()
    }

    @Test
    fun `una cita cancelada libera su horario`() = runTest {
        val first = create().success()
        appointments.applyAction(first.appointmentId, AppointmentAction.CANCEL, AppointmentStatus.SCHEDULED, null, SeedIds.CLAUDIA).success()

        create().success()
    }

    // ---------------------------------------------------------------- editar datos administrativos

    @Test
    fun `corregir los datos administrativos conserva horario y estado y deja constancia`() = runTest {
        val created = create().success()

        val updated = appointments.updateAdministrativeData(
            created.appointmentId,
            AppointmentAdminUpdate(AppointmentModality.ONLINE, null, "https://meet.example.org/nuevo", " Llegar 10 min antes "),
            SeedIds.CLAUDIA,
        ).success()

        assertEquals(AppointmentModality.ONLINE, updated.summary.modality)
        assertEquals("https://meet.example.org/nuevo", updated.meetingUrl)
        assertEquals("Llegar 10 min antes", updated.administrativeNotes)
        assertNull(updated.summary.location)
        assertEquals(created.summary.start, updated.summary.start)
        assertEquals(AppointmentStatus.SCHEDULED, updated.status)
        val row = auditRows("APPOINTMENT_UPDATED").single()
        assertEquals(created.appointmentId, row.entityId)
        assertTrue(row.metadata!!.contains("MODALITY"))
        assertFalse(row.metadata!!.contains("Llegar"))
    }

    @Test
    fun `una cita terminada no se edita`() = runTest {
        val error = appointments.updateAdministrativeData(
            SeedIds.CITA_ANA_REALIZADA,
            AppointmentAdminUpdate(AppointmentModality.IN_PERSON, "Otro", null, null),
            SeedIds.CLAUDIA,
        ).failure<OperationError.NotEditable>()

        assertEquals(AppointmentStatus.COMPLETED, error.status)
    }

    @Test
    fun `editar valida modalidad y ubicacion`() = runTest {
        val created = create().success()

        val error = appointments.updateAdministrativeData(
            created.appointmentId,
            AppointmentAdminUpdate(AppointmentModality.IN_PERSON, "  ", null, null),
            SeedIds.CLAUDIA,
        ).failure<OperationError.InvalidAppointment>()

        assertEquals(AppointmentIssue.REQUIRED, error.issues[AppointmentField.LOCATION])
    }

    @Test
    fun `quien no gestiona la cita no puede editarla`() = runTest {
        appointments.updateAdministrativeData(
            SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA,
            AppointmentAdminUpdate(AppointmentModality.IN_PERSON, "Otro", null, null),
            SeedIds.MARIANA,
        ).failure<OperationError.NotAuthorized>()
    }

    // ---------------------------------------------------------------- reprogramar

    @Test
    fun `reprogramar conserva la cita, cambia el horario y deja el estado reprogramada`() = runTest {
        val appointmentId = SeedIds.CITA_MARIA_HOY_MEDICINA

        val result = appointments.reschedule(
            appointmentId,
            AppointmentReschedule(testInstant(1, 9), 45),
            AppointmentStatus.SCHEDULED,
            SeedIds.HECTOR,
        ).success()

        assertEquals(appointmentId, result.appointmentId)
        assertEquals(AppointmentStatus.RESCHEDULED, result.status)
        assertEquals(testInstant(1, 9), result.summary.start)
        assertEquals(testInstant(1, 9, 45), result.summary.end)
        assertEquals(now, result.updatedAt)
        assertEquals(12, appointmentCount())
        assertEquals("RESCHEDULED", statusOf(appointmentId))
        val row = auditRows("APPOINTMENT_RESCHEDULED").single()
        assertEquals(appointmentId, row.entityId)
        assertTrue(row.metadata!!.contains("SCHEDULED"))
        assertTrue(row.metadata!!.contains(testInstant(1, 9).toString()))
    }

    @Test
    fun `una cita ya reprogramada se puede reprogramar de nuevo`() = runTest {
        val result = appointments.reschedule(
            SeedIds.CITA_DIEGO_REPROGRAMADA,
            AppointmentReschedule(testInstant(8, 12), 50),
            AppointmentStatus.RESCHEDULED,
            SeedIds.CLAUDIA,
        ).success()

        assertEquals(testInstant(8, 12), result.summary.start)
    }

    @Test
    fun `reprogramar a un horario ocupado no cambia nada`() = runTest {
        // Rodrigo ocupa hoy 10:00–10:50; la cita reprogramada de Diego (también de Rodrigo) intenta ir a las 10:30.
        val error = appointments.reschedule(
            SeedIds.CITA_DIEGO_REPROGRAMADA,
            AppointmentReschedule(testInstant(0, 10, 30), 50),
            AppointmentStatus.RESCHEDULED,
            SeedIds.CLAUDIA,
        ).failure<OperationError.ScheduleConflicts>()

        assertEquals(SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA, error.conflicts.single().appointmentId)
        assertEquals("RESCHEDULED", statusOf(SeedIds.CITA_DIEGO_REPROGRAMADA))
        assertEquals(testInstant(7, 16), appointments.getAppointment(SeedIds.CITA_DIEGO_REPROGRAMADA, account(SeedIds.CLAUDIA))!!.summary.start)
        assertTrue(auditRows("APPOINTMENT_RESCHEDULED").isEmpty())
    }

    @Test
    fun `una cita no choca consigo misma al moverse un poco`() = runTest {
        appointments.reschedule(
            SeedIds.CITA_DIEGO_REPROGRAMADA,
            AppointmentReschedule(testInstant(7, 16, 30), 50),
            AppointmentStatus.RESCHEDULED,
            SeedIds.CLAUDIA,
        ).success()
    }

    @Test
    fun `reprogramar al pasado o con duracion invalida se rechaza`() = runTest {
        val past = appointments.reschedule(
            SeedIds.CITA_MARIA_HOY_MEDICINA,
            AppointmentReschedule(testInstant(-2, 9), 30),
            AppointmentStatus.SCHEDULED,
            SeedIds.HECTOR,
        ).failure<OperationError.InvalidAppointment>()
        assertEquals(AppointmentIssue.IN_THE_PAST, past.issues[AppointmentField.DATE])

        val duration = appointments.reschedule(
            SeedIds.CITA_MARIA_HOY_MEDICINA,
            AppointmentReschedule(testInstant(1, 9), 1_000),
            AppointmentStatus.SCHEDULED,
            SeedIds.HECTOR,
        ).failure<OperationError.InvalidAppointment>()
        assertEquals(AppointmentIssue.TOO_LONG, duration.issues[AppointmentField.DURATION])
    }

    @Test
    fun `una cita pendiente o terminada no se reprograma`() = runTest {
        appointments.reschedule(
            SeedIds.CITA_ANA_PENDIENTE,
            AppointmentReschedule(testInstant(4, 11), 50),
            AppointmentStatus.PENDING,
            SeedIds.CLAUDIA,
        ).failure<OperationError.InvalidTransition>()
        appointments.reschedule(
            SeedIds.CITA_ANA_REALIZADA,
            AppointmentReschedule(testInstant(4, 11), 50),
            AppointmentStatus.COMPLETED,
            SeedIds.CLAUDIA,
        ).failure<OperationError.InvalidTransition>()
    }

    @Test
    fun `si la cita cambio de estado mientras se veia no se reprograma`() = runTest {
        val error = appointments.reschedule(
            SeedIds.CITA_MARIA_HOY_MEDICINA,
            AppointmentReschedule(testInstant(1, 9), 30),
            AppointmentStatus.CONFIRMED, // la persona la veía confirmada, pero sigue programada
            SeedIds.HECTOR,
        ).failure<OperationError.StatusChanged>()

        assertEquals(AppointmentStatus.SCHEDULED, error.current)
    }

    @Test
    fun `quien no gestiona la cita no la reprograma`() = runTest {
        appointments.reschedule(
            SeedIds.CITA_MARIA_HOY_MEDICINA,
            AppointmentReschedule(testInstant(1, 9), 30),
            AppointmentStatus.SCHEDULED,
            SeedIds.CLAUDIA, // coordina Psicología, la cita es de Medicina
        ).failure<OperationError.NotAuthorized>()
    }

    // ---------------------------------------------------------------- cambios de estado

    private suspend fun apply(
        appointmentId: String,
        action: AppointmentAction,
        expected: AppointmentStatus,
        reason: String? = null,
        actor: String = SeedIds.HECTOR,
    ) = appointments.applyAction(appointmentId, action, expected, reason, actor)

    @Test
    fun `cada accion de estado cambia el estado y deja su propia constancia`() = runTest {
        val id = SeedIds.CITA_MARIA_HOY_MEDICINA // programada

        apply(id, AppointmentAction.CONFIRM, AppointmentStatus.SCHEDULED).success()
        assertEquals("CONFIRMED", statusOf(id))
        apply(id, AppointmentAction.COMPLETE, AppointmentStatus.CONFIRMED).success()
        assertEquals("COMPLETED", statusOf(id))

        val noShow = SeedIds.CITA_LUIS_MANANA_NUTRICION
        apply(noShow, AppointmentAction.MARK_NO_SHOW, AppointmentStatus.SCHEDULED).success()
        assertEquals("NO_SHOW", statusOf(noShow))

        val pending = SeedIds.CITA_ANA_PENDIENTE
        apply(pending, AppointmentAction.SCHEDULE, AppointmentStatus.PENDING).success()
        assertEquals("SCHEDULED", statusOf(pending))

        apply(SeedIds.CITA_FERNANDA_NUTRICION, AppointmentAction.CANCEL, AppointmentStatus.SCHEDULED).success()
        assertEquals("CANCELLED", statusOf(SeedIds.CITA_FERNANDA_NUTRICION))

        assertEquals(
            listOf(
                "APPOINTMENT_CONFIRMED",
                "APPOINTMENT_COMPLETED",
                "APPOINTMENT_NO_SHOW",
                "APPOINTMENT_SCHEDULED",
                "APPOINTMENT_CANCELLED",
            ),
            auditedActions(),
        )
    }

    @Test
    fun `una accion de estado conserva el horario y actualiza la fecha de modificacion`() = runTest {
        val before = appointments.getAppointment(SeedIds.CITA_MARIA_HOY_MEDICINA, account(SeedIds.HECTOR))!!

        val after = apply(SeedIds.CITA_MARIA_HOY_MEDICINA, AppointmentAction.CONFIRM, AppointmentStatus.SCHEDULED).success()

        assertEquals(before.summary.start, after.summary.start)
        assertEquals(before.summary.end, after.summary.end)
        assertEquals(now, after.updatedAt)
    }

    @Test
    fun `la constancia de un cambio de estado guarda el estado anterior y el nuevo`() = runTest {
        apply(SeedIds.CITA_MARIA_HOY_MEDICINA, AppointmentAction.CONFIRM, AppointmentStatus.SCHEDULED).success()

        val row = auditRows("APPOINTMENT_CONFIRMED").single()
        assertEquals(SeedIds.HECTOR, row.actor)
        assertEquals(SeedIds.CITA_MARIA_HOY_MEDICINA, row.entityId)
        assertEquals(SeedIds.MARIA, row.patientId)
        assertEquals("GENERAL_MEDICINE", row.areaCode)
        assertTrue(row.metadata!!.contains("\"from\":\"SCHEDULED\""))
        assertTrue(row.metadata!!.contains("\"to\":\"CONFIRMED\""))
    }

    @Test
    fun `las citas terminales no admiten ninguna accion y quedan intactas`() = runTest {
        for ((id, status) in listOf(
            SeedIds.CITA_ANA_REALIZADA to AppointmentStatus.COMPLETED,
            SeedIds.CITA_ANA_SIN_ASISTENCIA to AppointmentStatus.NO_SHOW,
            SeedIds.CITA_REGINA_CANCELADA to AppointmentStatus.CANCELLED,
        )) {
            for (action in AppointmentAction.entries) {
                val error = apply(id, action, status).failure<OperationError.InvalidTransition>()
                assertEquals(status, error.from)
            }
            assertEquals(status.name, statusOf(id))
        }
        assertTrue(auditedActions().isEmpty())
    }

    @Test
    fun `no se aceptan transiciones que la maquina de estados no permite`() = runTest {
        apply(SeedIds.CITA_ANA_PENDIENTE, AppointmentAction.COMPLETE, AppointmentStatus.PENDING).failure<OperationError.InvalidTransition>()
        apply(SeedIds.CITA_ANA_PENDIENTE, AppointmentAction.MARK_NO_SHOW, AppointmentStatus.PENDING).failure<OperationError.InvalidTransition>()
        apply(SeedIds.CITA_MARIA_HOY_MEDICINA, AppointmentAction.SCHEDULE, AppointmentStatus.SCHEDULED).failure<OperationError.InvalidTransition>()
        assertEquals("PENDING", statusOf(SeedIds.CITA_ANA_PENDIENTE))
    }

    @Test
    fun `reprogramar no se hace por la ruta de acciones de estado`() = runTest {
        apply(SeedIds.CITA_MARIA_HOY_MEDICINA, AppointmentAction.RESCHEDULE, AppointmentStatus.SCHEDULED).failure<OperationError.InvalidTransition>()
        assertEquals("SCHEDULED", statusOf(SeedIds.CITA_MARIA_HOY_MEDICINA))
    }

    @Test
    fun `si otra persona ya cambio el estado la accion no se aplica`() = runTest {
        apply(SeedIds.CITA_MARIA_HOY_MEDICINA, AppointmentAction.CONFIRM, AppointmentStatus.SCHEDULED).success()

        val error = apply(SeedIds.CITA_MARIA_HOY_MEDICINA, AppointmentAction.CANCEL, AppointmentStatus.SCHEDULED).failure<OperationError.StatusChanged>()

        assertEquals(AppointmentStatus.CONFIRMED, error.current)
        assertEquals("CONFIRMED", statusOf(SeedIds.CITA_MARIA_HOY_MEDICINA))
    }

    @Test
    fun `cancelar conserva la cita, guarda la razon administrativa y no la copia a la bitacora`() = runTest {
        val cancelled = apply(
            SeedIds.CITA_MARIA_HOY_MEDICINA,
            AppointmentAction.CANCEL,
            AppointmentStatus.SCHEDULED,
            reason = "  El paciente avisó que no puede asistir  ",
        ).success()

        assertEquals(AppointmentStatus.CANCELLED, cancelled.status)
        assertEquals(12, appointmentCount())
        assertTrue(cancelled.administrativeNotes!!.contains("El paciente avisó que no puede asistir"))
        val row = auditRows("APPOINTMENT_CANCELLED").single()
        assertFalse(row.metadata!!.contains("avisó"))
        assertTrue(row.metadata!!.contains("\"reasonGiven\":true"))
    }

    @Test
    fun `la razon solo aplica a cancelar`() = runTest {
        val confirmed = apply(SeedIds.CITA_MARIA_HOY_MEDICINA, AppointmentAction.CONFIRM, AppointmentStatus.SCHEDULED, reason = "texto").success()

        assertNull(confirmed.administrativeNotes)
    }

    @Test
    fun `quien no gestiona la cita no puede cambiar su estado`() = runTest {
        apply(SeedIds.CITA_MARIA_HOY_MEDICINA, AppointmentAction.CANCEL, AppointmentStatus.SCHEDULED, actor = SeedIds.CLAUDIA).failure<OperationError.NotAuthorized>()
        apply(SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA, AppointmentAction.COMPLETE, AppointmentStatus.CONFIRMED, actor = SeedIds.MARIANA).failure<OperationError.NotAuthorized>()
        apply(SeedIds.CITA_MARIA_HOY_MEDICINA, AppointmentAction.CANCEL, AppointmentStatus.SCHEDULED, actor = insertSystemAdmin().userId).failure<OperationError.NotAuthorized>()
        assertEquals("SCHEDULED", statusOf(SeedIds.CITA_MARIA_HOY_MEDICINA))
    }

    @Test
    fun `el profesional titular cambia el estado de su propia cita`() = runTest {
        apply(SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA, AppointmentAction.COMPLETE, AppointmentStatus.CONFIRMED, actor = SeedIds.RODRIGO).success()
    }

    @Test
    fun `una cita inexistente no se puede modificar`() = runTest {
        apply("no-existe", AppointmentAction.CONFIRM, AppointmentStatus.SCHEDULED).failure<OperationError.NotFound>()
    }

    // ---------------------------------------------------------------- lecturas por rol

    @Test
    fun `la agenda de un profesional trae solo sus citas`() = runTest {
        val ids = appointments.observeAppointments(account(SeedIds.RODRIGO)).first().map { it.appointmentId }

        assertTrue(ids.isNotEmpty())
        assertTrue(appointments.observeAppointments(account(SeedIds.RODRIGO)).first().all { it.professionalId == SeedIds.RODRIGO })
    }

    @Test
    fun `la agenda de coordinacion trae las citas de su area y la de administracion todas`() = runTest {
        val coordinator = appointments.observeAppointments(account(SeedIds.CLAUDIA)).first()
        val admin = appointments.observeAppointments(account(SeedIds.HECTOR)).first()

        assertTrue(coordinator.all { it.area == ClinicalArea.PSYCHOLOGY })
        assertEquals(12, admin.size)
        assertEquals(admin.sortedBy { it.start }, admin)
    }

    @Test
    fun `el administrador del sistema no ve la agenda`() = runTest {
        assertTrue(appointments.observeAppointments(insertSystemAdmin()).first().isEmpty())
    }

    @Test
    fun `las citas de un paciente se limitan a las areas que se pueden ver`() = runTest {
        val asMariana = appointments.observePatientAppointments(SeedIds.FERNANDA, account(SeedIds.MARIANA)).first()
        val asAdmin = appointments.observePatientAppointments(SeedIds.FERNANDA, account(SeedIds.HECTOR)).first()

        assertTrue(asMariana.all { it.area == ClinicalArea.PSYCHOLOGY })
        assertTrue(asAdmin.any { it.area == ClinicalArea.NUTRITION })
        assertTrue(asAdmin.size > asMariana.size)
    }

    @Test
    fun `la proxima cita del paciente es la mas cercana abierta y visible`() = runTest {
        val next = appointments.getNextAppointment(SeedIds.FERNANDA, account(SeedIds.HECTOR))!!
        assertEquals(SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA, next.appointmentId)

        // Paola no ve Psicología: para ella la próxima es la de Nutrición.
        val forNutritionist = appointments.getNextAppointment(SeedIds.FERNANDA, account(SeedIds.PAOLA))!!
        assertEquals(SeedIds.CITA_FERNANDA_NUTRICION, forNutritionist.appointmentId)

        assertNull(appointments.getNextAppointment(SeedIds.ANDRES, account(SeedIds.HECTOR)))
    }

    @Test
    fun `el detalle de una cita trae los datos completos para quien puede verla`() = runTest {
        val detail = appointments.getAppointment(SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA, account(SeedIds.CLAUDIA))!!

        assertEquals("Fernanda", detail.patientFirstName)
        assertEquals("+528100000103", detail.contactPhone)
        assertEquals("Consultorio 2", detail.summary.location)
        assertEquals("Rodrigo Villarreal Saldaña", detail.createdByName)
        assertNotNull(detail.createdAt)
    }

    @Test
    fun `el detalle de una cita de otra area no se entrega`() = runTest {
        assertNull(appointments.getAppointment(SeedIds.CITA_MARIA_HOY_MEDICINA, account(SeedIds.CLAUDIA)))
        assertNull(appointments.getAppointment(SeedIds.CITA_MARIA_HOY_MEDICINA, insertSystemAdmin()))
        assertNull(appointments.getAppointment("no-existe", account(SeedIds.HECTOR)))
    }

    @Test
    fun `el detalle de una cita realizada trae su encuentro vinculado`() = runTest {
        val detail = appointments.getAppointment(SeedIds.CITA_ANA_REALIZADA, account(SeedIds.MARIANA))!!

        assertEquals(1, detail.encounters.size)
        assertEquals("f0000000-0000-4000-8000-000000000001", detail.encounters.single().encounterId)
    }

    @Test
    fun `una cita creada aparece en la agenda sin recargar`() = runTest {
        val seen = appointments.observeAppointments(account(SeedIds.CLAUDIA)).observeAround(
            change = { create().success() },
            until = { list -> list.any { it.appointmentId == "generated-1" } },
        )

        assertFalse(seen.first().any { it.appointmentId == "generated-1" })
        assertTrue(seen.last().any { it.appointmentId == "generated-1" })
    }

    @Test
    fun `reprogramar se refleja en el detalle observado`() = runTest {
        val seen = appointments.observeAppointment(SeedIds.CITA_MARIA_HOY_MEDICINA, account(SeedIds.HECTOR)).observeAround(
            change = {
                appointments.reschedule(
                    SeedIds.CITA_MARIA_HOY_MEDICINA,
                    AppointmentReschedule(testInstant(1, 9), 30),
                    AppointmentStatus.SCHEDULED,
                    SeedIds.HECTOR,
                ).success()
            },
            until = { it?.status == AppointmentStatus.RESCHEDULED },
        )

        assertEquals(AppointmentStatus.SCHEDULED, seen.first()!!.status)
        assertEquals(AppointmentStatus.RESCHEDULED, seen.last()!!.status)
    }

    // ---------------------------------------------------------------- WhatsApp

    @Test
    fun `abrir WhatsApp deja constancia sin telefono ni texto`() = runTest {
        appointments.recordWhatsAppOpened(SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA, SeedIds.CLAUDIA).success()

        val row = auditRows("APPOINTMENT_WHATSAPP_OPENED").single()
        assertEquals(SeedIds.CLAUDIA, row.actor)
        assertEquals(SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA, row.entityId)
        assertEquals(SeedIds.FERNANDA, row.patientId)
        assertNull(row.metadata)
    }

    @Test
    fun `quien no gestiona la cita no puede dejar constancia de contacto`() = runTest {
        appointments.recordWhatsAppOpened(SeedIds.CITA_MARIA_HOY_MEDICINA, SeedIds.CLAUDIA).failure<OperationError.NotAuthorized>()
        appointments.recordWhatsAppOpened("no-existe", SeedIds.CLAUDIA).failure<OperationError.NotFound>()

        assertTrue(auditRows("APPOINTMENT_WHATSAPP_OPENED").isEmpty())
    }

    // ---------------------------------------------------------------- integridad y bitacora

    @Test
    fun `la bitacora de citas nunca contiene contrasenas, telefonos, nombres ni el texto de notas`() = runTest {
        val created = create(draft(notes = "Nota administrativa privada")).success()
        appointments.updateAdministrativeData(created.appointmentId, AppointmentAdminUpdate(AppointmentModality.IN_PERSON, "Sala Roja", null, "Otra nota privada"), SeedIds.CLAUDIA).success()
        appointments.reschedule(created.appointmentId, AppointmentReschedule(testInstant(2, 9), 50), AppointmentStatus.SCHEDULED, SeedIds.CLAUDIA).success()
        apply(created.appointmentId, AppointmentAction.CANCEL, AppointmentStatus.RESCHEDULED, reason = "Razón privada", actor = SeedIds.CLAUDIA).success()
        appointments.recordWhatsAppOpened(SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA, SeedIds.CLAUDIA).success()

        val everything = db.query("SELECT COALESCE(metadata, '') FROM audit_entries WHERE action LIKE 'APPOINTMENT_%'", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }.joinToString(" ")
        for (secret in listOf("privada", "Privada", "Razón", "Sala Roja", "Ana", "Fernanda", "5281", "8100000", "@example", "Beta-Local", "password", "token")) {
            assertFalse("no debe aparecer «$secret»", everything.contains(secret))
        }
    }

    @Test
    fun `tras todas las operaciones no queda ninguna clave foranea rota`() = runTest {
        val created = create().success()
        apply(created.appointmentId, AppointmentAction.CONFIRM, AppointmentStatus.SCHEDULED, actor = SeedIds.CLAUDIA).success()
        appointments.reschedule(created.appointmentId, AppointmentReschedule(testInstant(2, 9), 50), AppointmentStatus.CONFIRMED, SeedIds.CLAUDIA).success()

        assertEquals(0, foreignKeyViolations())
        assertEquals("ok", text("PRAGMA integrity_check"))
    }
}
