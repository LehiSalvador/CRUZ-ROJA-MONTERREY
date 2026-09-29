package mx.crnl.clinica.beta.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.testing.DatabaseTest
import mx.crnl.clinica.beta.testing.assignmentEntity
import mx.crnl.clinica.beta.testing.assertFailsWithType
import mx.crnl.clinica.beta.testing.contactEntity
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
    fun `el agregado reune al paciente con sus contactos y asignaciones`() = runTest {
        dao.insert(patientEntity(id = "p1", number = "CRNL-000001"))
        dao.insert(patientEntity(id = "p2", number = "CRNL-000002"))
        dao.insertContacts(
            listOf(
                contactEntity("c1", patientId = "p1", type = "PHONE"),
                contactEntity("c2", patientId = "p1", type = "EMAIL", value = "uno@example.org"),
                contactEntity("c3", patientId = "p2", type = "PHONE", value = "+528100000102"),
            ),
        )

        db.seedDao().insertAll(records(assignments = listOf(assignmentEntity("a1", patientId = "p1"))))

        val aggregates = dao.observeAggregates().first().associateBy { it.patient.patientId }

        assertEquals(setOf("c1", "c2"), aggregates.getValue("p1").contacts.map { it.contactId }.toSet())
        assertEquals(listOf("a1"), aggregates.getValue("p1").assignments.map { it.assignmentId })
        assertEquals(listOf("c3"), aggregates.getValue("p2").contacts.map { it.contactId })
        assertEquals(emptyList<String>(), aggregates.getValue("p2").assignments.map { it.assignmentId })
    }

    @Test
    fun `los contactos vigentes se devuelven con el principal primero`() = runTest {
        dao.insert(patientEntity())
        dao.insertContacts(
            listOf(
                contactEntity("old", primary = false, createdAt = 1_000L),
                contactEntity("main", primary = true, createdAt = 3_000L),
                contactEntity("gone", primary = false, status = "INACTIVE"),
            ),
        )

        assertEquals(listOf("main", "old"), dao.getActiveContacts("patient-1").map { it.contactId })
    }

    @Test
    fun `un contacto no puede referenciar a un paciente inexistente`() = runTest {
        assertFailsWithType<SQLiteConstraintException> {
            dao.insertContacts(listOf(contactEntity("c1", patientId = "fantasma")))
        }
        assertEquals(0, count("patient_contacts"))
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
