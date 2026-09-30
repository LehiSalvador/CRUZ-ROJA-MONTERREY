package mx.crnl.clinica.beta.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.appointment.AppointmentAction
import mx.crnl.clinica.beta.domain.common.EntityKind
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.model.AccessGrantStatus
import mx.crnl.clinica.beta.domain.model.AccessRequestDraft
import mx.crnl.clinica.beta.domain.model.AppointmentDraft
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.EncounterDraft
import mx.crnl.clinica.beta.domain.model.EncounterType
import mx.crnl.clinica.beta.domain.model.RequestStatus
import mx.crnl.clinica.beta.testing.RepositoryTest
import mx.crnl.clinica.beta.testing.SeedIds
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.TestZone
import mx.crnl.clinica.beta.testing.failure
import mx.crnl.clinica.beta.testing.observeAround
import mx.crnl.clinica.beta.testing.success
import mx.crnl.clinica.beta.testing.testInstant
import mx.crnl.clinica.beta.testing.userEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fernanda se atiende en Psicología (Rodrigo) y Nutrición (Paola). Mariana es psicóloga: de Nutrición solo ve que hay
 * atención, hasta que una concesión de lectura vigente le abre el área, sin darle ninguna escritura.
 */
@RunWith(AndroidJUnit4::class)
class LocalAccessRequestRepositoryTest : RepositoryTest() {
    private val reason = "Necesito consultar el seguimiento nutricional"
    private val nutritionCoordinator = "a0000000-0000-4000-8000-0000000000c1"

    private fun request(area: ClinicalArea = ClinicalArea.NUTRITION, patient: String = SeedIds.FERNANDA, reason: String = this.reason) =
        AccessRequestDraft(patient, area, reason)

    private suspend fun create(draft: AccessRequestDraft = request(), actor: String = SeedIds.MARIANA) =
        accessRequests.create(draft, actor)

    private suspend fun approve(requestId: String, days: Int = 7, actor: String = SeedIds.HECTOR) =
        accessRequests.approve(requestId, days, actor)

    private suspend fun insertNutritionCoordinator() {
        db.userDao().insert(userEntity(id = nutritionCoordinator, role = "AREA_COORDINATOR", area = "NUTRITION", firstName = "Renata", paternalSurname = "Coordinadora"))
    }

    /** Repositorios que leen con otro reloj: sirve para comprobar que una concesión vence sola. */
    private fun clockAt(instant: Instant): Clock = Clock.fixed(instant, TestZone)

    // ---------------------------------------------------------------- solicitar

    @Test
    fun `un profesional solicita lectura de un area ajena y queda pendiente sin conceder nada`() = runTest {
        val created = create().success()

        assertEquals(RequestStatus.PENDING, created.status)
        assertEquals(SeedIds.MARIANA, created.requesterId)
        assertEquals(ClinicalArea.PSYCHOLOGY, created.requesterArea)
        assertEquals(ClinicalArea.NUTRITION, created.ownerArea)
        assertEquals(SeedIds.FERNANDA, created.patientId)
        assertEquals("READ", text("SELECT requestedScope FROM interarea_access_requests WHERE accessRequestId = '${created.requestId}'"))
        assertEquals(0, scalar("SELECT COUNT(*) FROM access_grants"))
        assertNull(created.grant)
    }

    @Test
    fun `solicitar deja constancia sin el motivo escrito`() = runTest {
        val created = create().success()

        val entry = auditRows("INTERAREA_REQUEST_CREATED").single()
        assertEquals(SeedIds.MARIANA, entry.actor)
        assertEquals(created.requestId, entry.entityId)
        assertEquals(SeedIds.FERNANDA, entry.patientId)
        assertEquals("NUTRITION", entry.areaCode)
        assertFalse(entry.metadata.orEmpty().contains("seguimiento"))
    }

    @Test
    fun `solicitar valida el motivo, el area, la actividad y los duplicados`() = runTest {
        create(request(reason = "corto")).failure<OperationError.InvalidReason>()
        create(request(area = ClinicalArea.PSYCHOLOGY)).failure<OperationError.AreaAlreadyReadable>()
        create(request(patient = SeedIds.ANA)).failure<OperationError.NoAreaActivity>()
        create(request(patient = "no-existe")).failure<OperationError.NotFound>()
        assertEquals(0, scalar("SELECT COUNT(*) FROM interarea_access_requests"))

        create().success()
        create().failure<OperationError.DuplicatePendingRequest>()
        assertEquals(1, scalar("SELECT COUNT(*) FROM interarea_access_requests"))
    }

    @Test
    fun `administracion clinica y del sistema no piden acceso`() = runTest {
        val system = insertSystemAdmin()

        create(actor = SeedIds.HECTOR).failure<OperationError.AreaAlreadyReadable>()
        create(actor = system.userId).failure<OperationError.NotAuthorized>()
        create(actor = "fantasma").failure<OperationError.NotAuthorized>()
    }

    // ---------------------------------------------------------------- revisar

    @Test
    fun `administracion clinica aprueba y se crea una concesion de solo lectura con vigencia`() = runTest {
        val created = create().success()

        val approved = approve(created.requestId, days = 7).success()

        assertEquals(RequestStatus.APPROVED, approved.status)
        assertEquals("Héctor Montemayor Salazar", approved.reviewerName)
        val grant = requireNotNull(approved.grant)
        assertEquals(SeedIds.MARIANA, grant.granteeUserId)
        assertEquals(SeedIds.FERNANDA, grant.patientId)
        assertEquals(ClinicalArea.NUTRITION, grant.ownerArea)
        assertEquals("READ", grant.scope.name)
        assertEquals(TestNow.toInstant(), grant.validFrom)
        assertEquals(TestNow.toInstant().plusSeconds(7 * 86_400L), grant.expiresAt)
        assertEquals(AccessGrantStatus.ACTIVE, grant.statusAt(TestNow.toInstant()))
        assertEquals(1, scalar("SELECT COUNT(*) FROM access_grants"))
        assertEquals(SeedIds.HECTOR, text("SELECT grantedBy FROM access_grants WHERE accessRequestId = '${created.requestId}'"))
    }

    @Test
    fun `aprobar solo cambia la solicitud y la concesion, no las asignaciones ni las citas`() = runTest {
        val created = create().success()
        val assignmentsBefore = scalar("SELECT COUNT(*) FROM professional_assignments")
        val appointmentsBefore = scalar("SELECT COUNT(*) FROM appointments")

        approve(created.requestId).success()

        assertEquals(assignmentsBefore, scalar("SELECT COUNT(*) FROM professional_assignments"))
        assertEquals(appointmentsBefore, scalar("SELECT COUNT(*) FROM appointments"))
        assertEquals(1, auditRows("INTERAREA_REQUEST_APPROVED").size)
        assertEquals("7", auditRows("INTERAREA_REQUEST_APPROVED").single().metadata.orEmpty().substringAfter("\"days\":\"").substringBefore("\""))
    }

    @Test
    fun `la coordinacion del area propietaria aprueba y la de otra area no`() = runTest {
        insertNutritionCoordinator()
        val created = create().success()

        approve(created.requestId, actor = SeedIds.CLAUDIA).failure<OperationError.NotAuthorized>()
        assertEquals("PENDING", text("SELECT status FROM interarea_access_requests WHERE accessRequestId = '${created.requestId}'"))
        assertEquals(0, scalar("SELECT COUNT(*) FROM access_grants"))

        approve(created.requestId, actor = nutritionCoordinator).success()
        assertEquals(1, scalar("SELECT COUNT(*) FROM access_grants"))
    }

    @Test
    fun `quien la pidio no la resuelve y un profesional del area tampoco`() = runTest {
        // Claudia (coordinación de Psicología) pide Nutrición: administración clínica la revisa, no ella misma.
        val created = create(actor = SeedIds.CLAUDIA).success()

        approve(created.requestId, actor = SeedIds.CLAUDIA).failure<OperationError.NotAuthorized>()
        approve(created.requestId, actor = SeedIds.PAOLA).failure<OperationError.NotAuthorized>()
        approve(created.requestId, actor = insertSystemAdmin().userId).failure<OperationError.NotAuthorized>()
        assertEquals(0, scalar("SELECT COUNT(*) FROM access_grants"))
    }

    @Test
    fun `la vigencia debe ser una de las permitidas`() = runTest {
        val created = create().success()

        approve(created.requestId, days = 0).failure<OperationError.InvalidGrantDuration>()
        approve(created.requestId, days = 365).failure<OperationError.InvalidGrantDuration>()

        assertEquals(0, scalar("SELECT COUNT(*) FROM access_grants"))
        assertEquals(1, approve(created.requestId, days = 30).success().grant?.let { 1 })
    }

    @Test
    fun `rechazar cambia el estado, no crea concesion y no se puede aprobar despues`() = runTest {
        val created = create().success()

        val rejected = accessRequests.reject(created.requestId, SeedIds.HECTOR).success()

        assertEquals(RequestStatus.REJECTED, rejected.status)
        assertNull(rejected.grant)
        assertEquals(0, scalar("SELECT COUNT(*) FROM access_grants"))
        assertEquals(1, auditRows("INTERAREA_REQUEST_REJECTED").size)
        val error = approve(created.requestId).failure<OperationError.RequestAlreadyResolved>()
        assertEquals(RequestStatus.REJECTED, error.current)
        assertEquals(0, scalar("SELECT COUNT(*) FROM access_grants"))
    }

    @Test
    fun `aprobar dos veces crea una sola concesion y una sola entrada de bitacora`() = runTest {
        val created = create().success()
        approve(created.requestId).success()

        val error = approve(created.requestId).failure<OperationError.RequestAlreadyResolved>()

        assertEquals(RequestStatus.APPROVED, error.current)
        assertEquals(1, scalar("SELECT COUNT(*) FROM access_grants"))
        assertEquals(1, auditRows("INTERAREA_REQUEST_APPROVED").size)
    }

    @Test
    fun `una solicitud inexistente se rechaza`() = runTest {
        assertEquals(EntityKind.ACCESS_REQUEST, approve("no-existe").failure<OperationError.NotFound>().entity)
        assertEquals(EntityKind.ACCESS_REQUEST, accessRequests.reject("no-existe", SeedIds.HECTOR).failure<OperationError.NotFound>().entity)
    }

    // ---------------------------------------------------------------- lo que la concesion abre

    @Test
    fun `sin concesion el area ajena no muestra profesional, citas ni atencion`() = runTest {
        val mariana = account(SeedIds.MARIANA)

        val detail = patients.observePatientDetail(SeedIds.FERNANDA, mariana).first()!!

        assertEquals(setOf(ClinicalArea.PSYCHOLOGY), detail.viewableAreas)
        assertTrue(detail.encounters.none { it.area == ClinicalArea.NUTRITION })
        assertTrue(detail.appointments.none { it.area == ClinicalArea.NUTRITION })
        assertNull(assignments.getActiveAssignment(SeedIds.FERNANDA, ClinicalArea.NUTRITION, mariana))
        assertNull(appointments.getAppointment(SeedIds.CITA_FERNANDA_NUTRICION, mariana))
    }

    @Test
    fun `con una concesion vigente el area se lee completa con profesional, citas y atencion`() = runTest {
        val created = create().success()
        val mariana = account(SeedIds.MARIANA)
        approve(created.requestId).success()

        val detail = patients.observePatientDetail(SeedIds.FERNANDA, mariana).first()!!

        assertEquals(setOf(ClinicalArea.PSYCHOLOGY, ClinicalArea.NUTRITION), detail.viewableAreas)
        assertEquals(TestNow.toInstant().plusSeconds(7 * 86_400L), detail.grantedAreas.getValue(ClinicalArea.NUTRITION))
        assertEquals("Paola Garza", detail.activeAssignment(ClinicalArea.NUTRITION)?.professionalName)
        assertTrue(detail.encounters.any { it.area == ClinicalArea.NUTRITION })
        assertTrue(detail.appointments.any { it.area == ClinicalArea.NUTRITION })
        assertEquals("Paola Garza", assignments.getActiveAssignment(SeedIds.FERNANDA, ClinicalArea.NUTRITION, mariana)?.professionalName)
        assertNotNull(appointments.getAppointment(SeedIds.CITA_FERNANDA_NUTRICION, mariana))
        assertEquals(1, encounters.observeAreaEncounters(SeedIds.FERNANDA, ClinicalArea.NUTRITION, mariana).first().size)
        assertEquals(1, assignments.observeHistory(SeedIds.FERNANDA, ClinicalArea.NUTRITION, mariana).first().size)
    }

    @Test
    fun `la concesion no habilita citas, atencion, asignaciones ni cambios de profesional`() = runTest {
        val created = create().success()
        approve(created.requestId).success()

        appointments.createAppointment(
            AppointmentDraft(SeedIds.FERNANDA, ClinicalArea.NUTRITION, SeedIds.PAOLA, testInstant(2, 12), 30, AppointmentModality.IN_PERSON, "Consultorio 2", null, null),
            SeedIds.MARIANA,
        ).failure<OperationError.NotAuthorized>()
        encounters.createEncounter(
            EncounterDraft(SeedIds.FERNANDA, ClinicalArea.NUTRITION, SeedIds.PAOLA, null, EncounterType.FOLLOW_UP, testInstant(0, 9)),
            SeedIds.MARIANA,
        ).failure<OperationError.NotAuthorized>()
        assignments.createInitialAssignment(SeedIds.FERNANDA, ClinicalArea.NUTRITION, SeedIds.PAOLA, null, SeedIds.MARIANA).failure<OperationError.NotAuthorized>()
        appointments.applyAction(
            SeedIds.CITA_FERNANDA_NUTRICION,
            AppointmentAction.CONFIRM,
            AppointmentStatus.SCHEDULED,
            null,
            SeedIds.MARIANA,
        ).failure<OperationError.NotAuthorized>()

        assertEquals("SCHEDULED", text("SELECT status FROM appointments WHERE appointmentId = '${SeedIds.CITA_FERNANDA_NUTRICION}'"))
        assertEquals(6, scalar("SELECT COUNT(*) FROM clinical_encounters"))
    }

    @Test
    fun `la concesion es solo para su titular, su paciente y su area`() = runTest {
        val created = create().success()
        approve(created.requestId).success()

        // Otra persona de Psicología no la hereda.
        val rodrigo = account(SeedIds.RODRIGO)
        assertTrue(patients.observePatientDetail(SeedIds.FERNANDA, rodrigo).first()!!.encounters.none { it.area == ClinicalArea.NUTRITION })
        // Otro paciente con atención de Nutrición tampoco.
        val luis = patients.observePatientDetail(SeedIds.LUIS, account(SeedIds.MARIANA)).first()!!
        assertEquals(setOf(ClinicalArea.PSYCHOLOGY), luis.viewableAreas)
        assertTrue(luis.grantedAreas.isEmpty())
        // Otra área del mismo paciente (Medicina no tiene atención de Fernanda; se verifica que Nutrición no abre más de lo pedido).
        assertEquals(setOf(ClinicalArea.NUTRITION), patients.observePatientDetail(SeedIds.FERNANDA, account(SeedIds.MARIANA)).first()!!.grantedAreas.keys)
    }

    @Test
    fun `una concesion vencida ya no se usa`() = runTest {
        val created = create().success()
        approve(created.requestId, days = 1).success()
        val mariana = account(SeedIds.MARIANA)
        val later = LocalPatientRepository(db, audit, clockAt(TestNow.toInstant().plusSeconds(86_400L + 60)), newId)

        val detail = later.observePatientDetail(SeedIds.FERNANDA, mariana).first()!!

        assertEquals(setOf(ClinicalArea.PSYCHOLOGY), detail.viewableAreas)
        assertTrue(detail.grantedAreas.isEmpty())
        assertTrue(detail.encounters.none { it.area == ClinicalArea.NUTRITION })
        val request = accessRequests.observeRequest(created.requestId, mariana).first()!!
        assertEquals(AccessGrantStatus.EXPIRED, request.grant?.statusAt(TestNow.toInstant().plusSeconds(86_400L + 60)))
    }

    // ---------------------------------------------------------------- revocar

    @Test
    fun `administracion clinica revoca la concesion y la lectura se retira`() = runTest {
        val created = create().success()
        val mariana = account(SeedIds.MARIANA)
        val grantId = requireNotNull(approve(created.requestId).success().grant).grantId

        val seen = patients.observePatientDetail(SeedIds.FERNANDA, mariana).observeAround(
            change = { accessRequests.revokeGrant(grantId, SeedIds.HECTOR).success() },
            until = { it?.grantedAreas?.isEmpty() == true },
        )

        assertTrue(seen.first()!!.grantedAreas.isNotEmpty())
        assertTrue(seen.last()!!.encounters.none { it.area == ClinicalArea.NUTRITION })
        assertEquals("REVOKED", text("SELECT status FROM access_grants WHERE accessGrantId = '$grantId'"))
        assertEquals(SeedIds.HECTOR, text("SELECT revokedBy FROM access_grants WHERE accessGrantId = '$grantId'"))
        assertEquals(1, scalar("SELECT COUNT(*) FROM access_grants"))
        assertEquals(1, auditRows("ACCESS_GRANT_REVOKED").size)
    }

    @Test
    fun `revocar exige permiso de revision y una concesion activa`() = runTest {
        insertNutritionCoordinator()
        val created = create().success()
        val grantId = requireNotNull(approve(created.requestId).success().grant).grantId

        accessRequests.revokeGrant(grantId, SeedIds.MARIANA).failure<OperationError.NotAuthorized>()
        accessRequests.revokeGrant(grantId, SeedIds.CLAUDIA).failure<OperationError.NotAuthorized>()
        accessRequests.revokeGrant(grantId, nutritionCoordinator).success()
        accessRequests.revokeGrant(grantId, SeedIds.HECTOR).failure<OperationError.GrantNotActive>()
        assertEquals(EntityKind.ACCESS_GRANT, accessRequests.revokeGrant("no-existe", SeedIds.HECTOR).failure<OperationError.NotFound>().entity)
        assertEquals(1, auditRows("ACCESS_GRANT_REVOKED").size)
    }

    @Test
    fun `una concesion vencida no se revoca`() = runTest {
        val created = create().success()
        val grantId = requireNotNull(approve(created.requestId, days = 1).success().grant).grantId
        val later = LocalAccessRequestRepository(db, audit, clockAt(TestNow.toInstant().plusSeconds(2 * 86_400L)), newId)

        later.revokeGrant(grantId, SeedIds.HECTOR).failure<OperationError.GrantNotActive>()
    }

    // ---------------------------------------------------------------- bandeja

    @Test
    fun `cada persona ve sus solicitudes o las que le toca revisar y el sistema ninguna`() = runTest {
        insertNutritionCoordinator()
        create().success()

        assertEquals(1, accessRequests.observeRequests(account(SeedIds.MARIANA)).first().size)
        assertEquals(1, accessRequests.observeRequests(account(SeedIds.HECTOR)).first().size)
        assertEquals(1, accessRequests.observeRequests(account(nutritionCoordinator)).first().size)
        assertTrue(accessRequests.observeRequests(account(SeedIds.RODRIGO)).first().isEmpty())
        assertTrue(accessRequests.observeRequests(account(SeedIds.CLAUDIA)).first().isEmpty())
        assertTrue(accessRequests.observeRequests(insertSystemAdmin()).first().isEmpty())
    }

    @Test
    fun `el contexto del formulario avisa si ya hay una solicitud pendiente`() = runTest {
        val mariana = account(SeedIds.MARIANA)
        assertFalse(accessRequests.getRequestContext(SeedIds.FERNANDA, ClinicalArea.NUTRITION, mariana)!!.alreadyPending)
        create().success()

        assertTrue(accessRequests.getRequestContext(SeedIds.FERNANDA, ClinicalArea.NUTRITION, mariana)!!.alreadyPending)
        assertNull(accessRequests.getRequestContext(SeedIds.FERNANDA, ClinicalArea.PSYCHOLOGY, mariana))
        assertNull(accessRequests.getRequestContext("no-existe", ClinicalArea.NUTRITION, mariana))
    }
}
