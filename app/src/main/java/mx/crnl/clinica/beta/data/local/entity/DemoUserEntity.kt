package mx.crnl.clinica.beta.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Usuarios internos ficticios de la Beta local; el backend real los sustituirá. Timestamps en UTC (epoch ms). */
@Entity(
    tableName = "demo_users",
    indices = [Index(value = ["email"], unique = true)],
)
data class DemoUserEntity(
    @PrimaryKey val userId: String,
    val firstName: String,
    val paternalSurname: String,
    val maternalSurname: String?,
    val email: String,
    val roleCode: String,
    val areaCode: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)
