package mx.crnl.clinica.beta.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Paciente: entidad central. `patientId` es el identificador técnico (UUID) y `patientNumber` el folio
 * humano (CRNL-000001). La edad no se persiste; `birthDate` se guarda en ISO-8601 (yyyy-MM-dd).
 */
@Entity(
    tableName = "patients",
    foreignKeys = [
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
        Index(value = ["patientNumber"], unique = true),
        Index(value = ["paternalSurname", "maternalSurname", "firstName"]),
        Index(value = ["createdBy"]),
        Index(value = ["updatedBy"]),
    ],
)
data class PatientEntity(
    @PrimaryKey val patientId: String,
    val patientNumber: String,
    val firstName: String,
    val paternalSurname: String,
    val maternalSurname: String?,
    val birthDate: String,
    val birthPlace: String?,
    val sexCode: String,
    val municipality: String?,
    val populationTypeCode: String,
    val status: String,
    val createdAt: Long,
    val createdBy: String,
    val updatedAt: Long,
    val updatedBy: String,
)
