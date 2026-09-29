package mx.crnl.clinica.beta.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Bitácora de auditoría, solo de inserción en operación normal. */
@Entity(
    tableName = "audit_entries",
    foreignKeys = [
        ForeignKey(
            entity = DemoUserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["actorUserId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patientId"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["actorUserId"]),
        Index(value = ["patientId"]),
        Index(value = ["occurredAt"]),
    ],
)
data class AuditEntryEntity(
    @PrimaryKey val auditId: String,
    val actorUserId: String?,
    val action: String,
    val entityType: String,
    val entityId: String?,
    val patientId: String?,
    val areaCode: String?,
    val occurredAt: Long,
    val result: String,
    val metadata: String?,
)
