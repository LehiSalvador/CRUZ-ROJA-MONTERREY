package mx.crnl.clinica.beta.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import mx.crnl.clinica.beta.data.local.dao.AppointmentDao
import mx.crnl.clinica.beta.data.local.dao.PatientDao
import mx.crnl.clinica.beta.data.local.dao.SeedDao
import mx.crnl.clinica.beta.data.local.entity.AccessRequestEntity
import mx.crnl.clinica.beta.data.local.entity.AppointmentEntity
import mx.crnl.clinica.beta.data.local.entity.AssessmentEntity
import mx.crnl.clinica.beta.data.local.entity.AssessmentResultEntity
import mx.crnl.clinica.beta.data.local.entity.AuditEntryEntity
import mx.crnl.clinica.beta.data.local.entity.ClinicalEncounterEntity
import mx.crnl.clinica.beta.data.local.entity.DemoUserEntity
import mx.crnl.clinica.beta.data.local.entity.PatientContactEntity
import mx.crnl.clinica.beta.data.local.entity.PatientEntity
import mx.crnl.clinica.beta.data.local.entity.ProfessionalAssignmentEntity

@Database(
    entities = [
        DemoUserEntity::class,
        PatientEntity::class,
        PatientContactEntity::class,
        ProfessionalAssignmentEntity::class,
        AppointmentEntity::class,
        ClinicalEncounterEntity::class,
        AssessmentEntity::class,
        AssessmentResultEntity::class,
        AccessRequestEntity::class,
        AuditEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ClinicalDatabase : RoomDatabase() {
    abstract fun seedDao(): SeedDao

    abstract fun patientDao(): PatientDao

    abstract fun appointmentDao(): AppointmentDao

    companion object {
        const val FILE_NAME = "clinical_beta.db"

        fun create(context: Context): ClinicalDatabase =
            Room.databaseBuilder(context.applicationContext, ClinicalDatabase::class.java, FILE_NAME).build()
    }
}
