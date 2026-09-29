package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.data.local.entity.PatientContactEntity
import mx.crnl.clinica.beta.data.local.entity.PatientEntity
import mx.crnl.clinica.beta.data.local.entity.ProfessionalAssignmentEntity

/** Paciente con sus contactos y asignaciones, resueltos por Room para listar, buscar y detectar duplicados. */
data class PatientAggregate(
    @Embedded val patient: PatientEntity,
    @Relation(parentColumn = "patientId", entityColumn = "patientId")
    val contacts: List<PatientContactEntity>,
    @Relation(parentColumn = "patientId", entityColumn = "patientId")
    val assignments: List<ProfessionalAssignmentEntity>,
)

data class PatientWithContacts(
    @Embedded val patient: PatientEntity,
    @Relation(parentColumn = "patientId", entityColumn = "patientId")
    val contacts: List<PatientContactEntity>,
)

@Dao
interface PatientDao {
    @Transaction
    @Query("SELECT * FROM patients")
    fun observeAggregates(): Flow<List<PatientAggregate>>

    @Transaction
    @Query("SELECT * FROM patients")
    suspend fun getAggregates(): List<PatientAggregate>

    @Transaction
    @Query("SELECT * FROM patients WHERE patientId = :patientId")
    fun observeWithContacts(patientId: String): Flow<PatientWithContacts?>

    @Query("SELECT * FROM patients WHERE patientId = :patientId")
    suspend fun getById(patientId: String): PatientEntity?

    @Query("SELECT patientNumber FROM patients")
    suspend fun getAllPatientNumbers(): List<String>

    @Query(
        """
        SELECT * FROM patient_contacts
        WHERE patientId = :patientId AND status = 'ACTIVE'
        ORDER BY isPrimary DESC, createdAt ASC, contactId ASC
        """,
    )
    suspend fun getActiveContacts(patientId: String): List<PatientContactEntity>

    @Insert
    suspend fun insert(patient: PatientEntity)

    @Insert
    suspend fun insertContacts(contacts: List<PatientContactEntity>)

    @Update
    suspend fun update(patient: PatientEntity)

    @Update
    suspend fun updateContact(contact: PatientContactEntity)

    @Query(
        """
        UPDATE patients
        SET status = :status, updatedAt = :updatedAt, updatedBy = :updatedBy
        WHERE patientId = :patientId
        """,
    )
    suspend fun updateStatus(patientId: String, status: String, updatedAt: Long, updatedBy: String): Int
}
