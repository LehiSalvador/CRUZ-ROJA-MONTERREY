package mx.crnl.clinica.beta.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.common.EntityKind
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.model.AssignmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.testing.RepositoryTest
import mx.crnl.clinica.beta.testing.SeedIds
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.failure
import mx.crnl.clinica.beta.testing.observeAround
import mx.crnl.clinica.beta.testing.success
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalProfessionalAssignmentRepositoryTest : RepositoryTest() {
    // Andrés es el paciente ficticio con la asignación cerrada y sin vigente, pero está inactivo: aquí se activa para poder asignarlo.
    @Before
    fun activateAndres() = setPatientStatus(SeedIds.ANDRES, "ACTIVE")

    private fun assignmentCount() = scalar("SELECT COUNT(*) FROM professional_assignments").toInt()

    private suspend fun assign(
        patientId: String = SeedIds.ANDRES,
        area: ClinicalArea = ClinicalArea.PSYCHOLOGY,
        professionalId: String = SeedIds.RODRIGO,
        reason: String? = null,
        actor: String = SeedIds.CLAUDIA,
    ) = assignments.createInitialAssignment(patientId, area, professionalId, reason, actor)

    // ---------------------------------------------------------------- crear la asignación inicial

    @Test
    fun `coordinacion asigna un profesional del area a un paciente sin asignacion vigente`() = runTest {
        val created = assign(reason = "  Primera vez en el programa ").success()

        assertEquals(ClinicalArea.PSYCHOLOGY, created.area)
        assertEquals(SeedIds.RODRIGO, created.professionalId)
        assertEquals("Rodrigo Villarreal", created.professionalName)
        assertEquals(AssignmentStatus.ACTIVE, created.status)
        assertEquals(TestNow.toInstant(), created.since)
        assertNull(created.until)
        assertEquals("Primera vez en el programa", created.reason)
        assertEquals("Claudia Benavides Rangel", created.assignedByName)
        assertEquals(SeedIds.CLAUDIA, text("SELECT assignedBy FROM professional_assignments WHERE assignmentId = '${created.assignmentId}'"))
    }

    @Test
    fun `la razon es opcional`() = runTest {
        assertNull(assign(reason = null).success().reason)
        assertNull(assign(SeedIds.ANA, ClinicalArea.NUTRITION, SeedIds.PAOLA, reason = "   ", actor = SeedIds.HECTOR).success().reason)
    }

    @Test
    fun `asignar deja constancia de auditoria con el profesional y sin la razon`() = runTest {
        assign(reason = "Motivo administrativo").success()

        val row = auditRows("ASSIGNMENT_CREATED").single()
        assertEquals(SeedIds.CLAUDIA, row.actor)
        assertEquals("ASSIGNMENT", row.entityType)
        assertEquals(SeedIds.ANDRES, row.patientId)
        assertEquals("PSYCHOLOGY", row.areaCode)
        assertTrue(row.metadata!!.contains(SeedIds.RODRIGO))
        assertFalse(row.metadata!!.contains("Motivo"))
    }

    @Test
    fun `el historial se conserva y la asignacion cerrada previa sigue ahi junto a la nueva`() = runTest {
        val before = assignmentCount()
        val closedBefore = text("SELECT status FROM professional_assignments WHERE patientId = '${SeedIds.ANDRES}'")
        assertEquals("ENDED", closedBefore)

        assign().success()

        assertEquals(before + 1, assignmentCount())
        val history = assignments.observeHistory(SeedIds.ANDRES, ClinicalArea.PSYCHOLOGY, account(SeedIds.CLAUDIA)).first()
        assertEquals(listOf(AssignmentStatus.ACTIVE, AssignmentStatus.ENDED), history.map { it.status })
        assertNotNull(history.last().until)
        assertEquals(SeedIds.MARIANA, history.last().professionalId)
    }

    @Test
    fun `administracion clinica asigna en cualquier area`() = runTest {
        assign(SeedIds.ANA, ClinicalArea.GENERAL_MEDICINE, SeedIds.EDUARDO, actor = SeedIds.HECTOR).success()
    }

    @Test
    fun `un profesional no puede asignarse pacientes, ni a si mismo ni a otro`() = runTest {
        assign(SeedIds.ANA, ClinicalArea.PSYCHOLOGY, SeedIds.MARIANA, actor = SeedIds.MARIANA).failure<OperationError.NotAuthorized>()
        assign(SeedIds.ANDRES, ClinicalArea.PSYCHOLOGY, SeedIds.MARIANA, actor = SeedIds.MARIANA).failure<OperationError.NotAuthorized>()
        assertEquals(12, assignmentCount())
    }

    @Test
    fun `coordinacion no asigna fuera de su area`() = runTest {
        assign(SeedIds.ANA, ClinicalArea.NUTRITION, SeedIds.PAOLA, actor = SeedIds.CLAUDIA).failure<OperationError.NotAuthorized>()
    }

    @Test
    fun `el administrador del sistema no asigna por ser administrador`() = runTest {
        assign(actor = insertSystemAdmin().userId).failure<OperationError.NotAuthorized>()
    }

    @Test
    fun `una cuenta que no esta activa no asigna`() = runTest {
        setUserStatus(SeedIds.CLAUDIA, "SUSPENDED")
        assign().failure<OperationError.NotAuthorized>()
    }

    @Test
    fun `no se asigna a un profesional de otra area`() = runTest {
        assign(professionalId = SeedIds.PAOLA).failure<OperationError.ProfessionalNotAvailable>()
    }

    @Test
    fun `no se asigna a cuentas pendientes, suspendidas, rechazadas o inactivas`() = runTest {
        assign(SeedIds.ANA, ClinicalArea.NUTRITION, SeedIds.VALERIA, actor = SeedIds.HECTOR).failure<OperationError.ProfessionalNotAvailable>()
        assign(SeedIds.ANA, ClinicalArea.GENERAL_MEDICINE, SeedIds.GERARDO, actor = SeedIds.HECTOR).failure<OperationError.ProfessionalNotAvailable>()
        assign(professionalId = SeedIds.IVAN).failure<OperationError.ProfessionalNotAvailable>()
        assign(professionalId = SeedIds.SOFIA).failure<OperationError.ProfessionalNotAvailable>()
    }

    @Test
    fun `una cuenta que no es de profesional no se asigna como tal`() = runTest {
        // Claudia coordina Psicología pero no es «profesional».
        assign(professionalId = SeedIds.CLAUDIA).failure<OperationError.ProfessionalNotAvailable>()
    }

    @Test
    fun `un profesional o un paciente inexistentes se reportan`() = runTest {
        assign(professionalId = "fantasma").failure<OperationError.NotFound>().also { assertEquals(EntityKind.PROFESSIONAL, it.entity) }
        assign(patientId = "fantasma").failure<OperationError.NotFound>().also { assertEquals(EntityKind.PATIENT, it.entity) }
    }

    @Test
    fun `un paciente que no esta activo no recibe asignaciones`() = runTest {
        setPatientStatus(SeedIds.ANDRES, "ARCHIVED")

        assign().failure<OperationError.PatientNotActive>()
    }

    @Test
    fun `solo puede haber una asignacion vigente por paciente y area`() = runTest {
        // Ana ya tiene a Mariana en Psicología.
        assign(SeedIds.ANA, ClinicalArea.PSYCHOLOGY, SeedIds.RODRIGO).failure<OperationError.AssignmentAlreadyActive>()

        assign().success()
        assign(professionalId = SeedIds.MARIANA).failure<OperationError.AssignmentAlreadyActive>()
        assertEquals(1, scalar("SELECT COUNT(*) FROM professional_assignments WHERE patientId = '${SeedIds.ANDRES}' AND status = 'ACTIVE'").toInt())
    }

    @Test
    fun `cambiar de profesional no esta habilitado y la asignacion vigente no se cierra ni se reemplaza`() = runTest {
        val before = assignmentCount()

        assign(SeedIds.ANA, ClinicalArea.PSYCHOLOGY, SeedIds.RODRIGO).failure<OperationError.AssignmentAlreadyActive>()

        assertEquals(before, assignmentCount())
        assertEquals("ACTIVE", text("SELECT status FROM professional_assignments WHERE patientId = '${SeedIds.ANA}'"))
        assertEquals(SeedIds.MARIANA, text("SELECT professionalId FROM professional_assignments WHERE patientId = '${SeedIds.ANA}'"))
    }

    // ---------------------------------------------------------------- lecturas

    @Test
    fun `lista solo profesionales activos del area, ordenados por apellido`() = runTest {
        val psychology = assignments.listAssignableProfessionals(ClinicalArea.PSYCHOLOGY, account(SeedIds.CLAUDIA))

        // Iván (rechazado) y Sofía (inactiva) quedan fuera; Claudia es coordinadora, no profesional.
        assertEquals(listOf(SeedIds.MARIANA, SeedIds.RODRIGO), psychology.map { it.userId })
        assertEquals("00000103", psychology.first { it.userId == SeedIds.MARIANA }.professionalLicense)
        assertEquals(listOf(SeedIds.PAOLA), assignments.listAssignableProfessionals(ClinicalArea.NUTRITION, account(SeedIds.HECTOR)).map { it.userId })
        assertEquals(listOf(SeedIds.EDUARDO), assignments.listAssignableProfessionals(ClinicalArea.GENERAL_MEDICINE, account(SeedIds.HECTOR)).map { it.userId })
    }

    @Test
    fun `quien no puede asignar recibe una lista vacia`() = runTest {
        assertTrue(assignments.listAssignableProfessionals(ClinicalArea.PSYCHOLOGY, account(SeedIds.MARIANA)).isEmpty())
        assertTrue(assignments.listAssignableProfessionals(ClinicalArea.NUTRITION, account(SeedIds.CLAUDIA)).isEmpty())
        assertTrue(assignments.listAssignableProfessionals(ClinicalArea.PSYCHOLOGY, insertSystemAdmin()).isEmpty())
    }

    @Test
    fun `la asignacion vigente se consulta por paciente y area respetando la visibilidad`() = runTest {
        val active = assignments.getActiveAssignment(SeedIds.FERNANDA, ClinicalArea.NUTRITION, account(SeedIds.PAOLA))!!
        assertEquals(SeedIds.PAOLA, active.professionalId)
        assertEquals("Héctor Montemayor Salazar", active.assignedByName)

        assertNull(assignments.getActiveAssignment(SeedIds.FERNANDA, ClinicalArea.NUTRITION, account(SeedIds.CLAUDIA)))
        assertNull(assignments.getActiveAssignment(SeedIds.ANDRES, ClinicalArea.PSYCHOLOGY, account(SeedIds.CLAUDIA)))
    }

    @Test
    fun `las asignaciones vigentes del paciente se limitan a las areas visibles`() = runTest {
        val asMariana = assignments.observeActiveAssignments(SeedIds.FERNANDA, account(SeedIds.MARIANA)).first()
        val asAdmin = assignments.observeActiveAssignments(SeedIds.FERNANDA, account(SeedIds.HECTOR)).first()

        assertEquals(listOf(ClinicalArea.PSYCHOLOGY), asMariana.map { it.area })
        assertEquals(listOf(ClinicalArea.PSYCHOLOGY, ClinicalArea.NUTRITION), asAdmin.map { it.area })
    }

    @Test
    fun `el historial de un area ajena llega vacio`() = runTest {
        assertTrue(assignments.observeHistory(SeedIds.FERNANDA, ClinicalArea.NUTRITION, account(SeedIds.MARIANA)).first().isEmpty())
        assertEquals(2, assignments.observeHistory(SeedIds.FERNANDA, ClinicalArea.PSYCHOLOGY, account(SeedIds.MARIANA)).first().size)
    }

    @Test
    fun `una asignacion nueva aparece en el historial observado`() = runTest {
        val seen = assignments.observeActiveAssignments(SeedIds.ANDRES, account(SeedIds.CLAUDIA)).observeAround(
            change = { assign().success() },
            until = { it.isNotEmpty() },
        )

        assertTrue(seen.first().isEmpty())
        assertEquals(SeedIds.RODRIGO, seen.last().single().professionalId)
    }

    @Test
    fun `tras asignar no queda ninguna clave foranea rota`() = runTest {
        assign().success()

        assertEquals(0, foreignKeyViolations())
    }
}
