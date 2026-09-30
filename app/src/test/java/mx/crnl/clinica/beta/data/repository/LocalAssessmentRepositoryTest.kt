package mx.crnl.clinica.beta.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.common.EntityKind
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.model.AccessRequestDraft
import mx.crnl.clinica.beta.domain.model.AdministrationMode
import mx.crnl.clinica.beta.domain.model.AssessmentInstrument
import mx.crnl.clinica.beta.domain.model.AssessmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.testing.RepositoryTest
import mx.crnl.clinica.beta.testing.SeedIds
import mx.crnl.clinica.beta.testing.failure
import mx.crnl.clinica.beta.testing.success
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Las cuatro aplicaciones ficticias: A de Ana (captura profesional), A de Diego (supervisada), B de Fernanda (captura
 * profesional) y B cancelada de Diego (supervisada, sin resultado).
 */
@RunWith(AndroidJUnit4::class)
class LocalAssessmentRepositoryTest : RepositoryTest() {
    private val anaA = "a1000000-0000-4000-8000-000000000001"
    private val diegoA = "a1000000-0000-4000-8000-000000000002"
    private val fernandaB = "a1000000-0000-4000-8000-000000000003"
    private val diegoBCancelled = "a1000000-0000-4000-8000-000000000004"

    private fun countAssessments() = scalar("SELECT COUNT(*) FROM assessments")

    @Test
    fun `el historial de un paciente lista sus aplicaciones con instrumento generico, version, modo y resultado registrado`() = runTest {
        val list = assessmentRepo.observePatientAssessments(SeedIds.ANA, account(SeedIds.MARIANA)).first()

        val assessment = list.single()
        assertEquals(AssessmentInstrument.PLACEHOLDER_A, assessment.instrument)
        assertEquals("0", assessment.instrumentVersion)
        assertEquals(AdministrationMode.PROFESSIONAL_CAPTURE, assessment.mode)
        assertEquals(AssessmentStatus.COMPLETED, assessment.status)
        assertEquals(ClinicalArea.PSYCHOLOGY, assessment.area)
        assertEquals("Mariana Elizondo", assessment.professionalName)
        assertTrue(assessment.hasResult)
        assertEquals(18.0, assessment.rawScore!!, 0.0)
        assertNotNull(assessment.completedAt)
    }

    @Test
    fun `el segundo marcador se presenta como Instrumento B y nunca como uno real`() = runTest {
        val detail = assessmentRepo.observeAssessment(fernandaB, account(SeedIds.HECTOR)).first()!!

        assertEquals(AssessmentInstrument.PLACEHOLDER_B, detail.summary.instrument)
        assertEquals(31.0, detail.summary.rawScore!!, 0.0)
        assertEquals("placeholder-0", detail.scoringVersion)
        assertEquals(SeedIds.FERNANDA, detail.patientId)
        assertEquals("f0000000-0000-4000-8000-000000000005", detail.encounterId)
    }

    @Test
    fun `una aplicacion cancelada no trae resultado`() = runTest {
        val detail = assessmentRepo.observeAssessment(diegoBCancelled, account(SeedIds.RODRIGO)).first()!!

        assertEquals(AssessmentStatus.CANCELLED, detail.summary.status)
        assertFalse(detail.summary.hasResult)
        assertNull(detail.summary.rawScore)
        assertNull(detail.scoringVersion)
    }

    @Test
    fun `el modo de aplicacion se distingue entre captura profesional y supervisada`() = runTest {
        val professional = assessmentRepo.observeAssessment(anaA, account(SeedIds.MARIANA)).first()!!
        val supervised = assessmentRepo.observeAssessment(diegoA, account(SeedIds.RODRIGO)).first()!!

        assertEquals(AdministrationMode.PROFESSIONAL_CAPTURE, professional.summary.mode)
        assertEquals(AdministrationMode.SUPERVISED_PATIENT, supervised.summary.mode)
        assertFalse("la vista previa solo existe para aplicaciones supervisadas", professional.canOpenSupervisedPreview)
        assertTrue(supervised.canOpenSupervisedPreview)
    }

    // ---------------------------------------------------------------- quién puede leer

    @Test
    fun `una area ajena no filtra ni el listado ni el detalle de evaluaciones`() = runTest {
        val paola = account(SeedIds.PAOLA)

        assertTrue(assessmentRepo.observePatientAssessments(SeedIds.ANA, paola).first().isEmpty())
        assertNull(assessmentRepo.observeAssessment(anaA, paola).first())
        assertNull(assessmentRepo.observeAssessment(fernandaB, paola).first())
    }

    @Test
    fun `el administrador del sistema no recibe ninguna evaluacion clinica`() = runTest {
        val system = insertSystemAdmin()

        assertTrue(assessmentRepo.observePatientAssessments(SeedIds.ANA, system).first().isEmpty())
        assertNull(assessmentRepo.observeAssessment(anaA, system).first())
    }

    @Test
    fun `administracion clinica y la coordinacion del area las leen`() = runTest {
        assertEquals(4, listOf(SeedIds.ANA, SeedIds.DIEGO, SeedIds.FERNANDA).flatMap { assessmentRepo.observePatientAssessments(it, account(SeedIds.HECTOR)).first() }.size)
        assertNotNull(assessmentRepo.observeAssessment(anaA, account(SeedIds.CLAUDIA)).first())
    }

    @Test
    fun `una concesion de lectura vigente abre las evaluaciones del area solo en lectura`() = runTest {
        val request = accessRequests.create(AccessRequestDraft(SeedIds.FERNANDA, ClinicalArea.PSYCHOLOGY, "Necesito revisar el historial de evaluaciones"), SeedIds.PAOLA).success()
        val paola = account(SeedIds.PAOLA)
        assertNull(assessmentRepo.observeAssessment(fernandaB, paola).first())

        accessRequests.approve(request.requestId, 7, SeedIds.HECTOR).success()

        val detail = assessmentRepo.observeAssessment(fernandaB, paola).first()
        assertNotNull(detail)
        assertEquals(1, assessmentRepo.observePatientAssessments(SeedIds.FERNANDA, paola).first().size)
        assertFalse("la concesión no abre el modo supervisado", detail!!.canOpenSupervisedPreview)
        // Y solo del paciente concedido.
        assertNull(assessmentRepo.observeAssessment(anaA, paola).first())
    }

    @Test
    fun `una concesion revocada retira las evaluaciones`() = runTest {
        val request = accessRequests.create(AccessRequestDraft(SeedIds.FERNANDA, ClinicalArea.PSYCHOLOGY, "Necesito revisar el historial de evaluaciones"), SeedIds.PAOLA).success()
        val grant = requireNotNull(accessRequests.approve(request.requestId, 7, SeedIds.HECTOR).success().grant)
        accessRequests.revokeGrant(grant.grantId, SeedIds.HECTOR).success()

        assertNull(assessmentRepo.observeAssessment(fernandaB, account(SeedIds.PAOLA)).first())
    }

    @Test
    fun `una aplicacion inexistente no se emite`() = runTest {
        assertNull(assessmentRepo.observeAssessment("no-existe", account(SeedIds.HECTOR)).first())
    }

    // ---------------------------------------------------------------- vista previa supervisada

    @Test
    fun `la vista previa supervisada se autoriza por rol, se audita y no crea nada`() = runTest {
        val assessmentsBefore = countAssessments()
        val resultsBefore = scalar("SELECT COUNT(*) FROM assessment_results")

        assessmentRepo.openSupervisedPreview(diegoA, SeedIds.RODRIGO).success()

        assertEquals(assessmentsBefore, countAssessments())
        assertEquals(resultsBefore, scalar("SELECT COUNT(*) FROM assessment_results"))
        val entry = auditRows("SUPERVISED_PREVIEW_OPENED").single()
        assertEquals(SeedIds.RODRIGO, entry.actor)
        assertEquals("ASSESSMENT", entry.entityType)
        assertEquals(diegoA, entry.entityId)
        assertEquals(SeedIds.DIEGO, entry.patientId)
        assertEquals("PSYCHOLOGY", entry.areaCode)
        assertNull(entry.metadata)
    }

    @Test
    fun `coordinacion y administracion clinica tambien abren la vista previa`() = runTest {
        assessmentRepo.openSupervisedPreview(diegoA, SeedIds.CLAUDIA).success()
        assessmentRepo.openSupervisedPreview(diegoA, SeedIds.HECTOR).success()

        assertEquals(2, auditRows("SUPERVISED_PREVIEW_OPENED").size)
    }

    @Test
    fun `la vista previa se niega a otras areas, al sistema y a las aplicaciones de captura profesional`() = runTest {
        assessmentRepo.openSupervisedPreview(diegoA, SeedIds.PAOLA).failure<OperationError.NotAuthorized>()
        assessmentRepo.openSupervisedPreview(diegoA, insertSystemAdmin().userId).failure<OperationError.NotAuthorized>()
        assessmentRepo.openSupervisedPreview(anaA, SeedIds.MARIANA).failure<OperationError.NotAuthorized>()
        assessmentRepo.openSupervisedPreview(diegoA, "fantasma").failure<OperationError.NotAuthorized>()
        assertEquals(EntityKind.ASSESSMENT, assessmentRepo.openSupervisedPreview("no-existe", SeedIds.HECTOR).failure<OperationError.NotFound>().entity)
        assertTrue(auditRows("SUPERVISED_PREVIEW_OPENED").isEmpty())
    }

    @Test
    fun `una concesion de lectura no abre la vista previa supervisada`() = runTest {
        val request = accessRequests.create(AccessRequestDraft(SeedIds.DIEGO, ClinicalArea.PSYCHOLOGY, "Necesito revisar el historial de evaluaciones"), SeedIds.PAOLA).success()
        // Diego solo tiene atención de Psicología: Paola pide acceso a esa área y, aun con la lectura, no abre la vista previa.
        accessRequests.approve(request.requestId, 7, SeedIds.HECTOR).success()

        assessmentRepo.openSupervisedPreview(diegoA, SeedIds.PAOLA).failure<OperationError.NotAuthorized>()
    }

    @Test
    fun `el repositorio de evaluaciones no expone escrituras de aplicaciones ni de resultados`() {
        val writers = mx.crnl.clinica.beta.domain.repository.AssessmentRepository::class.java.methods
            .map { it.name }
            .filter { name -> listOf("create", "save", "insert", "update", "delete", "score", "calculate").any { name.startsWith(it) } }

        assertEquals(emptyList<String>(), writers)
    }
}
