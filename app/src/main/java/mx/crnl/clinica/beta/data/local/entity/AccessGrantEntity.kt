package mx.crnl.clinica.beta.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Acceso interárea de solo lectura ya concedido: nace al aprobar una solicitud y se materializa como fila propia para
 * que la vigencia y la revocación no dependan del estado de la solicitud. Persiste únicamente ACTIVE o REVOKED; que
 * haya vencido se deduce de [expiresAt].
 */
@Entity(
    tableName = "access_grants",
    foreignKeys = [
        ForeignKey(
            entity = AccessRequestEntity::class,
            parentColumns = ["accessRequestId"],
            childColumns = ["accessRequestId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patientId"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = DemoUserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["granteeUserId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = DemoUserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["grantedBy"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = DemoUserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["revokedBy"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["accessRequestId"], unique = true),
        Index(value = ["patientId"]),
        Index(value = ["granteeUserId"]),
        Index(value = ["status"]),
        Index(value = ["expiresAt"]),
        Index(value = ["grantedBy"]),
        Index(value = ["revokedBy"]),
    ],
)
data class AccessGrantEntity(
    @PrimaryKey val accessGrantId: String,
    val accessRequestId: String,
    val patientId: String,
    val granteeUserId: String,
    val ownerAreaCode: String,
    val scopeCode: String,
    val validFrom: Long,
    val expiresAt: Long,
    val status: String,
    val grantedBy: String,
    val createdAt: Long,
    val revokedBy: String?,
    val revokedAt: Long?,
)
