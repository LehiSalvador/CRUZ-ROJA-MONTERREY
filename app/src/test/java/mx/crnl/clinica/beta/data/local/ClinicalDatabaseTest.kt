package mx.crnl.clinica.beta.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.testing.DatabaseTest
import mx.crnl.clinica.beta.testing.appointmentEntity
import mx.crnl.clinica.beta.testing.assertFailsWithType
import mx.crnl.clinica.beta.testing.credentialEntity
import mx.crnl.clinica.beta.testing.patientEntity
import mx.crnl.clinica.beta.testing.records
import mx.crnl.clinica.beta.testing.userEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClinicalDatabaseTest : DatabaseTest() {

    @Test
    fun `crea todas las tablas del esquema v2`() {
        val tables = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'",
            null,
        ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

        assertEquals(
            setOf(
                "demo_users",
                "demo_credentials",
                "patients",
                "patient_contacts",
                "professional_assignments",
                "appointments",
                "clinical_encounters",
                "assessments",
                "assessment_results",
                "interarea_access_requests",
                "audit_entries",
            ),
            tables,
        )
    }

    @Test
    fun `las llaves foraneas estan activas`() {
        db.query("PRAGMA foreign_keys", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun `un usuario referenciado por un paciente no se puede borrar`() = runTest {
        db.seedDao().insertAll(records(users = listOf(userEntity()), patients = listOf(patientEntity())))

        assertFailsWithType<SQLiteConstraintException> {
            db.openHelper.writableDatabase.execSQL("DELETE FROM demo_users WHERE userId = 'user-1'")
        }
        assertEquals(1, count("demo_users"))
    }

    @Test
    fun `una credencial no puede referenciar a un usuario inexistente`() = runTest {
        assertFailsWithType<SQLiteConstraintException> {
            db.credentialDao().insert(credentialEntity(userId = "fantasma"))
        }
        assertEquals(0, count("demo_credentials"))
    }

    @Test
    fun `un usuario con credencial no se puede borrar`() = runTest {
        db.seedDao().insertAll(records(users = listOf(userEntity()), credentials = listOf(credentialEntity())))

        assertFailsWithType<SQLiteConstraintException> {
            db.openHelper.writableDatabase.execSQL("DELETE FROM demo_users WHERE userId = 'user-1'")
        }
        assertEquals(1, count("demo_credentials"))
    }

    @Test
    fun `cada usuario tiene como maximo una credencial y un correo unico`() = runTest {
        db.seedDao().insertAll(records(users = listOf(userEntity()), credentials = listOf(credentialEntity())))

        assertFailsWithType<SQLiteConstraintException> { db.credentialDao().insert(credentialEntity()) }
        assertFailsWithType<SQLiteConstraintException> { db.userDao().insert(userEntity(id = "user-2", email = "user-1@example.org")) }
    }

    @Test
    fun `un paciente con registros asociados no se puede borrar`() = runTest {
        db.seedDao().insertAll(
            records(
                users = listOf(userEntity()),
                patients = listOf(patientEntity()),
                appointments = listOf(appointmentEntity()),
            ),
        )

        assertFailsWithType<SQLiteConstraintException> {
            db.openHelper.writableDatabase.execSQL("DELETE FROM patients WHERE patientId = 'patient-1'")
        }
        assertEquals(1, count("patients"))
    }
}
