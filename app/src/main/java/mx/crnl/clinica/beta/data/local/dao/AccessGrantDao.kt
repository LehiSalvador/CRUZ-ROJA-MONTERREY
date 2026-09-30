package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.data.local.entity.AccessGrantEntity

@Dao
interface AccessGrantDao {
    @Insert
    suspend fun insert(grant: AccessGrantEntity)

    @Query("SELECT * FROM access_grants WHERE accessGrantId = :accessGrantId")
    suspend fun getById(accessGrantId: String): AccessGrantEntity?

    @Query("SELECT * FROM access_grants WHERE accessRequestId = :accessRequestId")
    suspend fun getByRequestId(accessRequestId: String): AccessGrantEntity?

    /** Todas las concesiones de una persona; quien las consume decide cuáles están vigentes según el reloj. */
    @Query("SELECT * FROM access_grants WHERE granteeUserId = :granteeUserId")
    fun observeByGrantee(granteeUserId: String): Flow<List<AccessGrantEntity>>

    @Query("SELECT * FROM access_grants WHERE granteeUserId = :granteeUserId")
    suspend fun listByGrantee(granteeUserId: String): List<AccessGrantEntity>

    /** Revoca solo una concesión todavía activa; devuelve las filas cambiadas. */
    @Query(
        """
        UPDATE access_grants SET status = 'REVOKED', revokedBy = :revokedBy, revokedAt = :revokedAt
        WHERE accessGrantId = :accessGrantId AND status = 'ACTIVE'
        """,
    )
    suspend fun revoke(accessGrantId: String, revokedBy: String, revokedAt: Long): Int
}
