package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.data.local.entity.AuditEntryEntity

/** Una entrada de la bitácora con el nombre de quien actuó. Nunca incluye la metadata: no se consulta desde la interfaz. */
data class AuditRow(
    val auditId: String,
    val occurredAt: Long,
    val actionCode: String,
    val entityType: String,
    val areaCode: String?,
    val result: String,
    val actorFirstName: String?,
    val actorPaternalSurname: String?,
    val actorMaternalSurname: String?,
)

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(entry: AuditEntryEntity)

    /** Las entradas más recientes desde [since], la más nueva primero. Solo lectura: la bitácora no se edita ni se borra. */
    @Query(
        """
        SELECT a.auditId AS auditId,
               a.occurredAt AS occurredAt,
               a.`action` AS actionCode,
               a.entityType AS entityType,
               a.areaCode AS areaCode,
               a.result AS result,
               u.firstName AS actorFirstName,
               u.paternalSurname AS actorPaternalSurname,
               u.maternalSurname AS actorMaternalSurname
        FROM audit_entries a
        LEFT JOIN demo_users u ON u.userId = a.actorUserId
        WHERE a.occurredAt >= :since
        ORDER BY a.occurredAt DESC, a.rowid DESC
        LIMIT :limit
        """,
    )
    fun observeRows(since: Long, limit: Int): Flow<List<AuditRow>>
}
