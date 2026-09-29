package mx.crnl.clinica.beta.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cita administrativa; distinta del encuentro clínico, que registra lo ocurrido realmente. Fechas en UTC (epoch ms). */
@Entity(
    tableName = "appointments",
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
            childColumns = ["createdBy"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["professionalId"]),
        Index(value = ["startDateTime"]),
        Index(value = ["createdBy"]),
    ],
)
data class AppointmentEntity(
    @PrimaryKey val appointmentId: String,
    val patientId: String,
    val areaCode: String,
    val professionalId: String,
    val startDateTime: Long,
    val endDateTime: Long,
    val modality: String,
    val location: String?,
    val meetingUrl: String?,
    val status: String,
    val administrativeNotes: String?,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
)
