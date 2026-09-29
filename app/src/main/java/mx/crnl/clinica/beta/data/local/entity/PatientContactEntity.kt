package mx.crnl.clinica.beta.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "patient_contacts",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patientId"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["patientId"]),
        Index(value = ["contactValue"]),
    ],
)
data class PatientContactEntity(
    @PrimaryKey val contactId: String,
    val patientId: String,
    val contactType: String,
    val contactValue: String,
    val isPrimary: Boolean,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)
