package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.data.local.entity.ClinicalEncounterEntity

/** Un encuentro con los nombres resueltos y, si lo hay, el inicio de su cita. */
data class EncounterDetailRow(
    val encounterId: String,
    val patientId: String,
    val patientFirstName: String,
    val patientPaternalSurname: String,
    val patientMaternalSurname: String?,
    val patientNumber: String,
    val areaCode: String,
    val professionalId: String,
    val professionalFirstName: String,
    val professionalPaternalSurname: String,
    val appointmentId: String?,
    val appointmentStart: Long?,
    val encounterTypeCode: String,
    val status: String,
    val eventAt: Long,
    val recordedAt: Long,
    val createdByFirstName: String,
    val createdByPaternalSurname: String,
    val createdByMaternalSurname: String?,
)

private const val ENCOUNTER_SELECT = """
    SELECT e.encounterId AS encounterId,
           e.patientId AS patientId,
           p.firstName AS patientFirstName,
           p.paternalSurname AS patientPaternalSurname,
           p.maternalSurname AS patientMaternalSurname,
           p.patientNumber AS patientNumber,
           e.areaCode AS areaCode,
           e.professionalId AS professionalId,
           u.firstName AS professionalFirstName,
           u.paternalSurname AS professionalPaternalSurname,
           e.appointmentId AS appointmentId,
           a.startDateTime AS appointmentStart,
           e.encounterTypeCode AS encounterTypeCode,
           e.status AS status,
           e.eventAt AS eventAt,
           e.recordedAt AS recordedAt,
           c.firstName AS createdByFirstName,
           c.paternalSurname AS createdByPaternalSurname,
           c.maternalSurname AS createdByMaternalSurname
    FROM clinical_encounters e
    JOIN patients p ON p.patientId = e.patientId
    JOIN demo_users u ON u.userId = e.professionalId
    JOIN demo_users c ON c.userId = e.createdBy
    LEFT JOIN appointments a ON a.appointmentId = e.appointmentId
"""

/** Encuentros clínicos base: solo se insertan; no hay edición ni borrado. */
@Dao
interface EncounterDao {
    @Query("$ENCOUNTER_SELECT WHERE e.encounterId = :encounterId")
    fun observeDetailRow(encounterId: String): Flow<EncounterDetailRow?>

    @Query("$ENCOUNTER_SELECT WHERE e.encounterId = :encounterId")
    suspend fun getDetailRow(encounterId: String): EncounterDetailRow?

    @Query("$ENCOUNTER_SELECT WHERE e.patientId = :patientId AND e.areaCode = :areaCode ORDER BY e.eventAt DESC, e.recordedAt DESC")
    fun observeAreaRows(patientId: String, areaCode: String): Flow<List<EncounterDetailRow>>

    @Query("$ENCOUNTER_SELECT WHERE e.appointmentId = :appointmentId ORDER BY e.eventAt DESC, e.recordedAt DESC")
    fun observeRowsByAppointment(appointmentId: String): Flow<List<EncounterDetailRow>>

    @Query(
        """
        SELECT COUNT(*) FROM clinical_encounters
        WHERE patientId = :patientId AND areaCode = :areaCode AND professionalId = :professionalId
          AND encounterTypeCode = :typeCode AND eventAt = :eventAt
          AND ((appointmentId IS NULL AND :appointmentId IS NULL) OR appointmentId = :appointmentId)
        """,
    )
    suspend fun countIdentical(
        patientId: String,
        areaCode: String,
        professionalId: String,
        typeCode: String,
        eventAt: Long,
        appointmentId: String?,
    ): Int

    @Insert
    suspend fun insert(encounter: ClinicalEncounterEntity)
}
