package mx.crnl.clinica.beta.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.appointment.AppointmentAction
import mx.crnl.clinica.beta.domain.model.AccessRequestDraft
import mx.crnl.clinica.beta.domain.model.AppointmentDraft
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.EncounterDraft
import mx.crnl.clinica.beta.domain.model.EncounterType
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.testing.RepositoryTest
import mx.crnl.clinica.beta.testing.SeedIds
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.TestZone
import mx.crnl.clinica.beta.testing.success
import mx.crnl.clinica.beta.testing.testInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Matriz de fuga: Paola (Nutrición) frente a lo que Fernanda tiene en Psicología con Rodrigo. Para cada estado del
 * acceso se listan las lecturas que devuelven algo; sin concesión vigente no debe salir ninguna, con ella salen
 * todas, y las escrituras se rechazan siempre.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyLeakMatrixTest : RepositoryTest() {
    private val psychology = ClinicalArea.PSYCHOLOGY
    private val psychologyEncounter = "f0000000-0000-4000-8000-000000000005"
    private val psychologyAssessment = "a1000000-0000-4000-8000-000000000003"
    private val allReads = setOf("detalle-citas", "detalle-atencion", "detalle-asignacion", "detalle-evaluaciones", "cita", "atencion", "asignacion", "evaluacion", "historial", "citas-paciente")

    private class Readers(
        val patients: LocalPatientRepository,
        val appointments: LocalAppointmentRepository,
        val encounters: LocalEncounterRepository,
        val assignments: LocalProfessionalAssignmentRepository,
        val assessments: LocalAssessmentRepository,
    )

    private fun readers(clock: Clock = this.clock) = Readers(
        LocalPatientRepository(db, audit, clock, newId),
        LocalAppointmentRepository(db, audit, clock, newId),
        LocalEncounterRepository(db, audit, clock, newId),
        LocalProfessionalAssignmentRepository(db, audit, clock, newId),
        LocalAssessmentRepository(db, audit, clock),
    )

    /** Las lecturas de Psicología de Fernanda que devuelven algo a [viewer]. */
    private suspend fun leaks(viewer: UserAccount, readers: Readers = readers()): Set<String> = buildSet {
        val detail = readers.patients.observePatientDetail(SeedIds.FERNANDA, viewer).first()!!
        if (detail.appointments.any { it.area == psychology }) add("detalle-citas")
        if (detail.encounters.any { it.area == psychology }) add("detalle-atencion")
        if (detail.assignments.any { it.area == psychology }) add("detalle-asignacion")
        if (detail.assessments.any { it.area == psychology }) add("detalle-evaluaciones")
        if (readers.appointments.getAppointment(SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA, viewer) != null) add("cita")
        if (readers.encounters.observeEncounter(psychologyEncounter, viewer).first() != null) add("atencion")
        if (readers.assignments.getActiveAssignment(SeedIds.FERNANDA, psychology, viewer) != null) add("asignacion")
        if (readers.assessments.observeAssessment(psychologyAssessment, viewer).first() != null) add("evaluacion")
        if (readers.assignments.observeHistory(SeedIds.FERNANDA, psychology, viewer).first().isNotEmpty()) add("historial")
        if (readers.appointments.observePatientAppointments(SeedIds.FERNANDA, viewer).first().any { it.area == psychology }) add("citas-paciente")
    }

    /** Las escrituras sobre Psicología de Fernanda que aceptan a [actor]; deben ser ninguna. */
    private suspend fun writesAccepted(actor: String): Set<String> = buildSet {
        val draft = AppointmentDraft(SeedIds.FERNANDA, psychology, SeedIds.RODRIGO, testInstant(2, 15), 30, AppointmentModality.IN_PERSON, "Consultorio 1", null, null)
        if (appointments.createAppointment(draft, actor) is mx.crnl.clinica.beta.domain.common.OperationResult.Success) add("crear-cita")
        val encounter = EncounterDraft(SeedIds.FERNANDA, psychology, SeedIds.RODRIGO, null, EncounterType.FOLLOW_UP, testInstant(0, 9))
        if (encounters.createEncounter(encounter, actor) is mx.crnl.clinica.beta.domain.common.OperationResult.Success) add("crear-atencion")
        if (assignments.createInitialAssignment(SeedIds.FERNANDA, psychology, SeedIds.MARIANA, null, actor) is mx.crnl.clinica.beta.domain.common.OperationResult.Success) add("asignar")
        if (appointments.applyAction(SeedIds.CITA_FERNANDA_HOY_PSICOLOGIA, AppointmentAction.CANCEL, AppointmentStatus.CONFIRMED, null, actor) is mx.crnl.clinica.beta.domain.common.OperationResult.Success) add("cancelar-cita")
        if (assessmentRepo.openSupervisedPreview(psychologyAssessment, actor) is mx.crnl.clinica.beta.domain.common.OperationResult.Success) add("modo-supervisado")
    }

    private suspend fun grantToPaola(days: Int = 7): String {
        val request = accessRequests.create(AccessRequestDraft(SeedIds.FERNANDA, psychology, "Necesito consultar el seguimiento psicológico"), SeedIds.PAOLA).success()
        return requireNotNull(accessRequests.approve(request.requestId, days, SeedIds.CLAUDIA).success().grant).grantId
    }

    @Test
    fun `sin concesion no se filtra ninguna lectura de otra area`() = runTest {
        assertEquals(emptySet<String>(), leaks(account(SeedIds.PAOLA)))
    }

    @Test
    fun `de un area ajena solo sale la constancia de atencion, sin profesional ni tipo`() = runTest {
        val detail = patients.observePatientDetail(SeedIds.FERNANDA, account(SeedIds.PAOLA)).first()!!

        val activity = detail.restrictedAreas.single { it.area == psychology }
        assertEquals(1, activity.encounterCount)
        assertTrue(detail.encounters.none { it.area == psychology })
        assertTrue((detail.assignments + detail.assignmentHistory).none { it.area == psychology })
    }

    @Test
    fun `con una concesion vigente salen las lecturas del area y solo esas`() = runTest {
        grantToPaola()

        assertEquals(allReads, leaks(account(SeedIds.PAOLA)))
        // Otro paciente con atención de Psicología no se abre con la concesión de Fernanda.
        val other = patients.observePatientDetail(SeedIds.DIEGO, account(SeedIds.PAOLA)).first()!!
        assertTrue(other.encounters.isEmpty())
        assertTrue(other.grantedAreas.isEmpty())
    }

    @Test
    fun `una concesion vencida vuelve a no filtrar nada`() = runTest {
        grantToPaola(days = 1)
        assertEquals(allReads, leaks(account(SeedIds.PAOLA)))

        val expired = readers(Clock.fixed(TestNow.toInstant().plusSeconds(2 * 86_400L), TestZone))

        assertEquals(emptySet<String>(), leaks(account(SeedIds.PAOLA), expired))
    }

    @Test
    fun `una concesion revocada vuelve a no filtrar nada`() = runTest {
        val grantId = grantToPaola()
        assertEquals(allReads, leaks(account(SeedIds.PAOLA)))

        accessRequests.revokeGrant(grantId, SeedIds.HECTOR).success()

        assertEquals(emptySet<String>(), leaks(account(SeedIds.PAOLA)))
    }

    @Test
    fun `ninguna escritura se acepta jamas, con o sin concesion`() = runTest {
        assertEquals("sin concesión", emptySet<String>(), writesAccepted(SeedIds.PAOLA))
        grantToPaola()
        assertEquals("con concesión", emptySet<String>(), writesAccepted(SeedIds.PAOLA))
    }

    @Test
    fun `el administrador del sistema no recibe lectura clinica de ningun area`() = runTest {
        val system = insertSystemAdmin()

        assertEquals(emptySet<String>(), leaks(system))
        assertEquals(emptySet<String>(), writesAccepted(system.userId))
    }

    @Test
    fun `una cuenta suspendida con concesion vigente no lee`() = runTest {
        grantToPaola()
        setUserStatus(SeedIds.PAOLA, "SUSPENDED")

        assertEquals(emptySet<String>(), leaks(account(SeedIds.PAOLA)))
    }

    @Test
    fun `la bitacora de estas operaciones no guarda contenido clinico ni el motivo`() = runTest {
        grantToPaola()
        assessmentRepo.openSupervisedPreview("a1000000-0000-4000-8000-000000000002", SeedIds.RODRIGO).success()

        val sensitive = listOf("seguimiento", "Fernanda", "Diego", "Rodrigo", "rawScore", "18", "31", "@example.org")
        val allMetadata = db.query("SELECT metadata FROM audit_entries WHERE metadata IS NOT NULL", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        allMetadata.forEach { metadata ->
            sensitive.forEach { word -> assertTrue("la metadata «$metadata» no debe contener «$word»", !metadata.contains(word)) }
        }
    }
}
