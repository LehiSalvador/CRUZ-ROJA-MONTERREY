package mx.crnl.clinica.beta.testing

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import mx.crnl.clinica.beta.core.demo.AssetSeedFileReader
import mx.crnl.clinica.beta.core.demo.DemoDataInitializer
import mx.crnl.clinica.beta.core.demo.DemoSeedLoader
import mx.crnl.clinica.beta.core.security.PasswordHasher
import mx.crnl.clinica.beta.data.repository.AuditRecorder
import mx.crnl.clinica.beta.data.repository.LocalAppointmentRepository
import mx.crnl.clinica.beta.data.repository.LocalAuthRepository
import mx.crnl.clinica.beta.data.repository.LocalHomeRepository
import mx.crnl.clinica.beta.data.repository.LocalPatientRepository
import org.junit.Before

/** Repositorios reales sobre Room en memoria, con el conjunto ficticio completo ya cargado y reloj fijo. */
abstract class RepositoryTest : DatabaseTest() {
    protected val clock = fixedClock()
    protected val session = FakeSessionRepository()

    // Costo mínimo: las pruebas verifican la lógica de acceso, no la fortaleza de la derivación.
    protected val hasher = PasswordHasher(defaultIterations = TEST_ITERATIONS)

    private val idCounter = AtomicInteger()
    protected val newId: () -> String = { "generated-${idCounter.incrementAndGet()}" }

    protected lateinit var audit: AuditRecorder
    protected lateinit var patients: LocalPatientRepository
    protected lateinit var appointments: LocalAppointmentRepository
    protected lateinit var auth: LocalAuthRepository
    protected lateinit var home: LocalHomeRepository

    @Before
    fun seedAndAssemble() {
        runBlocking {
            DemoDataInitializer(
                seedDao = db.seedDao(),
                sessionRepository = FakeSessionRepository(),
                loader = DemoSeedLoader(AssetSeedFileReader(context.assets)),
                clock = clock,
            ).initialize()
        }
        audit = AuditRecorder(db.auditDao(), clock, newId)
        patients = LocalPatientRepository(db, audit, clock, newId)
        appointments = LocalAppointmentRepository(db.appointmentDao())
        auth = LocalAuthRepository(db, session, hasher, audit, clock, newId)
        home = LocalHomeRepository(db, clock)
    }

    /** Acciones registradas en la bitácora, de la más antigua a la más reciente, sin la carga inicial. */
    protected fun auditedActions(): List<String> =
        db.query("SELECT action FROM audit_entries WHERE action <> 'DEMO_SEED_APPLIED' ORDER BY rowid", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    protected companion object {
        const val TEST_ITERATIONS = 1_000
        const val USER_MARIANA = "a0000000-0000-4000-8000-000000000003"
        const val USER_COORDINATOR = "a0000000-0000-4000-8000-000000000001"
        const val USER_ADMIN = "a0000000-0000-4000-8000-000000000002"
    }
}
