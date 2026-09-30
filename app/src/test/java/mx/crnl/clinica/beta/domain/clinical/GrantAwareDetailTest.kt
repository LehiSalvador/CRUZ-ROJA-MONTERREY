package mx.crnl.clinica.beta.domain.clinical

import java.time.Instant
import mx.crnl.clinica.beta.domain.model.AccessGrant
import mx.crnl.clinica.beta.domain.model.AccessGrantStatus
import mx.crnl.clinica.beta.domain.model.AccessScope
import mx.crnl.clinica.beta.domain.model.AssignmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Un acceso temporal de lectura abre el detalle de un area, pero nunca concede una accion de escritura. */
class GrantAwareDetailTest {
    private val psychologist = userAccount(id = "psy", role = UserRole.PROFESSIONAL, area = PSYCHOLOGY)
    private val nutritionist = userAccount(id = "nut", role = UserRole.PROFESSIONAL, area = NUTRITION)
    private val expires = Instant.parse("2026-10-06T16:00:00Z")

    private val nutritionGrant = AccessGrant(
        grantId = "g1",
        requestId = "r1",
        patientId = "patient-1",
        granteeUserId = "psy",
        ownerArea = NUTRITION,
        scope = AccessScope.READ,
        validFrom = Instant.parse("2026-09-29T16:00:00Z"),
        expiresAt = expires,
        storedStatus = AccessGrantStatus.ACTIVE,
        revokedAt = null,
    )

    private val nutritionAssignment = PatientAssignment(
        assignmentId = "a-nut", area = NUTRITION, professionalId = "nut", professionalName = "Paola Garza", since = Instant.EPOCH,
        until = null, status = AssignmentStatus.ACTIVE, reason = null, assignedByName = "Héctor",
    )
    private val nutritionEncounter = EncounterSummary(
        encounterId = "e-nut", area = NUTRITION, professionalName = "Paola Garza", type = EncounterType.INITIAL,
        status = EncounterStatus.COMPLETED, eventAt = Instant.ofEpochSecond(3_000),
    )
    private val nutritionAppointment = domainAppointment(id = "c-nut", area = NUTRITION, professionalId = "nut")

    private fun assemble(grants: Map<ClinicalArea, AccessGrant> = emptyMap()) = PatientDetailAssembler.assemble(
        viewer = psychologist,
        patient = domainPatient(id = "patient-1"),
        contacts = emptyList(),
        assignments = listOf(nutritionAssignment),
        appointments = listOf(nutritionAppointment),
        encounters = listOf(nutritionEncounter),
        assessments = emptyList(),
        grants = grants,
    )

    @Test
    fun `sin concesion el area ajena solo deja constancia de atencion`() {
        val detail = assemble()

        assertEquals(setOf(PSYCHOLOGY), detail.viewableAreas)
        assertTrue(detail.encounters.isEmpty())
        assertEquals(listOf(NUTRITION), detail.restrictedAreas.map { it.area })
        assertTrue(detail.grantedAreas.isEmpty())
    }

    @Test
    fun `con una concesion vigente el area se lee completa y ya no es restringida`() {
        val detail = assemble(mapOf(NUTRITION to nutritionGrant))

        assertEquals(setOf(PSYCHOLOGY, NUTRITION), detail.viewableAreas)
        assertEquals(listOf("e-nut"), detail.encounters.map { it.encounterId })
        assertEquals(listOf("c-nut"), detail.appointments.map { it.appointmentId })
        assertEquals(listOf("a-nut"), detail.assignments.map { it.assignmentId })
        assertTrue(detail.restrictedAreas.isEmpty())
    }

    @Test
    fun `la concesion se presenta con su vencimiento como acceso temporal`() {
        val detail = assemble(mapOf(NUTRITION to nutritionGrant))

        assertEquals(mapOf(NUTRITION to expires), detail.grantedAreas)
    }

    @Test
    fun `una concesion sobre un area que el rol ya permite no la marca como temporal`() {
        val own = nutritionGrant.copy(ownerArea = PSYCHOLOGY)

        val detail = assemble(mapOf(PSYCHOLOGY to own))

        assertTrue(detail.grantedAreas.isEmpty())
    }

    @Test
    fun `la concesion no concede ninguna accion de escritura`() {
        val detail = assemble(mapOf(NUTRITION to nutritionGrant))

        val caps = AreaCapabilitiesResolver.resolve(psychologist, NUTRITION, detail.activeAssignment(NUTRITION), detail.viewableAreas)

        assertTrue(caps.canViewDetail)
        assertFalse(caps.canAssign)
        assertFalse(caps.canSchedule)
        assertFalse(caps.canRegisterEncounter)
        assertFalse(caps.canRequestProfessionalChange)
        assertFalse(caps.canRequestAccess)
    }

    @Test
    fun `una solicitud propia pendiente solo se muestra mientras el area sigue restringida`() {
        val restricted = PatientDetailAssembler.assemble(
            viewer = psychologist,
            patient = domainPatient(id = "patient-1"),
            contacts = emptyList(),
            assignments = listOf(nutritionAssignment),
            appointments = emptyList(),
            encounters = listOf(nutritionEncounter),
            assessments = emptyList(),
            pendingAccessRequests = mapOf(NUTRITION to "r-9"),
        )
        val granted = PatientDetailAssembler.assemble(
            viewer = psychologist,
            patient = domainPatient(id = "patient-1"),
            contacts = emptyList(),
            assignments = listOf(nutritionAssignment),
            appointments = emptyList(),
            encounters = listOf(nutritionEncounter),
            assessments = emptyList(),
            grants = mapOf(NUTRITION to nutritionGrant),
            pendingAccessRequests = mapOf(NUTRITION to "r-9"),
        )

        assertEquals(mapOf(NUTRITION to "r-9"), restricted.pendingAccessRequests)
        assertTrue(granted.pendingAccessRequests.isEmpty())
    }

    @Test
    fun `un cambio pendiente de un area que no se ve no se anuncia`() {
        val detail = PatientDetailAssembler.assemble(
            viewer = psychologist,
            patient = domainPatient(id = "patient-1"),
            contacts = emptyList(),
            assignments = listOf(nutritionAssignment),
            appointments = emptyList(),
            encounters = listOf(nutritionEncounter),
            assessments = emptyList(),
            pendingChangeRequests = mapOf(NUTRITION to "o-1", PSYCHOLOGY to "o-2"),
        )

        assertEquals(mapOf(PSYCHOLOGY to "o-2"), detail.pendingChangeRequests)
    }

    @Test
    fun `las capacidades de solicitud dependen del rol y no de la concesion`() {
        val requester = AreaCapabilitiesResolver.resolve(psychologist, NUTRITION, nutritionAssignment.copy(professionalId = "nut"), setOf(PSYCHOLOGY))
        val assignedProfessional = AreaCapabilitiesResolver.resolve(nutritionist, NUTRITION, nutritionAssignment, setOf(NUTRITION))

        assertTrue("pide acceso a un area que no ve", requester.canRequestAccess)
        assertFalse("y no pide el cambio del profesional de otra area", requester.canRequestProfessionalChange)
        assertTrue("el profesional asignado pide el cambio", assignedProfessional.canRequestProfessionalChange)
        assertFalse("no pide acceso a lo que ya ve", assignedProfessional.canRequestAccess)
    }
}
