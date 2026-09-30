package mx.crnl.clinica.beta.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.common.EntityKind
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.model.AssignmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeDraft
import mx.crnl.clinica.beta.domain.model.RequestStatus
import mx.crnl.clinica.beta.testing.RepositoryTest
import mx.crnl.clinica.beta.testing.SeedIds
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.failure
import mx.crnl.clinica.beta.testing.observeAround
import mx.crnl.clinica.beta.testing.success
import mx.crnl.clinica.beta.testing.userEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Fernanda: Psicología con Rodrigo (vigente) tras Mariana (cerrada) y Nutrición con Paola. */
@RunWith(AndroidJUnit4::class)
class LocalProfessionalChangeRepositoryTest : RepositoryTest() {
    private val reason = "Cambio por carga de agenda del profesional"
    private val nutritionCoordinator = "a0000000-0000-4000-8000-0000000000c1"
    private val psychology = ClinicalArea.PSYCHOLOGY

    private fun draft(
        patient: String = SeedIds.FERNANDA,
        area: ClinicalArea = psychology,
        requested: String = SeedIds.MARIANA,
        reason: String = this.reason,
    ) = ProfessionalChangeDraft(patient, area, requested, reason)

    private suspend fun create(draft: ProfessionalChangeDraft = draft(), actor: String = SeedIds.RODRIGO) =
        changeRequests.create(draft, actor)

    private fun assignmentRows(patient: String = SeedIds.FERNANDA, area: String = "PSYCHOLOGY") =
        scalar("SELECT COUNT(*) FROM professional_assignments WHERE patientId = '$patient' AND areaCode = '$area'").toInt()

    private fun activeProfessional(patient: String = SeedIds.FERNANDA, area: String = "PSYCHOLOGY") =
        text("SELECT professionalId FROM professional_assignments WHERE patientId = '$patient' AND areaCode = '$area' AND status = 'ACTIVE'")

    // ---------------------------------------------------------------- pedir

    @Test
    fun `el profesional asignado pide el cambio y queda pendiente sin tocar la asignacion`() = runTest {
        val created = create().success()

        assertEquals(RequestStatus.PENDING, created.status)
        assertEquals(SeedIds.RODRIGO, created.currentProfessionalId)
        assertEquals(SeedIds.MARIANA, created.requestedProfessionalId)
        assertEquals("d0000000-0000-4000-8000-000000000004", created.currentAssignmentId)
        assertEquals(SeedIds.RODRIGO, created.requesterId)
        assertEquals(SeedIds.RODRIGO, activeProfessional())
        assertEquals(2, assignmentRows())
        assertEquals(1, auditRows("OVERRIDE_REQUEST_CREATED").size)
    }

    @Test
    fun `quien puede pedir el cambio`() = runTest {
        // Otro profesional del área, un profesional de otra área y el administrador del sistema no.
        create(actor = SeedIds.MARIANA).failure<OperationError.NotAuthorized>()
        create(actor = SeedIds.PAOLA).failure<OperationError.NotAuthorized>()
        create(actor = insertSystemAdmin().userId).failure<OperationError.NotAuthorized>()
        assertEquals(0, scalar("SELECT COUNT(*) FROM professional_override_requests"))

        // Coordinación del área y administración clínica sí.
        create(actor = SeedIds.CLAUDIA).success()
    }

    @Test
    fun `administracion clinica pide un cambio en cualquier area`() = runTest {
        db.userDao().insert(userEntity(id = "otra-nutri", role = "PROFESSIONAL", area = "NUTRITION", firstName = "Otra", paternalSurname = "Nutrióloga"))

        val created = create(draft(area = ClinicalArea.NUTRITION, requested = "otra-nutri"), actor = SeedIds.HECTOR).success()

        assertEquals(ClinicalArea.NUTRITION, created.area)
        assertEquals(SeedIds.PAOLA, created.currentProfessionalId)
    }

    @Test
    fun `el sustituto debe estar activo, ser profesional del area y distinto del vigente`() = runTest {
        create(draft(requested = SeedIds.RODRIGO)).failure<OperationError.SameProfessional>()
        create(draft(requested = SeedIds.PAOLA)).failure<OperationError.ProfessionalNotAvailable>() // otra área
        create(draft(requested = SeedIds.SOFIA)).failure<OperationError.ProfessionalNotAvailable>() // inactiva
        create(draft(requested = SeedIds.IVAN)).failure<OperationError.ProfessionalNotAvailable>() // rechazado
        create(draft(requested = SeedIds.CLAUDIA)).failure<OperationError.ProfessionalNotAvailable>() // coordinación, no profesional
        assertEquals(EntityKind.PROFESSIONAL, create(draft(requested = "no-existe")).failure<OperationError.NotFound>().entity)
        assertEquals(0, scalar("SELECT COUNT(*) FROM professional_override_requests"))
    }

    @Test
    fun `se valida el motivo, la asignacion vigente y el paciente`() = runTest {
        create(draft(reason = "corto")).failure<OperationError.InvalidReason>()
        // Luis no tiene profesional de Psicología asignado.
        create(draft(patient = SeedIds.LUIS), actor = SeedIds.CLAUDIA).failure<OperationError.NoActiveAssignment>()
        // Andrés está inactivo.
        create(draft(patient = SeedIds.ANDRES), actor = SeedIds.CLAUDIA).failure<OperationError.PatientNotActive>()
        assertEquals(EntityKind.PATIENT, create(draft(patient = "no-existe")).failure<OperationError.NotFound>().entity)
    }

    @Test
    fun `no puede haber dos solicitudes pendientes para el mismo paciente y area`() = runTest {
        create().success()

        create(actor = SeedIds.CLAUDIA).failure<OperationError.DuplicatePendingRequest>()

        assertEquals(1, scalar("SELECT COUNT(*) FROM professional_override_requests"))
    }

    // ---------------------------------------------------------------- aprobar

    @Test
    fun `la coordinacion del area aprueba, cierra la asignacion anterior y abre otra sin borrar historia`() = runTest {
        val created = create().success()
        val oldStart = text("SELECT startAt FROM professional_assignments WHERE assignmentId = '${created.currentAssignmentId}'")

        val approved = changeRequests.approve(created.requestId, SeedIds.CLAUDIA).success()

        assertEquals(RequestStatus.APPROVED, approved.status)
        assertEquals("Claudia Benavides Rangel", approved.reviewerName)
        assertEquals(3, assignmentRows())
        assertEquals(SeedIds.MARIANA, activeProfessional())
        // La asignación anterior conserva a su profesional y su inicio; solo se cierra.
        assertEquals("ENDED", text("SELECT status FROM professional_assignments WHERE assignmentId = '${created.currentAssignmentId}'"))
        assertEquals(SeedIds.RODRIGO, text("SELECT professionalId FROM professional_assignments WHERE assignmentId = '${created.currentAssignmentId}'"))
        assertEquals(oldStart, text("SELECT startAt FROM professional_assignments WHERE assignmentId = '${created.currentAssignmentId}'"))
        assertEquals(TestNow.toInstant().toEpochMilli().toString(), text("SELECT endAt FROM professional_assignments WHERE assignmentId = '${created.currentAssignmentId}'"))
        // La nueva empieza cuando se aprueba, a nombre de quien aprobó.
        val newRow = "SELECT %s FROM professional_assignments WHERE patientId = '${SeedIds.FERNANDA}' AND areaCode = 'PSYCHOLOGY' AND status = 'ACTIVE'"
        assertEquals(TestNow.toInstant().toEpochMilli().toString(), text(newRow.format("startAt")))
        assertEquals(SeedIds.CLAUDIA, text(newRow.format("assignedBy")))
        assertTrue(text(newRow.format("reason")).orEmpty().startsWith("Cambio de profesional"))
        assertNull(text(newRow.format("endAt")))
    }

    @Test
    fun `aprobar deja una sola entrada de bitacora con los identificadores y sin el motivo`() = runTest {
        val created = create().success()

        changeRequests.approve(created.requestId, SeedIds.HECTOR).success()

        val entry = auditRows("OVERRIDE_REQUEST_APPROVED").single()
        assertEquals(SeedIds.HECTOR, entry.actor)
        assertEquals(created.requestId, entry.entityId)
        assertEquals("PSYCHOLOGY", entry.areaCode)
        assertTrue(entry.metadata.orEmpty().contains(SeedIds.MARIANA))
        assertFalse(entry.metadata.orEmpty().contains("agenda"))
    }

    @Test
    fun `el historial clinico y las citas pasadas conservan a su profesional`() = runTest {
        val created = create().success()
        val encounterBefore = text("SELECT professionalId FROM clinical_encounters WHERE encounterId = 'f0000000-0000-4000-8000-000000000005'")
        val doneBefore = text("SELECT professionalId FROM appointments WHERE appointmentId = 'e0000000-0000-4000-8000-000000000002'")

        changeRequests.approve(created.requestId, SeedIds.CLAUDIA).success()

        assertEquals(SeedIds.RODRIGO, encounterBefore)
        assertEquals(encounterBefore, text("SELECT professionalId FROM clinical_encounters WHERE encounterId = 'f0000000-0000-4000-8000-000000000005'"))
        assertEquals(doneBefore, text("SELECT professionalId FROM appointments WHERE appointmentId = 'e0000000-0000-4000-8000-000000000002'"))
        assertEquals(6, scalar("SELECT COUNT(*) FROM clinical_encounters"))
    }

    @Test
    fun `las citas abiertas del profesional anterior no se reasignan solas y se avisan antes`() = runTest {
        val context = changeRequests.getFormContext(SeedIds.FERNANDA, psychology, account(SeedIds.RODRIGO))!!
        assertEquals("la cita de hoy de Fernanda con Rodrigo", 1, context.upcomingAppointmentCount)
        val created = create().success()
        assertEquals(1, created.upcomingAppointmentCount)

        changeRequests.approve(created.requestId, SeedIds.CLAUDIA).success()

        assertEquals(SeedIds.RODRIGO, text("SELECT professionalId FROM appointments WHERE appointmentId = '${SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA}'"))
        assertEquals("CONFIRMED", text("SELECT status FROM appointments WHERE appointmentId = '${SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA}'"))
    }

    @Test
    fun `tras aprobar, el nuevo profesional es el titular de la atencion y el anterior ya no`() = runTest {
        val created = create().success()
        changeRequests.approve(created.requestId, SeedIds.CLAUDIA).success()

        assertTrue(encounters.getFormContext(SeedIds.FERNANDA, psychology, account(SeedIds.MARIANA))!!.canCreate)
        assertFalse(encounters.getFormContext(SeedIds.FERNANDA, psychology, account(SeedIds.RODRIGO))!!.canCreate)
        assertEquals("Mariana Elizondo", assignments.getActiveAssignment(SeedIds.FERNANDA, psychology, account(SeedIds.CLAUDIA))?.professionalName)
    }

    @Test
    fun `quien puede aprobar y quien no`() = runTest {
        db.userDao().insert(userEntity(id = nutritionCoordinator, role = "AREA_COORDINATOR", area = "NUTRITION", firstName = "Renata", paternalSurname = "Coordinadora"))
        val created = create().success()

        changeRequests.approve(created.requestId, SeedIds.RODRIGO).failure<OperationError.NotAuthorized>() // no aprueba su propio cambio
        changeRequests.approve(created.requestId, SeedIds.MARIANA).failure<OperationError.NotAuthorized>() // otro profesional
        changeRequests.approve(created.requestId, nutritionCoordinator).failure<OperationError.NotAuthorized>() // coordinación de otra área
        changeRequests.approve(created.requestId, insertSystemAdmin().userId).failure<OperationError.NotAuthorized>()
        assertEquals(SeedIds.RODRIGO, activeProfessional())
        assertEquals(2, assignmentRows())
        assertTrue(auditRows("OVERRIDE_REQUEST_APPROVED").isEmpty())

        changeRequests.approve(created.requestId, SeedIds.HECTOR).success()
        assertEquals(SeedIds.MARIANA, activeProfessional())
    }

    @Test
    fun `coordinacion no aprueba su propia solicitud pero administracion clinica si`() = runTest {
        val created = create(actor = SeedIds.CLAUDIA).success()

        changeRequests.approve(created.requestId, SeedIds.CLAUDIA).failure<OperationError.NotAuthorized>()
        assertEquals(2, assignmentRows())

        changeRequests.approve(created.requestId, SeedIds.HECTOR).success()
        assertEquals(3, assignmentRows())
    }

    @Test
    fun `aprobar dos veces no crea dos asignaciones`() = runTest {
        val created = create().success()
        changeRequests.approve(created.requestId, SeedIds.CLAUDIA).success()

        val error = changeRequests.approve(created.requestId, SeedIds.HECTOR).failure<OperationError.RequestAlreadyResolved>()

        assertEquals(RequestStatus.APPROVED, error.current)
        assertEquals(3, assignmentRows())
        assertEquals(1, auditRows("OVERRIDE_REQUEST_APPROVED").size)
    }

    @Test
    fun `si la asignacion vigente cambio despues de la solicitud, aprobar se rechaza y no toca nada`() = runTest {
        val created = create().success()
        // Otra vía cerró la vigente y abrió otra a nombre de un tercer profesional.
        val sql = db.openHelper.writableDatabase
        sql.execSQL("UPDATE professional_assignments SET status = 'ENDED', endAt = 5 WHERE assignmentId = '${created.currentAssignmentId}'")
        sql.execSQL(
            "INSERT INTO professional_assignments VALUES ('otra-vigente', '${SeedIds.FERNANDA}', 'PSYCHOLOGY', '${SeedIds.RODRIGO}', 6, NULL, 'ACTIVE', 'x', '${SeedIds.CLAUDIA}', 6)",
        )

        changeRequests.approve(created.requestId, SeedIds.CLAUDIA).failure<OperationError.AssignmentChanged>()

        assertEquals("PENDING", text("SELECT status FROM professional_override_requests WHERE overrideRequestId = '${created.requestId}'"))
        assertEquals("otra-vigente", text("SELECT assignmentId FROM professional_assignments WHERE patientId = '${SeedIds.FERNANDA}' AND areaCode = 'PSYCHOLOGY' AND status = 'ACTIVE'"))
        assertEquals(3, assignmentRows())
    }

    @Test
    fun `si el profesional propuesto dejo de estar disponible, aprobar se rechaza`() = runTest {
        val created = create().success()
        setUserStatus(SeedIds.MARIANA, "SUSPENDED")

        changeRequests.approve(created.requestId, SeedIds.CLAUDIA).failure<OperationError.ProfessionalNotAvailable>()

        assertEquals(SeedIds.RODRIGO, activeProfessional())
        assertEquals("PENDING", text("SELECT status FROM professional_override_requests WHERE overrideRequestId = '${created.requestId}'"))
    }

    @Test
    fun `una solicitud inexistente se rechaza`() = runTest {
        assertEquals(EntityKind.CHANGE_REQUEST, changeRequests.approve("no-existe", SeedIds.HECTOR).failure<OperationError.NotFound>().entity)
        assertEquals(EntityKind.CHANGE_REQUEST, changeRequests.reject("no-existe", null, SeedIds.HECTOR).failure<OperationError.NotFound>().entity)
    }

    // ---------------------------------------------------------------- rechazar

    @Test
    fun `rechazar guarda la razon, no cambia la asignacion y no se aprueba despues`() = runTest {
        val created = create().success()

        val rejected = changeRequests.reject(created.requestId, "  No hay disponibilidad  ", SeedIds.CLAUDIA).success()

        assertEquals(RequestStatus.REJECTED, rejected.status)
        assertEquals("No hay disponibilidad", rejected.resolutionReason)
        assertEquals(SeedIds.RODRIGO, activeProfessional())
        assertEquals(2, assignmentRows())
        assertEquals(1, auditRows("OVERRIDE_REQUEST_REJECTED").size)
        changeRequests.approve(created.requestId, SeedIds.HECTOR).failure<OperationError.RequestAlreadyResolved>()
    }

    @Test
    fun `quien la pidio no la rechaza ni la aprueba, y un profesional tampoco`() = runTest {
        val created = create().success()

        changeRequests.reject(created.requestId, null, SeedIds.RODRIGO).failure<OperationError.NotAuthorized>()
        changeRequests.reject(created.requestId, null, SeedIds.MARIANA).failure<OperationError.NotAuthorized>()
        assertEquals("PENDING", text("SELECT status FROM professional_override_requests WHERE overrideRequestId = '${created.requestId}'"))
    }

    // ---------------------------------------------------------------- lecturas

    @Test
    fun `la bandeja muestra lo propio y lo que se revisa, y el sistema no ve ninguna`() = runTest {
        create().success()

        assertEquals(1, changeRequests.observeRequests(account(SeedIds.RODRIGO)).first().size)
        assertEquals(1, changeRequests.observeRequests(account(SeedIds.CLAUDIA)).first().size)
        assertEquals(1, changeRequests.observeRequests(account(SeedIds.HECTOR)).first().size)
        assertTrue(changeRequests.observeRequests(account(SeedIds.MARIANA)).first().isEmpty())
        assertTrue(changeRequests.observeRequests(account(SeedIds.PAOLA)).first().isEmpty())
        assertTrue(changeRequests.observeRequests(insertSystemAdmin()).first().isEmpty())
    }

    @Test
    fun `el detalle se actualiza al resolver`() = runTest {
        val created = create().success()

        val seen = changeRequests.observeRequest(created.requestId, account(SeedIds.CLAUDIA)).observeAround(
            change = { changeRequests.approve(created.requestId, SeedIds.CLAUDIA).success() },
            until = { it?.status == RequestStatus.APPROVED },
        )

        assertEquals(RequestStatus.PENDING, seen.first()?.status)
        assertEquals(1, seen.first()?.upcomingAppointmentCount)
        assertEquals(RequestStatus.APPROVED, seen.last()?.status)
        assertEquals(0, seen.last()?.upcomingAppointmentCount)
        assertNull(changeRequests.observeRequest(created.requestId, account(SeedIds.PAOLA)).first())
    }

    @Test
    fun `el contexto del formulario lista solo a los sustitutos validos y avisa de un cambio pendiente`() = runTest {
        val rodrigo = account(SeedIds.RODRIGO)

        val context = changeRequests.getFormContext(SeedIds.FERNANDA, psychology, rodrigo)!!

        assertEquals(listOf(SeedIds.MARIANA), context.candidates.map { it.userId })
        assertNull(context.pendingRequestId)
        val created = create().success()
        assertEquals(created.requestId, changeRequests.getFormContext(SeedIds.FERNANDA, psychology, rodrigo)!!.pendingRequestId)
        assertNull(changeRequests.getFormContext(SeedIds.FERNANDA, psychology, account(SeedIds.MARIANA)))
        assertNull(changeRequests.getFormContext(SeedIds.LUIS, psychology, account(SeedIds.CLAUDIA)))
        assertNotNull(changeRequests.getFormContext(SeedIds.FERNANDA, psychology, account(SeedIds.CLAUDIA)))
    }

    @Test
    fun `el historial de asignaciones del area muestra las tres filas con sus fechas`() = runTest {
        val created = create().success()
        changeRequests.approve(created.requestId, SeedIds.CLAUDIA).success()

        val history = assignments.observeHistory(SeedIds.FERNANDA, psychology, account(SeedIds.CLAUDIA)).first()

        assertEquals(3, history.size)
        assertEquals(listOf(AssignmentStatus.ACTIVE, AssignmentStatus.ENDED, AssignmentStatus.ENDED), history.map { it.status })
        assertEquals(listOf("Mariana", "Rodrigo", "Mariana"), history.map { it.professionalName.substringBefore(" ") })
    }
}
