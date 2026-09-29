package mx.crnl.clinica.beta.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.testing.DatabaseTest
import mx.crnl.clinica.beta.testing.assertFailsWithType
import mx.crnl.clinica.beta.testing.patientEntity
import mx.crnl.clinica.beta.testing.records
import mx.crnl.clinica.beta.testing.userEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PatientDaoTest : DatabaseTest() {
    private val dao get() = db.patientDao()

    @Before
    fun insertCreator() = runTest {
        db.seedDao().insertAll(records(users = listOf(userEntity(), userEntity(id = "user-2"))))
    }

    @Test
    fun `inserta y recupera un paciente por id`() = runTest {
        val patient = patientEntity()
        dao.insert(patient)

        assertEquals(patient, dao.getById("patient-1"))
    }

    @Test
    fun `un id inexistente devuelve nulo`() = runTest {
        assertNull(dao.getById("no-existe"))
    }

    @Test
    fun `lista los pacientes por apellidos y nombre sin distinguir mayusculas`() = runTest {
        dao.insert(patientEntity(id = "p-zeta", number = "CRNL-000003", paternalSurname = "Zeta", maternalSurname = null))
        dao.insert(patientEntity(id = "p-alfa", number = "CRNL-000001", paternalSurname = "alfa", maternalSurname = null))
        dao.insert(patientEntity(id = "p-beta-b", number = "CRNL-000005", paternalSurname = "Beta", maternalSurname = "Beto", firstName = "Ana"))
        dao.insert(patientEntity(id = "p-beta-a", number = "CRNL-000004", paternalSurname = "Beta", maternalSurname = "Aro", firstName = "Zoe"))

        val ordered = dao.observeAll().first().map { it.patientId }

        assertEquals(listOf("p-alfa", "p-beta-a", "p-beta-b", "p-zeta"), ordered)
    }

    @Test
    fun `actualiza el estado y deja constancia de quien y cuando`() = runTest {
        dao.insert(patientEntity())

        val updated = dao.updateStatus("patient-1", "INACTIVE", updatedAt = 9_000L, updatedBy = "user-2")

        assertEquals(1, updated)
        val stored = dao.getById("patient-1")!!
        assertEquals("INACTIVE", stored.status)
        assertEquals(9_000L, stored.updatedAt)
        assertEquals("user-2", stored.updatedBy)
        assertEquals(2_000L, stored.createdAt)
        assertEquals("user-1", stored.createdBy)
    }

    @Test
    fun `actualizar un paciente inexistente no modifica nada`() = runTest {
        assertEquals(0, dao.updateStatus("no-existe", "INACTIVE", 1L, "user-1"))
    }

    @Test
    fun `el folio humano es unico`() = runTest {
        dao.insert(patientEntity(id = "p1", number = "CRNL-000001"))

        assertFailsWithType<SQLiteConstraintException> {
            dao.insert(patientEntity(id = "p2", number = "CRNL-000001"))
        }
        assertEquals(1, count("patients"))
    }

    @Test
    fun `un paciente no puede referenciar a un usuario inexistente`() = runTest {
        assertFailsWithType<SQLiteConstraintException> {
            dao.insert(patientEntity(createdBy = "fantasma"))
        }
        assertEquals(0, count("patients"))
    }
}
