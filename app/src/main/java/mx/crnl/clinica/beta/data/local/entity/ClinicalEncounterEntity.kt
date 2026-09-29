package mx.crnl.clinica.beta.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** `eventAt` es el momento real de la consulta; `recordedAt`, cuando se capturó en el sistema. */
@Entity(
    tableName = "clinical_encounters",
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
            entity = AppointmentEntity::class,
            parentColumns = ["appointmentId"],
            childColumns = ["appointmentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = DemoUserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["createdBy"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = DemoUserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["updatedBy"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["eventAt"]),
        Index(value = ["professionalId"]),
        Index(value = ["appointmentId"]),
        Index(value = ["createdBy"]),
        Index(value = ["updatedBy"]),
    ],
)
data class ClinicalEncounterEntity(
    @PrimaryKey val encounterId: String,
    val patientId: String,
    val areaCode: String,
    val professionalId: String,
    val appointmentId: String?,
    val encounterTypeCode: String,
    val status: String,
    val eventAt: Long,
    val recordedAt: Long,
    val createdBy: String,
    val updatedAt: Long,
    val updatedBy: String,
)
