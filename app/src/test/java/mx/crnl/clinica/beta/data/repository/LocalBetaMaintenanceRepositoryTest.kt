package mx.crnl.clinica.beta.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.core.demo.DemoSeed
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.model.AccessRequestDraft
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.PatientDraft
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.testing.BetaAccounts
import mx.crnl.clinica.beta.testing.RepositoryTest
import mx.crnl.clinica.beta.testing.SeedIds
import mx.crnl.clinica.beta.testing.failure
import mx.crnl.clinica.beta.testing.success
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalBetaMaintenanceRepositoryTest : RepositoryTest() {
    private fun localChanges() = scalar("SELECT COUNT(*) FROM patients") + scalar("SELECT COUNT(*) FROM interarea_access_requests")

    private suspend fun makeLocalChanges() {
        patients.createPatient(
            PatientDraft("Camila", "Ríos", "Soto", java.time.LocalDate.of(1990, 1, 15), null, Sex.FEMALE, null, PopulationType.GENERAL_PUBLIC, "8100009999", null),
            SeedIds.HECTOR,
        )
        val request = accessRequests.create(AccessRequestDraft(SeedIds.FERNANDA, ClinicalArea.NUTRITION, "Necesito consultar el seguimiento nutricional"), SeedIds.MARIANA).success()
        accessRequests.approve(request.requestId, 7, SeedIds.HECTOR).success()
        setUserStatus(SeedIds.PAOLA, "SUSPENDED")
    }

    @Test
    fun `solo administracion clinica restablece y los demas reciben un rechazo y no cambia nada`() = runTest {
        makeLocalChanges()
        val before = localChanges()
        val system = insertSystemAdmin()

        listOf(SeedIds.MARIANA, SeedIds.CLAUDIA, system.userId, "fantasma").forEach { actor ->
            maintenance.resetBetaData(actor).failure<OperationError.NotAuthorized>()
        }

        assertEquals(before, localChanges())
        assertEquals(9, scalar("SELECT COUNT(*) FROM patients"))
    }

    @Test
    fun `una cuenta de administracion clinica suspendida tampoco restablece`() = runTest {
        setUserStatus(SeedIds.HECTOR, "SUSPENDED")

        maintenance.resetBetaData(SeedIds.HECTOR).failure<OperationError.NotAuthorized>()
    }

    @Test
    fun `restablecer elimina los cambios locales y vuelve al conjunto ficticio inicial`() = runTest {
        makeLocalChanges()
        assertEquals(9, scalar("SELECT COUNT(*) FROM patients"))

        val summary = maintenance.resetBetaData(SeedIds.HECTOR).success()

        assertEquals(10, summary.users)
        assertEquals(8, summary.patients)
        assertEquals(12, summary.appointments)
        assertEquals(4, summary.assessments)
        assertEquals(0, scalar("SELECT COUNT(*) FROM patients WHERE firstName = 'Camila'"))
        assertEquals(0, scalar("SELECT COUNT(*) FROM interarea_access_requests"))
        assertEquals(0, scalar("SELECT COUNT(*) FROM access_grants"))
        assertEquals(0, scalar("SELECT COUNT(*) FROM professional_override_requests"))
        assertEquals("ACTIVE", text("SELECT status FROM demo_users WHERE userId = '${SeedIds.PAOLA}'"))
        assertEquals(12, scalar("SELECT COUNT(*) FROM professional_assignments"))
        assertEquals(6, scalar("SELECT COUNT(*) FROM clinical_encounters"))
        assertEquals(10, scalar("SELECT COUNT(*) FROM demo_credentials"))
    }

    @Test
    fun `tras restablecer las cuentas iniciales siguen entrando con la contrasena de la Beta`() = runTest {
        maintenance.resetBetaData(SeedIds.HECTOR).success()

        assertTrue(auth.signIn(BetaAccounts.PSYCHOLOGIST_EMAIL, BetaAccounts.PASSWORD) is mx.crnl.clinica.beta.domain.repository.SignInResult.Success)
    }

    @Test
    fun `restablecer cierra la sesion y deja la version de datos aplicada`() = runTest {
        auth.signIn(BetaAccounts.ADMIN_EMAIL, BetaAccounts.PASSWORD)
        assertEquals(SeedIds.HECTOR, session.sessionUserId.first())

        maintenance.resetBetaData(SeedIds.HECTOR).success()

        assertNull(session.sessionUserId.first())
        assertEquals(DemoSeed.SUPPORTED_VERSION, session.appliedSeedVersion())
    }

    @Test
    fun `la base queda integra, en su version y sin datos huerfanos`() = runTest {
        makeLocalChanges()

        maintenance.resetBetaData(SeedIds.HECTOR).success()

        assertEquals(0, foreignKeyViolations())
        assertEquals("ok", text("PRAGMA integrity_check"))
        assertEquals(3L, scalar("PRAGMA user_version"))
    }

    @Test
    fun `restablecer deja constancia y repetirlo no duplica el conjunto inicial`() = runTest {
        maintenance.resetBetaData(SeedIds.HECTOR).success()
        val patientsAfterFirst = scalar("SELECT COUNT(*) FROM patients")

        maintenance.resetBetaData(SeedIds.HECTOR).success()

        assertEquals(patientsAfterFirst, scalar("SELECT COUNT(*) FROM patients"))
        assertEquals(8, patientsAfterFirst.toInt())
        val resets = auditRows("BETA_DATA_RESET")
        assertEquals(1, resets.size) // la constancia previa se borra junto con la base: queda la del último restablecimiento
        assertEquals(SeedIds.HECTOR, resets.single().actor)
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_entries WHERE action = 'DEMO_SEED_APPLIED'"))
    }

    @Test
    fun `no toca archivos fuera de la base de la Beta`() = runTest {
        val marker = File(context.filesDir, "no-tocar.txt").apply { writeText("externo") }

        maintenance.resetBetaData(SeedIds.HECTOR).success()

        assertTrue(marker.exists())
        assertEquals("externo", marker.readText())
        assertTrue(marker.delete())
    }

    @Test
    fun `el vaciado cubre todas las tablas del esquema sin dejar ninguna fuera`() = runTest {
        val tables = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'",
            null,
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        assertTrue("el esquema tiene datos ficticios que vaciar", tables.any { count(it) > 0 })

        db.maintenanceDao().deleteAllRows()

        tables.forEach { table -> assertEquals("la tabla $table debe quedar vacía", 0, count(table)) }
    }

    @Test
    fun `si la recarga falla los datos quedan como estaban y la version aplicada se conserva`() = runTest {
        makeLocalChanges()
        session.recordAppliedSeedVersion(DemoSeed.SUPPORTED_VERSION)
        val failing = LocalBetaMaintenanceRepository(db, session, { throw java.io.IOException("recarga interrumpida") }, audit)
        val patientsBefore = scalar("SELECT COUNT(*) FROM patients")

        val outcome = runCatching { failing.resetBetaData(SeedIds.HECTOR) }

        assertTrue(outcome.isFailure)
        assertEquals("la transacción se deshace: nada se pierde", patientsBefore, scalar("SELECT COUNT(*) FROM patients"))
        assertEquals(1, scalar("SELECT COUNT(*) FROM access_grants"))
        assertEquals(DemoSeed.SUPPORTED_VERSION, session.appliedSeedVersion())
    }

    @Test
    fun `nadie observa la base vacia durante el restablecimiento`() = runTest {
        val seen = mutableListOf<Int>()
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            db.userDao().observeAll().collect { seen += it.size }
        }
        kotlinx.coroutines.delay(300)

        maintenance.resetBetaData(SeedIds.HECTOR).success()
        kotlinx.coroutines.delay(300)
        job.cancel()

        assertTrue("las cuentas nunca desaparecieron del flujo", seen.none { it == 0 })
    }
}
