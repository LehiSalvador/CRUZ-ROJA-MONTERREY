package mx.crnl.clinica.beta.domain.clinical

import java.time.Instant
import mx.crnl.clinica.beta.domain.model.AssignmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ClinicalArea.NUTRITION
import mx.crnl.clinica.beta.domain.model.ClinicalArea.PSYCHOLOGY
import mx.crnl.clinica.beta.domain.model.PatientAssignment
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AreaCapabilitiesTest {
    private val psychologist = userAccount(id = "psy", role = UserRole.PROFESSIONAL, area = PSYCHOLOGY)
    private val coordinator = userAccount(id = "coord", role = UserRole.AREA_COORDINATOR, area = PSYCHOLOGY)
    private val clinicalAdmin = userAccount(id = "admin", role = UserRole.CLINICAL_ADMIN, area = null)
    private val systemAdmin = userAccount(id = "sys", role = UserRole.SYSTEM_ADMIN, area = null)

    private fun assignment(professional: String, area: ClinicalArea = PSYCHOLOGY) = PatientAssignment(
        assignmentId = "a", area = area, professionalId = professional, professionalName = "Profesional", since = Instant.EPOCH,
        until = null, status = AssignmentStatus.ACTIVE, reason = null, assignedByName = "Claudia",
    )

    @Test
    fun `un profesional con paciente propio agenda y registra pero no asigna`() {
        val caps = AreaCapabilitiesResolver.resolve(psychologist, PSYCHOLOGY, assignment("psy"))

        assertTrue(caps.canViewDetail)
        assertFalse(caps.canAssign)
        assertTrue(caps.canSchedule)
        assertTrue(caps.canRegisterEncounter)
    }

    @Test
    fun `un profesional con paciente de otro profesional ve pero no agenda ni registra`() {
        val caps = AreaCapabilitiesResolver.resolve(psychologist, PSYCHOLOGY, assignment("otro"))

        assertTrue(caps.canViewDetail)
        assertFalse(caps.canSchedule)
        assertFalse(caps.canRegisterEncounter)
        assertFalse(caps.canAssign)
    }

    @Test
    fun `coordinacion asigna solo cuando el area no tiene profesional vigente`() {
        assertTrue(AreaCapabilitiesResolver.resolve(coordinator, PSYCHOLOGY, null).canAssign)
        assertFalse(AreaCapabilitiesResolver.resolve(coordinator, PSYCHOLOGY, assignment("psy")).canAssign)
    }

    @Test
    fun `sin profesional asignado no se agenda ni se registra`() {
        val caps = AreaCapabilitiesResolver.resolve(coordinator, PSYCHOLOGY, null)

        assertFalse(caps.canSchedule)
        assertFalse(caps.canRegisterEncounter)
    }

    @Test
    fun `coordinacion agenda y registra con el profesional asignado de su area`() {
        val caps = AreaCapabilitiesResolver.resolve(coordinator, PSYCHOLOGY, assignment("psy"))

        assertTrue(caps.canSchedule)
        assertTrue(caps.canRegisterEncounter)
    }

    @Test
    fun `un area ajena no permite nada`() {
        val caps = AreaCapabilitiesResolver.resolve(coordinator, NUTRITION, null)

        assertFalse(caps.canViewDetail)
        assertFalse(caps.canAssign)
        assertFalse(caps.canSchedule)
        assertFalse(caps.canRegisterEncounter)
    }

    @Test
    fun `administracion clinica puede todo en cualquier area`() {
        assertTrue(AreaCapabilitiesResolver.resolve(clinicalAdmin, NUTRITION, null).canAssign)
        val caps = AreaCapabilitiesResolver.resolve(clinicalAdmin, NUTRITION, assignment("nut", NUTRITION))
        assertTrue(caps.canSchedule)
        assertTrue(caps.canRegisterEncounter)
    }

    @Test
    fun `el administrador del sistema no puede nada`() {
        assertEquals(
            AreaCapabilities(canViewDetail = false, canAssign = false, canSchedule = false, canRegisterEncounter = false),
            AreaCapabilitiesResolver.resolve(systemAdmin, PSYCHOLOGY, assignment("psy")),
        )
    }
}
