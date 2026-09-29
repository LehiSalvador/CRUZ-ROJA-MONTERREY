package mx.crnl.clinica.beta.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Resultado calculado por el sistema; una aplicación tiene como máximo un resultado. */
@Entity(
    tableName = "assessment_results",
    foreignKeys = [
        ForeignKey(
            entity = AssessmentEntity::class,
            parentColumns = ["assessmentId"],
            childColumns = ["assessmentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["assessmentId"], unique = true)],
)
data class AssessmentResultEntity(
    @PrimaryKey val assessmentResultId: String,
    val assessmentId: String,
    val rawScore: Double,
    val normalizedScore: Double?,
    val classificationCode: String,
    val classificationLabel: String,
    val scoringVersion: String,
    val calculatedAt: Long,
)
