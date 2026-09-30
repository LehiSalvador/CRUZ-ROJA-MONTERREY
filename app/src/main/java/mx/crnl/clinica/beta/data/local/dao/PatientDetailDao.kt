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

data class AssessmentRow(
    val assessmentId: String,
    val areaCode: String?,
    val professionalFirstName: String,
    val professionalPaternalSurname: String,
    val status: String,
    val startedAt: Long,
    val resultId: String?,
    val classificationCode: String?,
    val classificationLabel: String?,
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

    @Query(
        """
        SELECT s.assessmentId AS assessmentId,
               e.areaCode AS areaCode,
               u.firstName AS professionalFirstName,
               u.paternalSurname AS professionalPaternalSurname,
               s.status AS status,
               s.startedAt AS startedAt,
               r.assessmentResultId AS resultId,
               r.classificationCode AS classificationCode,
               r.classificationLabel AS classificationLabel
        FROM assessments s
        JOIN demo_users u ON u.userId = s.professionalId
        LEFT JOIN clinical_encounters e ON e.encounterId = s.encounterId
        LEFT JOIN assessment_results r ON r.assessmentId = s.assessmentId
        WHERE s.patientId = :patientId
        ORDER BY s.startedAt DESC
        """,
    )
    fun observeAssessments(patientId: String): Flow<List<AssessmentRow>>
}
