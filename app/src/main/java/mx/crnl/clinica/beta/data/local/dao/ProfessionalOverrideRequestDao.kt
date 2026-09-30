package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.data.local.entity.ProfessionalOverrideRequestEntity

data class PendingChangeRow(val requesterUserId: String, val areaCode: String)

/** Una solicitud de cambio de profesional con los nombres ya resueltos. */
data class OverrideRequestRow(
    val overrideRequestId: String,
    val patientId: String,
    val patientFirstName: String,
    val patientPaternalSurname: String,
    val patientMaternalSurname: String?,
    val patientNumber: String,
    val areaCode: String,
    val currentAssignmentId: String,
    val currentProfessionalId: String,
    val currentFirstName: String,
    val currentPaternalSurname: String,
    val requestedProfessionalId: String,
    val requestedFirstName: String,
    val requestedPaternalSurname: String,
    val requestedBy: String,
    val requesterFirstName: String,
    val requesterPaternalSurname: String,
    val requesterMaternalSurname: String?,
    val reason: String,
    val requestedAt: Long,
    val status: String,
    val reviewedBy: String?,
    val reviewerFirstName: String?,
    val reviewerPaternalSurname: String?,
    val reviewerMaternalSurname: String?,
    val reviewedAt: Long?,
    val resolutionReason: String?,
)

private const val OVERRIDE_SELECT = """
    SELECT o.overrideRequestId AS overrideRequestId,
           o.patientId AS patientId,
           p.firstName AS patientFirstName,
           p.paternalSurname AS patientPaternalSurname,
           p.maternalSurname AS patientMaternalSurname,
           p.patientNumber AS patientNumber,
           o.areaCode AS areaCode,
           o.currentAssignmentId AS currentAssignmentId,
           o.currentProfessionalId AS currentProfessionalId,
           c.firstName AS currentFirstName,
           c.paternalSurname AS currentPaternalSurname,
           o.requestedProfessionalId AS requestedProfessionalId,
           n.firstName AS requestedFirstName,
           n.paternalSurname AS requestedPaternalSurname,
           o.requestedBy AS requestedBy,
           q.firstName AS requesterFirstName,
           q.paternalSurname AS requesterPaternalSurname,
           q.maternalSurname AS requesterMaternalSurname,
           o.reason AS reason,
           o.requestedAt AS requestedAt,
           o.status AS status,
           o.reviewedBy AS reviewedBy,
           v.firstName AS reviewerFirstName,
           v.paternalSurname AS reviewerPaternalSurname,
           v.maternalSurname AS reviewerMaternalSurname,
           o.reviewedAt AS reviewedAt,
           o.resolutionReason AS resolutionReason
    FROM professional_override_requests o
    JOIN patients p ON p.patientId = o.patientId
    JOIN demo_users c ON c.userId = o.currentProfessionalId
    JOIN demo_users n ON n.userId = o.requestedProfessionalId
    JOIN demo_users q ON q.userId = o.requestedBy
    LEFT JOIN demo_users v ON v.userId = o.reviewedBy
"""

@Dao
interface ProfessionalOverrideRequestDao {
    @Query("$OVERRIDE_SELECT ORDER BY o.requestedAt DESC, o.overrideRequestId ASC")
    fun observeRows(): Flow<List<OverrideRequestRow>>

    @Query("$OVERRIDE_SELECT WHERE o.overrideRequestId = :overrideRequestId")
    fun observeRow(overrideRequestId: String): Flow<OverrideRequestRow?>

    @Query("$OVERRIDE_SELECT WHERE o.overrideRequestId = :overrideRequestId")
    suspend fun getRow(overrideRequestId: String): OverrideRequestRow?

    @Query("SELECT * FROM professional_override_requests WHERE overrideRequestId = :overrideRequestId")
    suspend fun getById(overrideRequestId: String): ProfessionalOverrideRequestEntity?

    /** La solicitud sin resolver de un paciente en un área, si la hay (solo puede haber una). */
    @Query("$OVERRIDE_SELECT WHERE o.patientId = :patientId AND o.areaCode = :areaCode AND o.status = 'PENDING' LIMIT 1")
    fun observePendingRow(patientId: String, areaCode: String): Flow<OverrideRequestRow?>

    @Query("SELECT COUNT(*) FROM professional_override_requests WHERE patientId = :patientId AND areaCode = :areaCode AND status = 'PENDING'")
    suspend fun countPending(patientId: String, areaCode: String): Int

    /** Solicitudes sin resolver, solo lo necesario para contarlas por persona y área. */
    @Query("SELECT requestedBy AS requesterUserId, areaCode AS areaCode FROM professional_override_requests WHERE status = 'PENDING'")
    fun observePending(): Flow<List<PendingChangeRow>>

    @Insert
    suspend fun insert(request: ProfessionalOverrideRequestEntity)

    /** Resuelve la solicitud solo si sigue pendiente; devuelve las filas cambiadas. */
    @Query(
        """
        UPDATE professional_override_requests
        SET status = :status, reviewedBy = :reviewedBy, reviewedAt = :reviewedAt, resolutionReason = :resolutionReason
        WHERE overrideRequestId = :overrideRequestId AND status = 'PENDING'
        """,
    )
    suspend fun resolve(overrideRequestId: String, status: String, reviewedBy: String, reviewedAt: Long, resolutionReason: String?): Int
}
