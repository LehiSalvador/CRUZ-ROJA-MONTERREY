package mx.crnl.clinica.beta.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Material de verificación de contraseña de una cuenta local (1:1 con `demo_users`). Guarda solo la sal y el
 * resultado de la derivación, junto con los parámetros que hicieron falta para calcularlo.
 */
@Entity(
    tableName = "demo_credentials",
    foreignKeys = [
        ForeignKey(
            entity = DemoUserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class DemoCredentialEntity(
    @PrimaryKey val userId: String,
    val algorithm: String,
    val iterations: Int,
    val salt: String,
    val passwordHash: String,
    val createdAt: Long,
    val updatedAt: Long,
)
