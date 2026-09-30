package mx.crnl.clinica.beta.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditCategoryTest {
    @Test
    fun `cada accion auditada pertenece exactamente a una categoria`() {
        AuditAction.entries.forEach { action ->
            val owners = AuditCategory.entries.filter { it.accepts(action.name) }
            assertEquals("${action.name} debe tener una sola categoria", 1, owners.size)
        }
    }

    @Test
    fun `una accion que esta version no conoce cae en Sistema`() {
        assertTrue(AuditCategory.SYSTEM.accepts("DEMO_SEED_APPLIED"))
        assertFalse(AuditCategory.REQUESTS.accepts("DEMO_SEED_APPLIED"))
    }

    @Test
    fun `las categorias agrupan lo esperado`() {
        assertTrue(AuditCategory.SESSIONS.accepts("LOGIN"))
        assertTrue(AuditCategory.REQUESTS.accepts("USER_APPROVED"))
        assertTrue(AuditCategory.REQUESTS.accepts("OVERRIDE_REQUEST_APPROVED"))
        assertTrue(AuditCategory.CARE.accepts("APPOINTMENT_CREATED"))
        assertTrue(AuditCategory.CARE.accepts("SUPERVISED_PREVIEW_OPENED"))
        assertTrue(AuditCategory.PATIENTS.accepts("PATIENT_VIEWED"))
    }

    @Test
    fun `un registro reconoce su accion o la deja nula`() {
        val known = AuditRecord("a1", Instant.EPOCH, null, "LOGIN", "USER", null, true)
        val unknown = AuditRecord("a2", Instant.EPOCH, null, "ALGO_NUEVO", "USER", null, true)

        assertEquals(AuditAction.LOGIN, known.action)
        assertNull(unknown.action)
    }
}
