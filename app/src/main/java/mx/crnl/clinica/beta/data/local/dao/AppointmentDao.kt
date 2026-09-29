package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.data.local.entity.AppointmentEntity

@Dao
interface AppointmentDao {
    @Query(
        """
        SELECT a.appointmentId AS appointmentId,
               a.patientId AS patientId,
               p.firstName AS patientFirstName,
               p.paternalSurname AS patientPaternalSurname,
               p.maternalSurname AS patientMaternalSurname,
               p.patientNumber AS patientNumber,
               u.firstName AS professionalFirstName,
               u.paternalSurname AS professionalPaternalSurname,
               a.areaCode AS areaCode,
               a.startDateTime AS startDateTime,
               a.endDateTime AS endDateTime,
               a.modality AS modality,
               a.location AS location,
               a.status AS status
        FROM appointments a
        JOIN patients p ON p.patientId = a.patientId
        JOIN demo_users u ON u.userId = a.professionalId
        ORDER BY a.startDateTime ASC
        """,
    )
    fun observeRows(): Flow<List<AppointmentListRow>>

    @Query("SELECT * FROM appointments WHERE appointmentId = :appointmentId")
    suspend fun getById(appointmentId: String): AppointmentEntity?

    @Insert
    suspend fun insert(appointment: AppointmentEntity)

    @Query("UPDATE appointments SET status = :status, updatedAt = :updatedAt WHERE appointmentId = :appointmentId")
    suspend fun updateStatus(appointmentId: String, status: String, updatedAt: Long): Int
}
