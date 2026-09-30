package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.data.local.entity.AppointmentEntity

private const val LIST_SELECT = """
    SELECT a.appointmentId AS appointmentId,
           a.patientId AS patientId,
           p.firstName AS patientFirstName,
           p.paternalSurname AS patientPaternalSurname,
           p.maternalSurname AS patientMaternalSurname,
           p.patientNumber AS patientNumber,
           a.professionalId AS professionalId,
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
"""

private const val DETAIL_SELECT = """
    SELECT a.appointmentId AS appointmentId,
           a.patientId AS patientId,
           p.firstName AS patientFirstName,
           p.paternalSurname AS patientPaternalSurname,
           p.maternalSurname AS patientMaternalSurname,
           p.patientNumber AS patientNumber,
           a.professionalId AS professionalId,
           u.firstName AS professionalFirstName,
           u.paternalSurname AS professionalPaternalSurname,
           a.areaCode AS areaCode,
           a.startDateTime AS startDateTime,
           a.endDateTime AS endDateTime,
           a.modality AS modality,
           a.location AS location,
           a.meetingUrl AS meetingUrl,
           a.status AS status,
           a.administrativeNotes AS administrativeNotes,
           c.firstName AS createdByFirstName,
           c.paternalSurname AS createdByPaternalSurname,
           c.maternalSurname AS createdByMaternalSurname,
           a.createdAt AS createdAt,
           a.updatedAt AS updatedAt,
           (SELECT k.contactValue FROM patient_contacts k
             WHERE k.patientId = a.patientId AND k.contactType = 'PHONE' AND k.status = 'ACTIVE'
             ORDER BY k.isPrimary DESC, k.createdAt ASC, k.contactId ASC LIMIT 1) AS contactPhone
    FROM appointments a
    JOIN patients p ON p.patientId = a.patientId
    JOIN demo_users u ON u.userId = a.professionalId
    JOIN demo_users c ON c.userId = a.createdBy
"""

@Dao
interface AppointmentDao {
    @Query("$LIST_SELECT ORDER BY a.startDateTime ASC")
    fun observeRows(): Flow<List<AppointmentListRow>>

    @Query("$LIST_SELECT WHERE a.patientId = :patientId ORDER BY a.startDateTime ASC")
    fun observeRowsByPatient(patientId: String): Flow<List<AppointmentListRow>>

    @Query("$LIST_SELECT WHERE a.appointmentId = :appointmentId")
    suspend fun getListRow(appointmentId: String): AppointmentListRow?

    @Query("$DETAIL_SELECT WHERE a.appointmentId = :appointmentId")
    fun observeDetailRow(appointmentId: String): Flow<AppointmentDetailRow?>

    @Query("$DETAIL_SELECT WHERE a.appointmentId = :appointmentId")
    suspend fun getDetailRow(appointmentId: String): AppointmentDetailRow?

    @Query("SELECT * FROM appointments WHERE appointmentId = :appointmentId")
    suspend fun getById(appointmentId: String): AppointmentEntity?

    /** Citas no canceladas del paciente o del profesional cuyo horario se solapa con [start, end). */
    @Query(
        """
        SELECT * FROM appointments
        WHERE status <> 'CANCELLED'
          AND startDateTime < :end AND endDateTime > :start
          AND (patientId = :patientId OR professionalId = :professionalId)
        """,
    )
    suspend fun findOverlapping(patientId: String, professionalId: String, start: Long, end: Long): List<AppointmentEntity>

    @Insert
    suspend fun insert(appointment: AppointmentEntity)

    @Update
    suspend fun update(appointment: AppointmentEntity)

    @Query("UPDATE appointments SET status = :status, updatedAt = :updatedAt WHERE appointmentId = :appointmentId")
    suspend fun updateStatus(appointmentId: String, status: String, updatedAt: Long): Int
}
