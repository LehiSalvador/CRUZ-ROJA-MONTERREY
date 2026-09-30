package mx.crnl.clinica.beta.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.model.AccessRequestDraft
import mx.crnl.clinica.beta.domain.model.AuditAction
import mx.crnl.clinica.beta.domain.model.AuditCategory
import mx.crnl.clinica.beta.domain.model.AuditFilter
import mx.crnl.clinica.beta.domain.model.AuditPeriod
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.testing.BetaAccounts
import mx.crnl.clinica.beta.testing.RepositoryTest
import mx.crnl.clinica.beta.testing.SeedIds
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.TestZone
import mx.crnl.clinica.beta.testing.success
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAuditRepositoryTest : RepositoryTest() {
    private suspend fun entries(viewer: String, filter: AuditFilter = AuditFilter()) =
        auditRepo.observeEntries(filter, account(viewer)).first()

    @Test
    fun `administracion clinica consulta la bitacora, con la mas reciente primero`() = runTest {
        auth.signIn(BetaAccounts.PSYCHOLOGIST_EMAIL, BetaAccounts.PASSWORD)
        auth.signOut()

        val list = entries(SeedIds.HECTOR)

        assertEquals("LOGOUT", list.first().actionCode)
        assertEquals("Mariana Elizondo Cantú", list.first().actorName)
        assertEquals(listOf("LOGOUT", "LOGIN"), list.take(2).map { it.actionCode })
        assertTrue(list.zipWithNext().all { (newer, older) -> !newer.occurredAt.isBefore(older.occurredAt) })
    }

    @Test
    fun `el administrador del sistema tambien la consulta y no ve un dato clinico porque la bitacora no lo guarda`() = runTest {
        auth.signIn(BetaAccounts.PSYCHOLOGIST_EMAIL, BetaAccounts.PASSWORD)

        val list = entries(insertSystemAdmin().userId)

        assertTrue(list.isNotEmpty())
        // Un registro no lleva identificadores de entidad, metadata ni nombres de pacientes: solo quién, qué y sobre qué tipo.
        val fields = mx.crnl.clinica.beta.domain.model.AuditRecord::class.java.declaredFields.map { it.name }.toSet()
        assertFalse("metadata" in fields || "entityId" in fields || "patientId" in fields)
    }

    @Test
    fun `un profesional, coordinacion o una cuenta suspendida no reciben nada`() = runTest {
        assertTrue(entries(SeedIds.MARIANA).isEmpty())
        assertTrue(entries(SeedIds.CLAUDIA).isEmpty())
        setUserStatus(SeedIds.HECTOR, "SUSPENDED")
        assertTrue(entries(SeedIds.HECTOR).isEmpty())
    }

    @Test
    fun `una accion desconocida cae en Sistema y la carga inicial se reconoce`() = runTest {
        val seed = entries(SeedIds.HECTOR).single { it.actionCode == "DEMO_SEED_APPLIED" }

        assertNull(seed.action)
        assertTrue(AuditCategory.SYSTEM.accepts(seed.actionCode))
        assertTrue(entries(SeedIds.HECTOR, AuditFilter(AuditCategory.SYSTEM)).any { it.actionCode == "DEMO_SEED_APPLIED" })
    }

    @Test
    fun `filtrar por categoria deja solo las acciones de esa categoria`() = runTest {
        auth.signIn(BetaAccounts.PSYCHOLOGIST_EMAIL, BetaAccounts.PASSWORD)
        val request = accessRequests.create(AccessRequestDraft(SeedIds.FERNANDA, ClinicalArea.NUTRITION, "Necesito consultar el seguimiento nutricional"), SeedIds.MARIANA).success()
        accessRequests.approve(request.requestId, 7, SeedIds.HECTOR).success()

        val sessions = entries(SeedIds.HECTOR, AuditFilter(AuditCategory.SESSIONS))
        val requests = entries(SeedIds.HECTOR, AuditFilter(AuditCategory.REQUESTS))

        assertEquals(listOf("LOGIN"), sessions.map { it.actionCode })
        assertEquals(setOf("INTERAREA_REQUEST_CREATED", "INTERAREA_REQUEST_APPROVED"), requests.map { it.actionCode }.toSet())
        assertEquals(AuditAction.INTERAREA_REQUEST_APPROVED, requests.first().action)
        assertEquals(ClinicalArea.NUTRITION, requests.first().area)
    }

    @Test
    fun `filtrar por periodo usa el reloj y la zona de Monterrey`() = runTest {
        auth.signIn(BetaAccounts.PSYCHOLOGIST_EMAIL, BetaAccounts.PASSWORD)
        val tomorrow = LocalAuditRepository(db, Clock.fixed(TestNow.toInstant().plus(Duration.ofDays(1)), TestZone))
        val nextWeek = LocalAuditRepository(db, Clock.fixed(TestNow.toInstant().plus(Duration.ofDays(8)), TestZone))
        val admin = account(SeedIds.HECTOR)

        assertTrue(auditRepo.observeEntries(AuditFilter(period = AuditPeriod.TODAY), admin).first().any { it.actionCode == "LOGIN" })
        assertTrue(tomorrow.observeEntries(AuditFilter(period = AuditPeriod.TODAY), admin).first().isEmpty())
        assertTrue(tomorrow.observeEntries(AuditFilter(period = AuditPeriod.LAST_7_DAYS), admin).first().any { it.actionCode == "LOGIN" })
        assertTrue(nextWeek.observeEntries(AuditFilter(period = AuditPeriod.LAST_7_DAYS), admin).first().isEmpty())
        assertTrue(nextWeek.observeEntries(AuditFilter(period = AuditPeriod.ALL), admin).first().any { it.actionCode == "LOGIN" })
    }

    @Test
    fun `la consulta se limita a las entradas mas recientes`() = runTest {
        val sql = db.openHelper.writableDatabase
        repeat(LocalAuditRepository.MAX_ENTRIES + 20) { index ->
            sql.execSQL("INSERT INTO audit_entries VALUES ('bulk-$index', NULL, 'LOGIN', 'USER', NULL, NULL, NULL, ${TestNow.toInstant().toEpochMilli() + index}, 'SUCCESS', NULL)")
        }

        assertEquals(LocalAuditRepository.MAX_ENTRIES, entries(SeedIds.HECTOR).size)
    }

    @Test
    fun `la bitacora es solo de lectura desde la aplicacion`() {
        val writers = mx.crnl.clinica.beta.domain.repository.AuditRepository::class.java.methods
            .map { it.name }
            .filter { name -> listOf("delete", "update", "edit", "export", "clear", "remove").any { name.startsWith(it) } }

        assertEquals(emptyList<String>(), writers)
    }
}
