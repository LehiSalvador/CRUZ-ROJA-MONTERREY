package mx.crnl.clinica.beta.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Una aplicación concreta de un instrumento; su resultado calculado vive aparte en [AssessmentResultEntity]. */
@Entity(
    tableName = "assessments",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patientId"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ClinicalEncounterEntity::class,
            parentColumns = ["encounterId"],
            childColumns = ["encounterId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = DemoUserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["professionalId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["completedAt"]),
        Index(value = ["encounterId"]),
        Index(value = ["professionalId"]),
    ],
)
data class AssessmentEntity(
    @PrimaryKey val assessmentId: String,
    val patientId: String,
    val encounterId: String?,
    val professionalId: String,
    val instrumentCode: String,
    val instrumentVersion: String,
    val administrationModeCode: String,
    val startedAt: Long,
    val completedAt: Long?,
    val status: String,
)
