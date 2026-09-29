package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import mx.crnl.clinica.beta.data.local.entity.DemoCredentialEntity

@Dao
interface CredentialDao {
    @Query("SELECT * FROM demo_credentials WHERE userId = :userId")
    suspend fun getByUserId(userId: String): DemoCredentialEntity?

    @Insert
    suspend fun insert(credential: DemoCredentialEntity)
}
