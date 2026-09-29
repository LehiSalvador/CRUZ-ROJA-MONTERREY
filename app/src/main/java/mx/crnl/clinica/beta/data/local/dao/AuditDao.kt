package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import mx.crnl.clinica.beta.data.local.entity.AuditEntryEntity

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(entry: AuditEntryEntity)
}
