package mx.crnl.clinica.beta.domain.clinical

import java.time.Instant
import mx.crnl.clinica.beta.domain.model.AdministrationMode
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AssessmentInstrument
import mx.crnl.clinica.beta.domain.model.AssessmentStatus
import mx.crnl.clinica.beta.domain.model.AssessmentSummary
import mx.crnl.clinica.beta.domain.model.AssignmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ClinicalArea.GENERAL_MEDICINE
import mx.crnl.clinica.beta.domain.model.ClinicalArea.NUTRITION
import mx.crnl.clinica.beta.domain.model.ClinicalArea.PSYCHOLOGY
import mx.crnl.clinica.beta.domain.model.EncounterStatus
import mx.crnl.clinica.beta.domain.model.EncounterSummary
import mx.crnl.clinica.beta.domain.model.EncounterType
import mx.crnl.clinica.beta.domain.model.PatientAssignment
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.domainAppointment
import mx.crnl.clinica.beta.testing.domainPatient
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientDetailAssemblerTest {
    private val psychologist = userAccount(id = "psy", role = UserRole.PROFESSIONAL, area = PSYCHOLOGY)
    private val coordinator = userAccount(id = "coord", role = UserRole.AREA_COORDINATOR, area = PSYCHOLOGY)
    private val clinicalAdmin = userAccount(id = "admin", role = UserRole.CLINICAL_ADMIN, area = null)
    private val systemAdmin = userAccount(id = "sys", role = UserRole.SYSTEM_ADMIN, area = null)

    private fun assignment(id: String, area: ClinicalArea, active: Boolean = true, professional: String = "pro-$area", start: Long = 1_000) =
        PatientAssignment(
            assignmentId = id,
            area = area,
            professionalId = professional,
            professionalName = "Nombre $area",
            since = Instant.ofEpochSecond(start),
            until = if (active) null else Instant.ofEpochSecond(start + 100),
            status = if (active) AssignmentStatus.ACTIVE else AssignmentStatus.ENDED,
            reason = "Asignación inicial",
            assignedByName = "Claudia Benavides",
        )

    private fun encounter(id: String, area: ClinicalArea, at: Long, type: EncounterType = EncounterType.FOLLOW_UP) = EncounterSummary(
        encounterId = id,
        area = area,
        professionalName = "Nombre $area",
        type = type,
        status = EncounterStatus.COMPLETED,
        eventAt = Instant.ofEpochSecond(at),
    )

    private val assignments = listOf(
        assignment("a-psy", PSYCHOLOGY),
        assignment("a-psy-old", PSYCHOLOGY, active = false, professional = "otro", start = 100),
        assignment("a-nut", NUTRITION),
    )
    private val encounters = listOf(
        encounter("e-psy", PSYCHOLOGY, at = 5_000),
        encounter("e-nut-1", NUTRITION, at = 3_000, type = EncounterType.INITIAL),
        encounter("e-nut-2", NUTRITION, at = 4_000),
    )
    private val appointments = listOf(
        domainAppointment(id = "c-psy", area = PSYCHOLOGY),
        domainAppointment(id = "c-nut", area = NUTRITION),
    )
    private val assessments = listOf(
        assessment("s-psy", PSYCHOLOGY, AssessmentStatus.COMPLETED, rawScore = 10.0),
        assessment("s-nut", NUTRITION, AssessmentStatus.COMPLETED),
        assessment("s-sin-area", null, AssessmentStatus.STARTED),
    )

    private fun assessment(id: String, area: ClinicalArea?, status: AssessmentStatus, rawScore: Double? = null) = AssessmentSummary(
        assessmentId = id,
        area = area,
        professionalName = "Nombre",
        status = status,
        startedAt = Instant.EPOCH,
        completedAt = null,
        instrument = AssessmentInstrument.PLACEHOLDER_A,
        instrumentVersion = "0",
        mode = AdministrationMode.PROFESSIONAL_CAPTURE,
        hasResult = rawScore != null,
        rawScore = rawScore,
    )

    private fun assemble(viewer: mx.crnl.clinica.beta.domain.model.UserAccount) = PatientDetailAssembler.assemble(
        viewer = viewer,
        patient = domainPatient(),
        contacts = emptyList(),
        assignments = assignments,
        appointments = appointments,
        encounters = encounters,
        assessments = assessments,
    )

    @Test
    fun `un profesional recibe el detalle completo de su area y nada de las demas`() {
        val detail = assemble(psychologist)

        assertEquals(setOf(PSYCHOLOGY), detail.viewableAreas)
        assertEquals(listOf("a-psy"), detail.assignments.map { it.assignmentId })
        assertEquals(listOf("a-psy", "a-psy-old"), detail.assignmentHistory.map { it.assignmentId })
        assertEquals(listOf("c-psy"), detail.appointments.map { it.appointmentId })
        assertEquals(listOf("e-psy"), detail.encounters.map { it.encounterId })
        assertEquals(listOf("s-psy"), detail.assessments.map { it.assessmentId })
    }

    @Test
    fun `las demas areas solo dejan constancia de que hay atencion, con conteo y ultima fecha`() {
        val detail = assemble(psychologist)

        assertEquals(listOf(NUTRITION), detail.restrictedAreas.map { it.area })
        val nutrition = detail.restrictedAreas.single()
        assertEquals(2, nutrition.encounterCount)
        assertEquals(Instant.ofEpochSecond(4_000), nutrition.lastActivityAt)
    }

    @Test
    fun `el area restringida no expone profesional, tipo, cita ni evaluacion`() {
        val detail = assemble(psychologist)

        val leaked = detail.assignments + detail.assignmentHistory
        assertTrue(leaked.none { it.area == NUTRITION })
        assertTrue(detail.encounters.none { it.area == NUTRITION })
        assertTrue(detail.appointments.none { it.area == NUTRITION })
        assertTrue(detail.assessments.none { it.assessmentId == "s-nut" })
    }

    @Test
    fun `un area sin asignacion ni encuentros no aparece como restringida`() {
        val detail = assemble(psychologist)

        assertTrue(detail.restrictedAreas.none { it.area == GENERAL_MEDICINE })
    }

    @Test
    fun `un area con solo asignacion cuenta como atencion aunque no tenga encuentros`() {
        val detail = PatientDetailAssembler.assemble(
            viewer = psychologist,
            patient = domainPatient(),
            contacts = emptyList(),
            assignments = listOf(assignment("a-med", GENERAL_MEDICINE)),
            appointments = emptyList(),
            encounters = emptyList(),
            assessments = emptyList(),
        )

        val medicine = detail.restrictedAreas.single()
        assertEquals(GENERAL_MEDICINE, medicine.area)
        assertEquals(0, medicine.encounterCount)
        assertNull(medicine.lastActivityAt)
    }

    @Test
    fun `coordinacion ve solo su area`() {
        val detail = assemble(coordinator)

        assertEquals(setOf(PSYCHOLOGY), detail.viewableAreas)
        assertEquals(listOf("e-psy"), detail.encounters.map { it.encounterId })
        assertEquals(listOf(NUTRITION), detail.restrictedAreas.map { it.area })
    }

    @Test
    fun `administracion clinica ve las tres areas y las evaluaciones sin area`() {
        val detail = assemble(clinicalAdmin)

        assertEquals(ClinicalArea.entries.toSet(), detail.viewableAreas)
        assertEquals(3, detail.encounters.size)
        assertEquals(2, detail.appointments.size)
        assertEquals(3, detail.assessments.size)
        assertTrue(detail.restrictedAreas.isEmpty())
    }

    @Test
    fun `el administrador del sistema recibe datos generales pero ningun detalle clinico`() {
        val detail = assemble(systemAdmin)

        assertTrue(detail.viewableAreas.isEmpty())
        assertTrue(detail.assignments.isEmpty())
        assertTrue(detail.assignmentHistory.isEmpty())
        assertTrue(detail.appointments.isEmpty())
        assertTrue(detail.encounters.isEmpty())
        assertTrue(detail.assessments.isEmpty())
        assertEquals(setOf(PSYCHOLOGY, NUTRITION), detail.restrictedAreas.map { it.area }.toSet())
    }

    @Test
    fun `los encuentros del area salen del mas reciente al mas antiguo`() {
        val detail = assemble(clinicalAdmin)

        assertEquals(listOf("e-psy", "e-nut-2", "e-nut-1"), detail.encounters.map { it.encounterId })
    }

    @Test
    fun `la proxima y la ultima cita se calculan solo con lo visible`() {
        val now = Instant.parse("2026-09-29T16:00:00Z")
        val upcoming = domainAppointment(id = "futura", area = PSYCHOLOGY, start = now.plusSeconds(86_400))
        val past = domainAppointment(id = "pasada", area = PSYCHOLOGY, start = now.minusSeconds(86_400), status = AppointmentStatus.COMPLETED)
        val hidden = domainAppointment(id = "oculta", area = NUTRITION, start = now.plusSeconds(3_600))
        val detail = PatientDetailAssembler.assemble(
            viewer = psychologist,
            patient = domainPatient(),
            contacts = emptyList(),
            assignments = emptyList(),
            appointments = listOf(upcoming, past, hidden),
            encounters = emptyList(),
            assessments = emptyList(),
        )

        assertEquals("futura", detail.nextAppointment(now)?.appointmentId)
        assertEquals("pasada", detail.lastAppointment(now)?.appointmentId)
    }
}
