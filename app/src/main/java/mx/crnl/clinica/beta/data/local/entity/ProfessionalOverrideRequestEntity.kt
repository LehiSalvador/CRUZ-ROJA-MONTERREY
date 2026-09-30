package mx.crnl.clinica.beta.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Solicitud formal de cambio de profesional en un área. Aprobarla cierra la asignación vigente y abre otra; la
 * solicitud conserva cuál era la vigente y quién la pidió, y nunca se borra.
 */
@Entity(
    tableName = "professional_override_requests",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patientId"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ProfessionalAssignmentEntity::class,
            parentColumns = ["assignmentId"],
            childColumns = ["currentAssignmentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = DemoUserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["currentProfessionalId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = DemoUserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["requestedProfessionalId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = DemoUserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["requestedBy"],
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
        Index(value = ["patientId", "areaCode", "status"]),
        Index(value = ["currentAssignmentId"]),
        Index(value = ["currentProfessionalId"]),
        Index(value = ["requestedProfessionalId"]),
        Index(value = ["requestedBy"]),
        Index(value = ["reviewedBy"]),
        Index(value = ["status"]),
    ],
)
data class ProfessionalOverrideRequestEntity(
    @PrimaryKey val overrideRequestId: String,
    val patientId: String,
    val areaCode: String,
    val currentAssignmentId: String,
    val currentProfessionalId: String,
    val requestedProfessionalId: String,
    val requestedBy: String,
    val reason: String,
    val requestedAt: Long,
    val status: String,
    val reviewedBy: String?,
    val reviewedAt: Long?,
    val resolutionReason: String?,
)
