package mx.crnl.clinica.beta.core.demo

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.testing.DatabaseTest
import mx.crnl.clinica.beta.testing.FakeSessionRepository
import mx.crnl.clinica.beta.testing.TestZone
import mx.crnl.clinica.beta.testing.assertFailsWithType
import mx.crnl.clinica.beta.testing.fixedClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoDataInitializerTest : DatabaseTest() {
    private val clock = fixedClock()

    private fun assetReader() = AssetSeedFileReader(context.assets)

    private fun initializer(
        session: FakeSessionRepository = FakeSessionRepository(),
        reader: SeedFileReader = assetReader(),
    ) = DemoDataInitializer(db.seedDao(), session, DemoSeedLoader(reader), clock)

    private val tables = listOf(
        "demo_users", "patients", "patient_contacts", "professional_assignments", "appointments",
        "clinical_encounters", "assessments", "assessment_results", "audit_entries",
    )

    private fun rowCounts() = tables.associateWith { count(it) }

    @Test
    fun `carga el conjunto completo en el primer arranque`() = runTest {
        val seed = DemoSeedLoader(assetReader()).load()
        val session = FakeSessionRepository()

        val outcome = initializer(session).initialize()

        assertTrue(outcome is SeedOutcome.Applied)
        val counts = (outcome as SeedOutcome.Applied).counts
        assertEquals(seed.users.size, counts.users)
        assertEquals(seed.patients.size, counts.patients)
        assertEquals(seed.patients.sumOf { it.contacts.size }, counts.contacts)
        assertEquals(seed.assignments.size, counts.assignments)
        assertEquals(seed.appointments.size, counts.appointments)
        assertEquals(seed.encounters.size, counts.encounters)
        assertEquals(seed.assessments.size, counts.assessments)
        assertEquals(seed.assessments.count { it.result != null }, counts.results)
        assertEquals(1, counts.auditEntries)

        assertEquals(seed.users.size, count("demo_users"))
        assertEquals(seed.patients.size, count("patients"))
        assertEquals(seed.appointments.size, count("appointments"))
        assertEquals(DemoSeed.SUPPORTED_VERSION, session.appliedSeedVersion())
        assertEquals(0, foreignKeyViolations())
    }

    @Test
    fun `una segunda apertura no vuelve a cargar nada`() = runTest {
        val session = FakeSessionRepository()
        val initializer = initializer(session)
        initializer.initialize()
        val before = rowCounts()

        assertEquals(SeedOutcome.AlreadyApplied, initializer.initialize())

        assertEquals(before, rowCounts())
    }

    @Test
    fun `aun sin la version registrada las llaves determinísticas impiden duplicar`() = runTest {
        initializer(FakeSessionRepository()).initialize()
        val before = rowCounts()

        // Simula perder el archivo de preferencias: la carga se repite sobre una base ya poblada.
        val outcome = initializer(FakeSessionRepository()).initialize()

        assertEquals(0, (outcome as SeedOutcome.Applied).counts.total)
        assertEquals(before, rowCounts())
    }

    @Test
    fun `registra una sola entrada de auditoria de la carga`() = runTest {
        initializer().initialize()
        initializer(FakeSessionRepository()).initialize()

        db.query("SELECT action, result, actorUserId FROM audit_entries", null).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(DemoSeedMapper.SEED_AUDIT_ACTION, cursor.getString(0))
            assertEquals("SUCCESS", cursor.getString(1))
            assertTrue(cursor.isNull(2))
        }
    }

    @Test
    fun `resuelve los dias relativos contra el reloj y la zona de la clinica`() = runTest {
        initializer().initialize()

        // e0000000-…-0007 está sembrada como «hoy, 10:00, 50 minutos».
        val appointment = db.appointmentDao().getById("e0000000-0000-4000-8000-000000000007")
        assertNotNull(appointment)
        val expectedStart = ZonedDateTime.of(2026, 9, 29, 10, 0, 0, 0, TestZone).toInstant().toEpochMilli()
        assertEquals(expectedStart, appointment!!.startDateTime)
        assertEquals(expectedStart + 50 * 60_000L, appointment.endDateTime)
    }

    @Test
    fun `un conjunto invalido no se carga ni marca la version como aplicada`() = runTest {
        val session = FakeSessionRepository()
        val corrupted = SeedFileReader { name ->
            val text = assetReader().read(name)
            if (name == DemoSeed.PATIENTS_FILE) text.replace("CRNL-000001", "FOLIO-MAL") else text
        }

        val failure = assertFailsWithType<SeedException> { initializer(session, corrupted).initialize() }

        assertTrue(failure.message!!.contains("no cumple el formato"))
        assertEquals(tables.associateWith { 0 }, rowCounts())
        assertEquals(0, session.appliedSeedVersion())
    }

    @Test
    fun `un JSON malformado se reporta con el archivo de origen`() = runTest {
        val broken = SeedFileReader { name ->
            if (name == DemoSeed.APPOINTMENTS_FILE) "{ \"meta\": " else assetReader().read(name)
        }

        val failure = assertFailsWithType<SeedException> { initializer(reader = broken).initialize() }

        assertTrue(failure.message!!.startsWith(DemoSeed.APPOINTMENTS_FILE))
        assertEquals(tables.associateWith { 0 }, rowCounts())
    }
}
