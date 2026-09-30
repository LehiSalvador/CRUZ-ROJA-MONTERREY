package mx.crnl.clinica.beta.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.testing.DatabaseTest
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.appointmentEntity
import mx.crnl.clinica.beta.testing.assertFailsWithType
import mx.crnl.clinica.beta.testing.patientEntity
import mx.crnl.clinica.beta.testing.records
import mx.crnl.clinica.beta.testing.userEntity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppointmentDaoTest : DatabaseTest() {
    private val dao get() = db.appointmentDao()

    @Before
    fun insertParents() = runTest {
        db.seedDao().insertAll(
            records(
                users = listOf(userEntity(firstName = "Mariana", paternalSurname = "Elizondo")),
                patients = listOf(patientEntity(firstName = "Ana", paternalSurname = "Cavazos", maternalSurname = "Ibarra")),
            ),
        )
    }

    @Test
    fun `inserta y recupera una cita por id`() = runTest {
        val appointment = appointmentEntity()
        dao.insert(appointment)

        assertEquals(appointment, dao.getById("appointment-1"))
    }

    @Test
    fun `lista las citas de la mas antigua a la mas reciente con nombres de paciente y profesional`() = runTest {
        dao.insert(appointmentEntity(id = "later", start = TestNow.plusDays(2).toInstant()))
        dao.insert(appointmentEntity(id = "earlier", start = TestNow.minusDays(3).toInstant()))
        dao.insert(appointmentEntity(id = "middle", start = TestNow.toInstant()))

        val rows = dao.observeRows().first()

        assertEquals(listOf("earlier", "middle", "later"), rows.map { it.appointmentId })
        val row = rows.first()
        assertEquals("Ana", row.patientFirstName)
        assertEquals("Cavazos", row.patientPaternalSurname)
        assertEquals("Ibarra", row.patientMaternalSurname)
        assertEquals("CRNL-000001", row.patientNumber)
        assertEquals("Mariana", row.professionalFirstName)
        assertEquals("Elizondo", row.professionalPaternalSurname)
    }

    @Test
    fun `actualiza el estado de una cita`() = runTest {
        dao.insert(appointmentEntity(status = "SCHEDULED"))

        val updated = dao.updateStatus("appointment-1", "CONFIRMED", updatedAt = 8_000L)

        assertEquals(1, updated)
        val stored = dao.getById("appointment-1")!!
        assertEquals("CONFIRMED", stored.status)
        assertEquals(8_000L, stored.updatedAt)
    }

    @Test
    fun `encuentra las citas no canceladas del paciente o del profesional que se solapan con un horario`() = runTest {
        db.seedDao().insertAll(
            records(
                users = listOf(userEntity(id = "user-2", firstName = "Otro", paternalSurname = "Profesional")),
                patients = listOf(patientEntity(id = "patient-2", number = "CRNL-000002", firstName = "Segundo")),
            ),
        )
        val base = TestNow.toInstant()
        dao.insert(appointmentEntity(id = "mismo-pro", patientId = "patient-2", professionalId = "user-1", start = base, minutes = 50))
        dao.insert(appointmentEntity(id = "mismo-paciente", patientId = "patient-1", professionalId = "user-2", start = base.plusSeconds(1_800), minutes = 50))
        dao.insert(appointmentEntity(id = "cancelada", patientId = "patient-1", professionalId = "user-1", start = base, minutes = 50, status = "CANCELLED"))
        dao.insert(appointmentEntity(id = "contigua", patientId = "patient-1", professionalId = "user-1", start = base.plusSeconds(3_000), minutes = 50))
        dao.insert(appointmentEntity(id = "ajena", patientId = "patient-2", professionalId = "user-2", start = base, minutes = 50))

        val found = dao.findOverlapping("patient-1", "user-1", base.toEpochMilli(), base.plusSeconds(3_000).toEpochMilli())

        assertEquals(setOf("mismo-pro", "mismo-paciente"), found.map { it.appointmentId }.toSet())
    }

    @Test
    fun `actualiza una cita completa por su id`() = runTest {
        dao.insert(appointmentEntity())
        val current = dao.getById("appointment-1")!!

        dao.update(current.copy(location = "Sala 9", status = "RESCHEDULED", updatedAt = 9_000L))

        val stored = dao.getById("appointment-1")!!
        assertEquals("Sala 9", stored.location)
        assertEquals("RESCHEDULED", stored.status)
        assertEquals(current.createdAt, stored.createdAt)
        assertEquals(1, count("appointments"))
    }

    @Test
    fun `una cita no puede referenciar a un paciente inexistente`() = runTest {
        assertFailsWithType<SQLiteConstraintException> {
            dao.insert(appointmentEntity(patientId = "fantasma"))
        }
        assertEquals(0, count("appointments"))
    }

    @Test
    fun `una cita no puede referenciar a un profesional inexistente`() = runTest {
        assertFailsWithType<SQLiteConstraintException> {
            dao.insert(appointmentEntity(professionalId = "fantasma"))
        }
        assertEquals(0, count("appointments"))
    }
}
