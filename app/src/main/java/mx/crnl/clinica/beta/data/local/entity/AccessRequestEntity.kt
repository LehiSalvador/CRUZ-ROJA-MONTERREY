package mx.crnl.clinica.beta.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Solicitud de acceso interárea a información clínica de un paciente. */
@Entity(
    tableName = "interarea_access_requests",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patientId"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = DemoUserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["requesterUserId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = DemoUserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["reviewedBy"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["requesterUserId"]),
        Index(value = ["reviewedBy"]),
        Index(value = ["status"]),
    ],
)
data class AccessRequestEntity(
    @PrimaryKey val accessRequestId: String,
    val patientId: String,
    val requesterUserId: String,
    val requesterAreaCode: String,
    val ownerAreaCode: String,
    val reason: String,
    val requestedScope: String,
    val requestedAt: Long,
    val status: String,
    val reviewedBy: String?,
    val reviewedAt: Long?,
    val expiresAt: Long?,
)
