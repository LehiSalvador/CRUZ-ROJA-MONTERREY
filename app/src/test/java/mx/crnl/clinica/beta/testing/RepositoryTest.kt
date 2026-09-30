package mx.crnl.clinica.beta.testing

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import mx.crnl.clinica.beta.core.demo.AssetSeedFileReader
import mx.crnl.clinica.beta.core.demo.DemoDataInitializer
import mx.crnl.clinica.beta.core.demo.DemoSeedLoader
import mx.crnl.clinica.beta.core.security.PasswordHasher
import mx.crnl.clinica.beta.data.local.mapper.toDomain
import mx.crnl.clinica.beta.data.repository.AuditRecorder
import mx.crnl.clinica.beta.data.repository.LocalAppointmentRepository
import mx.crnl.clinica.beta.data.repository.LocalAuthRepository
import mx.crnl.clinica.beta.data.repository.LocalEncounterRepository
import mx.crnl.clinica.beta.data.repository.LocalHomeRepository
import mx.crnl.clinica.beta.data.repository.LocalPatientRepository
import mx.crnl.clinica.beta.data.repository.LocalProfessionalAssignmentRepository
import mx.crnl.clinica.beta.domain.model.UserAccount
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
    protected lateinit var assignments: LocalProfessionalAssignmentRepository
    protected lateinit var encounters: LocalEncounterRepository
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
        appointments = LocalAppointmentRepository(db, audit, clock, newId)
        assignments = LocalProfessionalAssignmentRepository(db, audit, clock, newId)
        encounters = LocalEncounterRepository(db, audit, clock, newId)
        auth = LocalAuthRepository(db, session, hasher, audit, clock, newId)
        home = LocalHomeRepository(db, clock)
    }

    /** La cuenta tal como está hoy en la base, como la vería quien inició sesión. */
    protected fun account(userId: String): UserAccount = runBlocking { db.userDao().getById(userId)!!.toDomain() }

    /** Inserta la cuenta técnica (administrador del sistema), que el conjunto ficticio no incluye. */
    protected fun insertSystemAdmin(): UserAccount {
        runBlocking { db.userDao().insert(userEntity(id = SeedIds.SYSTEM_ADMIN, role = "SYSTEM_ADMIN", area = null, firstName = "Sistema", paternalSurname = "Técnico")) }
        return account(SeedIds.SYSTEM_ADMIN)
    }

    protected fun setUserStatus(userId: String, status: String) {
        db.openHelper.writableDatabase.execSQL("UPDATE demo_users SET status = ? WHERE userId = ?", arrayOf(status, userId))
    }

    protected fun setPatientStatus(patientId: String, status: String) {
        db.openHelper.writableDatabase.execSQL("UPDATE patients SET status = ? WHERE patientId = ?", arrayOf(status, patientId))
    }

    /** Acciones registradas en la bitácora, de la más antigua a la más reciente, sin la carga inicial. */
    protected fun auditedActions(): List<String> =
        db.query("SELECT action FROM audit_entries WHERE action <> 'DEMO_SEED_APPLIED' ORDER BY rowid", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    /** Entradas de la bitácora de una acción: (actor, entidad, id de entidad, paciente, área, metadata). */
    protected fun auditRows(action: String): List<AuditRow> =
        db.query(
            "SELECT actorUserId, entityType, entityId, patientId, areaCode, metadata FROM audit_entries WHERE action = ? ORDER BY rowid",
            arrayOf(action),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        AuditRow(
                            actor = cursor.getString(0),
                            entityType = cursor.getString(1),
                            entityId = cursor.getString(2),
                            patientId = cursor.getString(3),
                            areaCode = cursor.getString(4),
                            metadata = cursor.getString(5),
                        ),
                    )
                }
            }
        }

    protected fun scalar(sql: String): Long = db.query(sql, null).use {
        it.moveToFirst()
        it.getLong(0)
    }

    protected fun text(sql: String): String? = db.query(sql, null).use {
        it.moveToFirst()
        if (it.isNull(0)) null else it.getString(0)
    }

    protected data class AuditRow(
        val actor: String?,
        val entityType: String,
        val entityId: String?,
        val patientId: String?,
        val areaCode: String?,
        val metadata: String?,
    )

    protected companion object {
        const val TEST_ITERATIONS = 1_000
        const val USER_MARIANA = "a0000000-0000-4000-8000-000000000003"
        const val USER_COORDINATOR = "a0000000-0000-4000-8000-000000000001"
        const val USER_ADMIN = "a0000000-0000-4000-8000-000000000002"
    }
}
