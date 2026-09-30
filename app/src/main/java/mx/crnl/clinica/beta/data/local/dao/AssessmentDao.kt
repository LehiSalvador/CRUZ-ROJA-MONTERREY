package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Una aplicación de un instrumento con su resultado, el encuentro relacionado y los nombres ya resueltos. Solo lectura. */
data class AssessmentRow(
    val assessmentId: String,
    val patientId: String,
    val patientFirstName: String,
    val patientPaternalSurname: String,
    val patientMaternalSurname: String?,
    val patientNumber: String,
    val areaCode: String?,
    val professionalFirstName: String,
    val professionalPaternalSurname: String,
    val instrumentCode: String,
    val instrumentVersion: String,
    val administrationModeCode: String,
    val status: String,
    val startedAt: Long,
    val completedAt: Long?,
    val encounterId: String?,
    val resultId: String?,
    val rawScore: Double?,
    val classificationCode: String?,
    val scoringVersion: String?,
)

private const val ASSESSMENT_SELECT = """
    SELECT s.assessmentId AS assessmentId,
           s.patientId AS patientId,
           p.firstName AS patientFirstName,
           p.paternalSurname AS patientPaternalSurname,
           p.maternalSurname AS patientMaternalSurname,
           p.patientNumber AS patientNumber,
           e.areaCode AS areaCode,
           u.firstName AS professionalFirstName,
           u.paternalSurname AS professionalPaternalSurname,
           s.instrumentCode AS instrumentCode,
           s.instrumentVersion AS instrumentVersion,
           s.administrationModeCode AS administrationModeCode,
           s.status AS status,
           s.startedAt AS startedAt,
           s.completedAt AS completedAt,
           s.encounterId AS encounterId,
           r.assessmentResultId AS resultId,
           r.rawScore AS rawScore,
           r.classificationCode AS classificationCode,
           r.scoringVersion AS scoringVersion
    FROM assessments s
    JOIN patients p ON p.patientId = s.patientId
    JOIN demo_users u ON u.userId = s.professionalId
    LEFT JOIN clinical_encounters e ON e.encounterId = s.encounterId
    LEFT JOIN assessment_results r ON r.assessmentId = s.assessmentId
"""

/** Lecturas del historial de evaluaciones. No hay escrituras: los resultados los produce un módulo posterior. */
@Dao
interface AssessmentDao {
    @Query("$ASSESSMENT_SELECT WHERE s.patientId = :patientId ORDER BY s.startedAt DESC, s.assessmentId ASC")
    fun observeRowsByPatient(patientId: String): Flow<List<AssessmentRow>>

    @Query("$ASSESSMENT_SELECT WHERE s.assessmentId = :assessmentId")
    fun observeRow(assessmentId: String): Flow<AssessmentRow?>

    @Query("$ASSESSMENT_SELECT WHERE s.assessmentId = :assessmentId")
    suspend fun getRow(assessmentId: String): AssessmentRow?
}
