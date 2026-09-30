package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.data.local.entity.AccessRequestEntity

data class PendingRequestRow(val requesterUserId: String, val ownerAreaCode: String)

/** Una solicitud interárea con los nombres ya resueltos y, si se aprobó, la concesión que produjo. */
data class AccessRequestRow(
    val accessRequestId: String,
    val patientId: String,
    val patientFirstName: String,
    val patientPaternalSurname: String,
    val patientMaternalSurname: String?,
    val patientNumber: String,
    val requesterUserId: String,
    val requesterFirstName: String,
    val requesterPaternalSurname: String,
    val requesterMaternalSurname: String?,
    val requesterAreaCode: String,
    val ownerAreaCode: String,
    val reason: String,
    val requestedScope: String,
    val requestedAt: Long,
    val status: String,
    val reviewedBy: String?,
    val reviewerFirstName: String?,
    val reviewerPaternalSurname: String?,
    val reviewerMaternalSurname: String?,
    val reviewedAt: Long?,
    val grantId: String?,
    val grantValidFrom: Long?,
    val grantExpiresAt: Long?,
    val grantStatus: String?,
    val grantRevokedAt: Long?,
)

private const val REQUEST_SELECT = """
    SELECT r.accessRequestId AS accessRequestId,
           r.patientId AS patientId,
           p.firstName AS patientFirstName,
           p.paternalSurname AS patientPaternalSurname,
           p.maternalSurname AS patientMaternalSurname,
           p.patientNumber AS patientNumber,
           r.requesterUserId AS requesterUserId,
           q.firstName AS requesterFirstName,
           q.paternalSurname AS requesterPaternalSurname,
           q.maternalSurname AS requesterMaternalSurname,
           r.requesterAreaCode AS requesterAreaCode,
           r.ownerAreaCode AS ownerAreaCode,
           r.reason AS reason,
           r.requestedScope AS requestedScope,
           r.requestedAt AS requestedAt,
           r.status AS status,
           r.reviewedBy AS reviewedBy,
           v.firstName AS reviewerFirstName,
           v.paternalSurname AS reviewerPaternalSurname,
           v.maternalSurname AS reviewerMaternalSurname,
           r.reviewedAt AS reviewedAt,
           g.accessGrantId AS grantId,
           g.validFrom AS grantValidFrom,
           g.expiresAt AS grantExpiresAt,
           g.status AS grantStatus,
           g.revokedAt AS grantRevokedAt
    FROM interarea_access_requests r
    JOIN patients p ON p.patientId = r.patientId
    JOIN demo_users q ON q.userId = r.requesterUserId
    LEFT JOIN demo_users v ON v.userId = r.reviewedBy
    LEFT JOIN access_grants g ON g.accessRequestId = r.accessRequestId
"""

@Dao
interface AccessRequestDao {
    @Query("SELECT requesterUserId AS requesterUserId, ownerAreaCode AS ownerAreaCode FROM interarea_access_requests WHERE status = 'PENDING'")
    fun observePending(): Flow<List<PendingRequestRow>>

    @Query("$REQUEST_SELECT ORDER BY r.requestedAt DESC, r.accessRequestId ASC")
    fun observeRows(): Flow<List<AccessRequestRow>>

    @Query("$REQUEST_SELECT WHERE r.accessRequestId = :accessRequestId")
    fun observeRow(accessRequestId: String): Flow<AccessRequestRow?>

    @Query("$REQUEST_SELECT WHERE r.accessRequestId = :accessRequestId")
    suspend fun getRow(accessRequestId: String): AccessRequestRow?

    @Query("SELECT * FROM interarea_access_requests WHERE accessRequestId = :accessRequestId")
    suspend fun getById(accessRequestId: String): AccessRequestEntity?

    /** Solicitudes sin resolver de la misma persona para el mismo paciente y área: no se duplican. */
    @Query(
        """
        SELECT COUNT(*) FROM interarea_access_requests
        WHERE status = 'PENDING' AND patientId = :patientId AND requesterUserId = :requesterUserId AND ownerAreaCode = :ownerAreaCode
        """,
    )
    suspend fun countPending(patientId: String, requesterUserId: String, ownerAreaCode: String): Int

    @Insert
    suspend fun insert(request: AccessRequestEntity)

    /** Resuelve una solicitud solo si sigue pendiente; devuelve las filas cambiadas (0 si otra persona ya la resolvió). */
    @Query(
        """
        UPDATE interarea_access_requests
        SET status = :status, reviewedBy = :reviewedBy, reviewedAt = :reviewedAt, expiresAt = :expiresAt
        WHERE accessRequestId = :accessRequestId AND status = 'PENDING'
        """,
    )
    suspend fun resolve(accessRequestId: String, status: String, reviewedBy: String, reviewedAt: Long, expiresAt: Long?): Int
}
