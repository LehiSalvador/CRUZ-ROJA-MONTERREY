package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.data.local.entity.PatientEntity

@Dao
interface PatientDao {
    @Query(
        """
        SELECT * FROM patients
        ORDER BY paternalSurname COLLATE NOCASE, maternalSurname COLLATE NOCASE, firstName COLLATE NOCASE
        """,
    )
    fun observeAll(): Flow<List<PatientEntity>>

    @Query("SELECT * FROM patients WHERE patientId = :patientId")
    suspend fun getById(patientId: String): PatientEntity?

    @Insert
    suspend fun insert(patient: PatientEntity)

    @Query(
        """
        UPDATE patients
        SET status = :status, updatedAt = :updatedAt, updatedBy = :updatedBy
        WHERE patientId = :patientId
        """,
    )
    suspend fun updateStatus(patientId: String, status: String, updatedAt: Long, updatedBy: String): Int
}
