package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.data.local.entity.DemoUserEntity

@Dao
interface UserDao {
    @Query("SELECT * FROM demo_users WHERE lower(email) = :email LIMIT 1")
    suspend fun findByEmail(email: String): DemoUserEntity?

    @Query("SELECT * FROM demo_users WHERE userId = :userId")
    suspend fun getById(userId: String): DemoUserEntity?

    @Query("SELECT * FROM demo_users WHERE userId = :userId")
    fun observeById(userId: String): Flow<DemoUserEntity?>

    /** Cuentas activas con rol de profesional en el área; el orden por apellido lo aplica quien las presenta. */
    @Query("SELECT * FROM demo_users WHERE roleCode = 'PROFESSIONAL' AND areaCode = :areaCode AND status = 'ACTIVE'")
    suspend fun listActiveProfessionals(areaCode: String): List<DemoUserEntity>

    @Insert
    suspend fun insert(user: DemoUserEntity)
}
