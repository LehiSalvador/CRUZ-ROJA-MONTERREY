package mx.crnl.clinica.beta.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.appointment.AppointmentAction
import mx.crnl.clinica.beta.domain.clinical.EncounterField
import mx.crnl.clinica.beta.domain.clinical.EncounterIssue
import mx.crnl.clinica.beta.domain.common.EntityKind
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.model.AppointmentDraft
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.EncounterDraft
import mx.crnl.clinica.beta.domain.model.EncounterStatus
import mx.crnl.clinica.beta.domain.model.EncounterType
import mx.crnl.clinica.beta.testing.RepositoryTest
import mx.crnl.clinica.beta.testing.SeedIds
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.failure
import mx.crnl.clinica.beta.testing.observeAround
import mx.crnl.clinica.beta.testing.success
import mx.crnl.clinica.beta.testing.testInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalEncounterRepositoryTest : RepositoryTest() {
    // Andrés (sin asignación vigente) está inactivo en el conjunto ficticio: se activa para probar la falta de profesional.
    @Before
    fun activateAndres() = setPatientStatus(SeedIds.ANDRES, "ACTIVE")

    private val now: Instant = TestNow.toInstant()

    private fun draft(
        patientId: String = SeedIds.ANA,
        area: ClinicalArea = ClinicalArea.PSYCHOLOGY,
        professionalId: String = SeedIds.MARIANA,
        appointmentId: String? = null,
        type: EncounterType = EncounterType.FOLLOW_UP,
        eventAt: Instant = testInstant(0, 9),
    ) = EncounterDraft(patientId, area, professionalId, appointmentId, type, eventAt)

    private suspend fun create(draft: EncounterDraft = draft(), actor: String = SeedIds.MARIANA) =
        encounters.createEncounter(draft, actor)

    private fun encounterCount() = scalar("SELECT COUNT(*) FROM clinical_encounters").toInt()

    // ---------------------------------------------------------------- crear

    @Test
    fun `el profesional registra un encuentro base de su paciente y de su area`() = runTest {
        val created = create().success()

        assertEquals("generated-1", created.summary.encounterId)
        assertEquals(ClinicalArea.PSYCHOLOGY, created.summary.area)
        assertEquals(EncounterType.FOLLOW_UP, created.summary.type)
        assertEquals(EncounterStatus.COMPLETED, created.summary.status)
        assertEquals("Mariana Elizondo", created.summary.professionalName)
        assertEquals("Ana Lucía Cavazos Ibarra", created.patientName)
        assertEquals("CRNL-000001", created.patientNumber)
        assertEquals(SeedIds.MARIANA, created.professionalId)
        assertNull(created.summary.appointmentId)
        assertEquals(testInstant(0, 9), created.summary.eventAt)
        assertEquals("Mariana Elizondo Cantú", created.createdByName)
    }

    @Test
    fun `eventAt es cuando ocurrio y recordedAt cuando se capturo, y no se confunden`() = runTest {
        val created = create(draft(eventAt = testInstant(-3, 9))).success()

        assertEquals(testInstant(-3, 9), created.summary.eventAt)
        assertEquals(now, created.recordedAt)
        assertNotEquals(created.summary.eventAt, created.recordedAt)
        assertEquals(now.toEpochMilli(), scalar("SELECT recordedAt FROM clinical_encounters WHERE encounterId = 'generated-1'"))
        assertEquals(now.toEpochMilli(), scalar("SELECT updatedAt FROM clinical_encounters WHERE encounterId = 'generated-1'"))
        assertEquals(SeedIds.MARIANA, text("SELECT createdBy FROM clinical_encounters WHERE encounterId = 'generated-1'"))
        assertEquals(SeedIds.MARIANA, text("SELECT updatedBy FROM clinical_encounters WHERE encounterId = 'generated-1'"))
    }

    @Test
    fun `crear el encuentro deja constancia con identificadores y sin contenido`() = runTest {
        create(draft(type = EncounterType.INTERVENTION)).success()

        val row = auditRows("ENCOUNTER_CREATED").single()
        assertEquals(SeedIds.MARIANA, row.actor)
        assertEquals("ENCOUNTER", row.entityType)
        assertEquals("generated-1", row.entityId)
        assertEquals(SeedIds.ANA, row.patientId)
        assertEquals("PSYCHOLOGY", row.areaCode)
        assertTrue(row.metadata!!.contains("INTERVENTION"))
        assertFalse(row.metadata!!.contains("Ana"))
    }

    @Test
    fun `un encuentro se puede vincular a una cita del mismo paciente, area y profesional sin modificarla`() = runTest {
        // Hoy 10:00 Rodrigo atiende a Fernanda (cita confirmada): se marca realizada y se registra la atención.
        appointments.applyAction(SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA, AppointmentAction.COMPLETE, AppointmentStatus.CONFIRMED, null, SeedIds.RODRIGO).success()
        val statusBefore = text("SELECT status FROM appointments WHERE appointmentId = '${SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA}'")
        val updatedBefore = scalar("SELECT updatedAt FROM appointments WHERE appointmentId = '${SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA}'")

        val created = create(
            draft(SeedIds.FERNANDA, ClinicalArea.PSYCHOLOGY, SeedIds.RODRIGO, SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA, eventAt = testInstant(0, 10)),
            actor = SeedIds.RODRIGO,
        ).success()

        assertEquals(SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA, created.summary.appointmentId)
        assertEquals(testInstant(0, 10), created.appointmentStart)
        assertEquals(statusBefore, text("SELECT status FROM appointments WHERE appointmentId = '${SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA}'"))
        assertEquals(updatedBefore, scalar("SELECT updatedAt FROM appointments WHERE appointmentId = '${SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA}'"))
    }

    @Test
    fun `una cita puede tener mas de un encuentro y un encuentro puede existir sin cita`() = runTest {
        // La cita realizada de Ana ya tiene un encuentro sembrado; otro de tipo distinto es válido.
        create(draft(appointmentId = SeedIds.CITA_ANA_REALIZADA, type = EncounterType.CLOSURE, eventAt = testInstant(-21, 11))).success()
        create(draft(type = EncounterType.OTHER)).success()

        assertEquals(2, scalar("SELECT COUNT(*) FROM clinical_encounters WHERE appointmentId = '${SeedIds.CITA_ANA_REALIZADA}'").toInt())
    }

    @Test
    fun `una cita de otro paciente, otra area u otro profesional no se puede vincular`() = runTest {
        // De otro paciente
        create(draft(appointmentId = SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA)).failure<OperationError.AppointmentMismatch>()
        // De otra área (Ana no tiene cita de Nutrición, se usa una cita de Diego)
        create(draft(appointmentId = SeedIds.CITA_DIEGO_REPROGRAMADA)).failure<OperationError.AppointmentMismatch>()
        // Del mismo paciente y área pero de otro profesional: Fernanda (Psicología) con una cita de Nutrición
        create(draft(SeedIds.FERNANDA, ClinicalArea.PSYCHOLOGY, SeedIds.RODRIGO, SeedIds.CITA_FERNANDA_NUTRICION), actor = SeedIds.RODRIGO)
            .failure<OperationError.AppointmentMismatch>()
        // Una cita inexistente
        create(draft(appointmentId = "no-existe")).failure<OperationError.NotFound>().also { assertEquals(EntityKind.APPOINTMENT, it.entity) }
        assertEquals(6, encounterCount())
    }

    @Test
    fun `una cita cancelada, sin asistencia o pendiente no puede ser el motivo de un encuentro`() = runTest {
        create(draft(SeedIds.REGINA, professionalId = SeedIds.RODRIGO, appointmentId = SeedIds.CITA_REGINA_CANCELADA), actor = SeedIds.RODRIGO)
            .failure<OperationError.AppointmentMismatch>()
        create(draft(appointmentId = SeedIds.CITA_ANA_SIN_ASISTENCIA, eventAt = testInstant(-7, 12))).failure<OperationError.AppointmentMismatch>()
        create(draft(appointmentId = SeedIds.CITA_ANA_PENDIENTE)).failure<OperationError.AppointmentMismatch>()
    }

    // ---------------------------------------------------------------- permisos

    @Test
    fun `un profesional no registra encuentros de pacientes asignados a otro profesional`() = runTest {
        create(draft(SeedIds.DIEGO, professionalId = SeedIds.MARIANA)).failure<OperationError.NotAuthorized>()
        create(draft(SeedIds.DIEGO, professionalId = SeedIds.RODRIGO)).failure<OperationError.NotAuthorized>()
        assertEquals(6, encounterCount())
    }

    @Test
    fun `un profesional no registra encuentros en otra area`() = runTest {
        // Fernanda tiene a Paola en Nutrición; Mariana es de Psicología.
        create(draft(SeedIds.FERNANDA, ClinicalArea.NUTRITION, SeedIds.PAOLA)).failure<OperationError.NotAuthorized>()
    }

    @Test
    fun `sin profesional asignado en el area no se registra el encuentro`() = runTest {
        create(draft(SeedIds.ANDRES)).failure<OperationError.NoActiveAssignment>()
        assertEquals(6, encounterCount())
    }

    @Test
    fun `coordinacion registra encuentros de su area atribuidos al profesional asignado`() = runTest {
        val created = create(draft(SeedIds.DIEGO, professionalId = SeedIds.RODRIGO), actor = SeedIds.CLAUDIA).success()

        assertEquals("Rodrigo Villarreal", created.summary.professionalName)
        assertEquals(SeedIds.CLAUDIA, text("SELECT createdBy FROM clinical_encounters WHERE encounterId = '${created.summary.encounterId}'"))
    }

    @Test
    fun `coordinacion no puede atribuirlo a un profesional distinto del asignado ni registrar en otra area`() = runTest {
        create(draft(SeedIds.DIEGO, professionalId = SeedIds.MARIANA), actor = SeedIds.CLAUDIA).failure<OperationError.NotAuthorized>()
        create(draft(SeedIds.FERNANDA, ClinicalArea.NUTRITION, SeedIds.PAOLA), actor = SeedIds.CLAUDIA).failure<OperationError.NotAuthorized>()
    }

    @Test
    fun `administracion clinica registra en las tres areas`() = runTest {
        create(draft(SeedIds.FERNANDA, ClinicalArea.NUTRITION, SeedIds.PAOLA), actor = SeedIds.HECTOR).success()
        create(draft(SeedIds.JOSE, ClinicalArea.GENERAL_MEDICINE, SeedIds.EDUARDO), actor = SeedIds.HECTOR).success()
        create(draft(), actor = SeedIds.HECTOR).success()
    }

    @Test
    fun `el administrador del sistema y las cuentas no activas no registran encuentros`() = runTest {
        create(actor = insertSystemAdmin().userId).failure<OperationError.NotAuthorized>()
        setUserStatus(SeedIds.MARIANA, "SUSPENDED")
        create().failure<OperationError.NotAuthorized>()
        assertEquals(6, encounterCount())
    }

    @Test
    fun `si el profesional asignado ya no esta activo no se le atribuyen encuentros`() = runTest {
        setUserStatus(SeedIds.RODRIGO, "SUSPENDED")

        create(draft(SeedIds.DIEGO, professionalId = SeedIds.RODRIGO), actor = SeedIds.CLAUDIA).failure<OperationError.ProfessionalNotAvailable>()
    }

    @Test
    fun `un paciente inexistente o no activo no recibe encuentros`() = runTest {
        create(draft(patientId = "no-existe")).failure<OperationError.NotFound>().also { assertEquals(EntityKind.PATIENT, it.entity) }
        setPatientStatus(SeedIds.ANA, "BLOCKED")
        create().failure<OperationError.PatientNotActive>()
    }

    // ---------------------------------------------------------------- validación y doble toque

    @Test
    fun `la fecha del evento no puede estar absurdamente en el futuro`() = runTest {
        val error = create(draft(eventAt = testInstant(3, 9))).failure<OperationError.InvalidEncounter>()

        assertEquals(EncounterIssue.IN_THE_FUTURE, error.issues[EncounterField.DATE])
        assertEquals(6, encounterCount())
        assertTrue(auditRows("ENCOUNTER_CREATED").isEmpty())
    }

    @Test
    fun `un doble toque no crea dos encuentros identicos`() = runTest {
        val first = draft(eventAt = testInstant(0, 9))

        create(first).success()
        create(first).failure<OperationError.DuplicateEncounter>()

        assertEquals(7, encounterCount())
        assertEquals(1, auditRows("ENCOUNTER_CREATED").size)
    }

    @Test
    fun `dos encuentros que difieren en tipo u hora no son duplicados`() = runTest {
        create(draft(eventAt = testInstant(0, 9))).success()
        create(draft(eventAt = testInstant(0, 9), type = EncounterType.INTERVENTION)).success()
        create(draft(eventAt = testInstant(0, 8))).success()

        assertEquals(9, encounterCount())
    }

    // ---------------------------------------------------------------- lecturas

    @Test
    fun `la linea del area ordena por evento, del mas reciente al mas antiguo, y se actualiza sola`() = runTest {
        val seen = encounters.observeAreaEncounters(SeedIds.ANA, ClinicalArea.PSYCHOLOGY, account(SeedIds.MARIANA)).observeAround(
            change = {
                create(draft(eventAt = testInstant(-1, 9))).success()
                create(draft(eventAt = testInstant(-5, 9), type = EncounterType.ASSESSMENT)).success()
            },
            until = { it.size == 3 },
        )

        val final = seen.last()
        assertEquals(1, seen.first().size)
        assertEquals(final.sortedByDescending { it.eventAt }, final)
        assertEquals(EncounterType.FOLLOW_UP, final.first().type)
    }

    @Test
    fun `la linea de un area ajena llega vacia`() = runTest {
        assertTrue(encounters.observeAreaEncounters(SeedIds.FERNANDA, ClinicalArea.NUTRITION, account(SeedIds.MARIANA)).first().isEmpty())
        assertEquals(1, encounters.observeAreaEncounters(SeedIds.FERNANDA, ClinicalArea.NUTRITION, account(SeedIds.PAOLA)).first().size)
        assertEquals(1, encounters.observeAreaEncounters(SeedIds.FERNANDA, ClinicalArea.NUTRITION, account(SeedIds.HECTOR)).first().size)
        assertTrue(encounters.observeAreaEncounters(SeedIds.FERNANDA, ClinicalArea.NUTRITION, insertSystemAdmin()).first().isEmpty())
    }

    @Test
    fun `el detalle del encuentro solo se entrega a quien ve su area`() = runTest {
        val id = "f0000000-0000-4000-8000-000000000001" // Psicología, Ana

        assertEquals(EncounterType.INITIAL, encounters.observeEncounter(id, account(SeedIds.MARIANA)).first()!!.summary.type)
        assertEquals(SeedIds.CITA_ANA_REALIZADA, encounters.observeEncounter(id, account(SeedIds.MARIANA)).first()!!.summary.appointmentId)
        assertNull(encounters.observeEncounter(id, account(SeedIds.PAOLA)).first())
        assertNull(encounters.observeEncounter("no-existe", account(SeedIds.HECTOR)).first())
    }

    @Test
    fun `el contexto del formulario trae al profesional asignado, las citas vinculables y si se puede registrar`() = runTest {
        val asMariana = encounters.getFormContext(SeedIds.ANA, ClinicalArea.PSYCHOLOGY, account(SeedIds.MARIANA))!!

        assertEquals("CRNL-000001", asMariana.patientNumber)
        assertEquals(SeedIds.MARIANA, asMariana.assignment!!.professionalId)
        assertTrue(asMariana.canCreate)
        // Cita realizada (vinculable) y cita pendiente (no), no canceladas ni sin asistencia.
        assertEquals(listOf(SeedIds.CITA_ANA_REALIZADA), asMariana.linkableAppointments.map { it.appointmentId })
    }

    @Test
    fun `el contexto indica que no se puede registrar cuando la politica lo impide`() = runTest {
        assertTrue(encounters.getFormContext(SeedIds.DIEGO, ClinicalArea.PSYCHOLOGY, account(SeedIds.CLAUDIA))!!.canCreate)
        assertFalse(encounters.getFormContext(SeedIds.DIEGO, ClinicalArea.PSYCHOLOGY, account(SeedIds.MARIANA))!!.canCreate)
        assertNull(encounters.getFormContext(SeedIds.ANDRES, ClinicalArea.PSYCHOLOGY, account(SeedIds.CLAUDIA))!!.assignment)
        assertFalse(encounters.getFormContext(SeedIds.ANDRES, ClinicalArea.PSYCHOLOGY, account(SeedIds.CLAUDIA))!!.canCreate)
        assertNull(encounters.getFormContext(SeedIds.FERNANDA, ClinicalArea.NUTRITION, account(SeedIds.MARIANA)))
        assertNull(encounters.getFormContext("no-existe", ClinicalArea.PSYCHOLOGY, account(SeedIds.HECTOR)))
    }

    @Test
    fun `una cita creada y realizada en la misma sesion se vincula y aparece en su detalle`() = runTest {
        val appointment = appointments.createAppointment(
            AppointmentDraft(SeedIds.ANA, ClinicalArea.PSYCHOLOGY, SeedIds.MARIANA, testInstant(0, 16), 50, AppointmentModality.IN_PERSON, "Consultorio 3", null, null),
            SeedIds.CLAUDIA,
        ).success()
        appointments.applyAction(appointment.appointmentId, AppointmentAction.COMPLETE, AppointmentStatus.SCHEDULED, null, SeedIds.MARIANA).success()

        // La cita empieza más tarde hoy: el encuentro ocurrió «ahora», no a la hora de la cita.
        val created = create(draft(appointmentId = appointment.appointmentId, eventAt = now)).success()

        val detail = appointments.getAppointment(appointment.appointmentId, account(SeedIds.MARIANA))!!
        assertEquals(listOf(created.summary.encounterId), detail.encounters.map { it.encounterId })
    }

    @Test
    fun `tras registrar no queda ninguna clave foranea rota y la base esta integra`() = runTest {
        create().success()

        assertEquals(0, foreignKeyViolations())
        assertEquals("ok", text("PRAGMA integrity_check"))
    }
}
