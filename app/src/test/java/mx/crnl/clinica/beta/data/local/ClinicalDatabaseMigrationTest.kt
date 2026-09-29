package mx.crnl.clinica.beta.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.core.database.MIGRATION_1_2
import mx.crnl.clinica.beta.core.demo.AssetSeedFileReader
import mx.crnl.clinica.beta.core.demo.DemoDataInitializer
import mx.crnl.clinica.beta.core.demo.DemoSeedLoader
import mx.crnl.clinica.beta.core.demo.SeedOutcome
import mx.crnl.clinica.beta.core.security.PasswordHasher
import mx.crnl.clinica.beta.data.repository.AuditRecorder
import mx.crnl.clinica.beta.data.repository.LocalAuthRepository
import mx.crnl.clinica.beta.domain.repository.SignInResult
import mx.crnl.clinica.beta.testing.BetaAccounts
import mx.crnl.clinica.beta.testing.FakeSessionRepository
import mx.crnl.clinica.beta.testing.assertFailsWithType
import mx.crnl.clinica.beta.testing.fixedClock
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Actualizar desde la Fase 1 (esquema v1) no puede perder datos ni exigir reinstalar. La base v1 se crea a partir
 * del esquema exportado (`schemas/…/1.json`); Room valida el resultado de la migración contra el esquema v2 al abrirla.
 */
@RunWith(AndroidJUnit4::class)
class ClinicalDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var opened: ClinicalDatabase? = null

    @After
    fun closeAndDelete() {
        opened?.close()
        context.deleteDatabase(MIGRATED)
        context.deleteDatabase(ClinicalDatabase.FILE_NAME)
    }

    private fun createPhaseOneDatabase(name: String) {
        val schemaFile = File("schemas/${ClinicalDatabase::class.java.canonicalName}/1.json")
        val schema = JSONObject(schemaFile.readText(Charsets.UTF_8)).getJSONObject("database")
        fun String.resolved(table: String) = replace("\${TABLE_NAME}", table)

        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").resolved(table))
                val indices = entity.optJSONArray("indices")
                for (position in 0 until (indices?.length() ?: 0)) {
                    db.execSQL(indices!!.getJSONObject(position).getString("createSql").resolved(table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
            db.version = 1
            db.insertPhaseOneData()
        }
    }

    private fun SQLiteDatabase.insertPhaseOneData() {
        execSQL(
            "INSERT INTO demo_users VALUES ('${BetaAccounts.PSYCHOLOGIST_ID}', 'Mariana', 'Elizondo', 'Cantú', " +
                "'${BetaAccounts.PSYCHOLOGIST_EMAIL}', 'PROFESSIONAL', 'PSYCHOLOGY', 'ACTIVE', 1000, 1000)",
        )
        execSQL(
            "INSERT INTO demo_users VALUES ('${BetaAccounts.ADMIN_ID}', 'Héctor', 'Montemayor', 'Salazar', " +
                "'${BetaAccounts.ADMIN_EMAIL}', 'CLINICAL_ADMIN', NULL, 'ACTIVE', 1000, 1000)",
        )
        execSQL(
            "INSERT INTO patients VALUES ('b0000000-0000-4000-8000-000000000001', 'CRNL-000001', 'Ana Lucía', 'Cavazos', 'Ibarra', " +
                "'1998-05-14', 'Monterrey, Nuevo León', 'FEMALE', 'Monterrey', 'STUDENT', 'ACTIVE', 2000, '${BetaAccounts.PSYCHOLOGIST_ID}', " +
                "2000, '${BetaAccounts.PSYCHOLOGIST_ID}')",
        )
        execSQL(
            "INSERT INTO patient_contacts VALUES ('c0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001', " +
                "'PHONE', '+528100000101', 1, 'ACTIVE', 2000, 2000)",
        )
        execSQL(
            "INSERT INTO professional_assignments VALUES ('d0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001', " +
                "'PSYCHOLOGY', '${BetaAccounts.PSYCHOLOGIST_ID}', 3000, NULL, 'ACTIVE', 'Asignación inicial', '${BetaAccounts.ADMIN_ID}', 3000)",
        )
        execSQL(
            "INSERT INTO appointments VALUES ('e0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001', " +
                "'PSYCHOLOGY', '${BetaAccounts.PSYCHOLOGIST_ID}', 4000, 7000, 'IN_PERSON', 'Consultorio 1', NULL, 'SCHEDULED', NULL, " +
                "'${BetaAccounts.PSYCHOLOGIST_ID}', 4000, 4000)",
        )
        execSQL(
            "INSERT INTO audit_entries VALUES ('audit-v1', NULL, 'DEMO_SEED_APPLIED', 'DEMO_SEED', 'v1', NULL, NULL, 5000, 'SUCCESS', NULL)",
        )
    }

    private fun openMigrated(name: String): ClinicalDatabase =
        Room.databaseBuilder(context, ClinicalDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
            .also {
                opened = it
                it.openHelper.writableDatabase // fuerza la migración y la validación del esquema
            }

    private fun ClinicalDatabase.scalar(sql: String): Long = openHelper.writableDatabase.query(sql).use { cursor ->
        cursor.moveToFirst()
        cursor.getLong(0)
    }

    @Test
    fun `la base de la version 1 se crea a partir del esquema exportado`() {
        createPhaseOneDatabase(MIGRATED)

        SQLiteDatabase.openDatabase(context.getDatabasePath(MIGRATED).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(1, db.version)
            db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'demo_credentials'", null).use {
                assertEquals("la tabla de credenciales todavía no existe en v1", 0, it.count)
            }
        }
    }

    @Test
    fun `abrir sin la migracion falla en lugar de borrar los datos`() {
        createPhaseOneDatabase(MIGRATED)
        val withoutMigration = Room.databaseBuilder(context, ClinicalDatabase::class.java, MIGRATED).allowMainThreadQueries().build()

        assertFailsWithType<IllegalStateException> { withoutMigration.openHelper.writableDatabase }
        withoutMigration.close()

        SQLiteDatabase.openDatabase(context.getDatabasePath(MIGRATED).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM patients", null).use {
                it.moveToFirst()
                assertEquals("los datos siguen ahí", 1, it.getInt(0))
            }
        }
    }

    @Test
    fun `la migracion conserva todos los datos de la version 1`() {
        createPhaseOneDatabase(MIGRATED)

        val db = openMigrated(MIGRATED)

        assertEquals(2, db.scalar("SELECT COUNT(*) FROM demo_users"))
        assertEquals(1, db.scalar("SELECT COUNT(*) FROM patients"))
        assertEquals(1, db.scalar("SELECT COUNT(*) FROM patient_contacts"))
        assertEquals(1, db.scalar("SELECT COUNT(*) FROM professional_assignments"))
        assertEquals(1, db.scalar("SELECT COUNT(*) FROM appointments"))
        assertEquals(1, db.scalar("SELECT COUNT(*) FROM audit_entries"))
        db.openHelper.writableDatabase.query("SELECT firstName, email, roleCode, areaCode, status FROM demo_users WHERE userId = '${BetaAccounts.PSYCHOLOGIST_ID}'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Mariana", cursor.getString(0))
            assertEquals(BetaAccounts.PSYCHOLOGIST_EMAIL, cursor.getString(1))
            assertEquals("PROFESSIONAL", cursor.getString(2))
            assertEquals("PSYCHOLOGY", cursor.getString(3))
            assertEquals("ACTIVE", cursor.getString(4))
        }
        db.openHelper.writableDatabase.query("SELECT patientNumber, firstName, birthDate, createdBy FROM patients").use { cursor ->
            cursor.moveToFirst()
            assertEquals("CRNL-000001", cursor.getString(0))
            assertEquals("Ana Lucía", cursor.getString(1))
            assertEquals("1998-05-14", cursor.getString(2))
            assertEquals(BetaAccounts.PSYCHOLOGIST_ID, cursor.getString(3))
        }
    }

    @Test
    fun `la migracion agrega la cedula sin valor y la tabla de credenciales vacia`() {
        createPhaseOneDatabase(MIGRATED)

        val db = openMigrated(MIGRATED)

        assertEquals(2, db.scalar("SELECT COUNT(*) FROM demo_users WHERE professionalLicense IS NULL"))
        assertEquals(0, db.scalar("SELECT COUNT(*) FROM demo_credentials"))
        assertEquals(2, db.openHelper.writableDatabase.version)
    }

    @Test
    fun `tras migrar la base pasa las comprobaciones de integridad y de claves foraneas`() {
        createPhaseOneDatabase(MIGRATED)

        val db = openMigrated(MIGRATED)

        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals("hay violaciones de claves foráneas", 0, it.count) }
        db.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
            cursor.moveToFirst()
            assertEquals("ok", cursor.getString(0))
        }
    }

    @Test
    fun `la tabla de credenciales migrada conserva la restriccion contra borrar usuarios`() {
        createPhaseOneDatabase(MIGRATED)
        val db = openMigrated(MIGRATED)
        runBlocking { db.credentialDao().insert(mx.crnl.clinica.beta.testing.credentialEntity(userId = BetaAccounts.ADMIN_ID)) }

        val deleted = runCatching { db.openHelper.writableDatabase.execSQL("DELETE FROM demo_users WHERE userId = '${BetaAccounts.ADMIN_ID}'") }

        assertTrue("borrar un usuario con credencial debe fallar", deleted.isFailure)
        assertEquals(1, db.scalar("SELECT COUNT(*) FROM demo_users WHERE userId = '${BetaAccounts.ADMIN_ID}'"))
    }

    @Test
    fun `la aplicacion abre una instalacion de la Fase 1 y la actualiza sin perder ni duplicar datos`() {
        createPhaseOneDatabase(ClinicalDatabase.FILE_NAME)

        val db = ClinicalDatabase.create(context).also { opened = it }
        val clock = fixedClock()
        val seedCounts = runBlocking {
            val outcome = DemoDataInitializer(
                seedDao = db.seedDao(),
                sessionRepository = FakeSessionRepository(seedVersion = 1),
                loader = DemoSeedLoader(AssetSeedFileReader(context.assets)),
                clock = clock,
            ).initialize()
            (outcome as SeedOutcome.Applied).counts
        }

        val seed = DemoSeedLoader(AssetSeedFileReader(context.assets)).load()
        assertEquals(seed.users.size - 2, seedCounts.users)
        assertEquals(seed.credentials.size, seedCounts.credentials)
        assertEquals(seed.patients.size - 1, seedCounts.patients)
        assertEquals(seed.patients.size.toLong(), db.scalar("SELECT COUNT(*) FROM patients"))
        assertEquals(1, db.scalar("SELECT COUNT(*) FROM patients WHERE patientNumber = 'CRNL-000001'"))
        assertEquals(
            "00000103",
            db.openHelper.writableDatabase.query("SELECT professionalLicense FROM demo_users WHERE userId = '${BetaAccounts.PSYCHOLOGIST_ID}'").use {
                it.moveToFirst()
                it.getString(0)
            },
        )
        assertEquals(0, db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { it.count })

        val auth = LocalAuthRepository(
            database = db,
            sessionRepository = FakeSessionRepository(),
            passwordHasher = PasswordHasher(defaultIterations = 1_000),
            audit = AuditRecorder(db.auditDao(), clock) { "upgrade-audit" },
            clock = clock,
            idGenerator = { "upgrade-id" },
        )
        assertTrue(runBlocking { auth.signIn(BetaAccounts.PSYCHOLOGIST_EMAIL, BetaAccounts.PASSWORD) } is SignInResult.Success)
        assertNotNull(runBlocking { db.patientDao().getById("b0000000-0000-4000-8000-000000000001") })
    }

    private companion object {
        const val MIGRATED = "migration-test.db"
    }
}
