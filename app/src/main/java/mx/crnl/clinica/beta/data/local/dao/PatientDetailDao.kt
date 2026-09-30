package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class EncounterRow(
    val encounterId: String,
    val areaCode: String,
    val professionalFirstName: String,
    val professionalPaternalSurname: String,
    val encounterTypeCode: String,
    val status: String,
    val eventAt: Long,
    val appointmentId: String?,
)

/** Lecturas con nombres ya resueltos para el expediente de un paciente. Solo lectura. */
@Dao
interface PatientDetailDao {
    @Query(
        """
        SELECT e.encounterId AS encounterId,
               e.areaCode AS areaCode,
               u.firstName AS professionalFirstName,
               u.paternalSurname AS professionalPaternalSurname,
               e.encounterTypeCode AS encounterTypeCode,
               e.status AS status,
               e.eventAt AS eventAt,
               e.appointmentId AS appointmentId
        FROM clinical_encounters e
        JOIN demo_users u ON u.userId = e.professionalId
        WHERE e.patientId = :patientId
        ORDER BY e.eventAt DESC
        """,
    )
    fun observeEncounters(patientId: String): Flow<List<EncounterRow>>
}
