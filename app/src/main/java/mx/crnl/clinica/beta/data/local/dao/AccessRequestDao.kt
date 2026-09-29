package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class PendingRequestRow(val requesterUserId: String, val ownerAreaCode: String)

@Dao
interface AccessRequestDao {
    @Query("SELECT requesterUserId AS requesterUserId, ownerAreaCode AS ownerAreaCode FROM interarea_access_requests WHERE status = 'PENDING'")
    fun observePending(): Flow<List<PendingRequestRow>>
}
