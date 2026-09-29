package mx.crnl.clinica.beta.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Historial de asignaciones: cambiar de profesional cierra la vigente (`endAt`) y crea otra; nunca sobrescribe. */
@Entity(
    tableName = "professional_assignments",
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
            childColumns = ["professionalId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = DemoUserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["assignedBy"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["patientId", "areaCode", "status"]),
        Index(value = ["professionalId"]),
        Index(value = ["assignedBy"]),
    ],
)
data class ProfessionalAssignmentEntity(
    @PrimaryKey val assignmentId: String,
    val patientId: String,
    val areaCode: String,
    val professionalId: String,
    val startAt: Long,
    val endAt: Long?,
    val status: String,
    val reason: String?,
    val assignedBy: String,
    val createdAt: Long,
)
