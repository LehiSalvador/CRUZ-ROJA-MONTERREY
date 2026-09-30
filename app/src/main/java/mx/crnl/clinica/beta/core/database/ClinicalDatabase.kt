package mx.crnl.clinica.beta.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import mx.crnl.clinica.beta.data.local.dao.AccessGrantDao
import mx.crnl.clinica.beta.data.local.dao.AccessRequestDao
import mx.crnl.clinica.beta.data.local.dao.AppointmentDao
import mx.crnl.clinica.beta.data.local.dao.AssessmentDao
import mx.crnl.clinica.beta.data.local.dao.AuditDao
import mx.crnl.clinica.beta.data.local.dao.CredentialDao
import mx.crnl.clinica.beta.data.local.dao.EncounterDao
import mx.crnl.clinica.beta.data.local.dao.MaintenanceDao
import mx.crnl.clinica.beta.data.local.dao.PatientDao
import mx.crnl.clinica.beta.data.local.dao.PatientDetailDao
import mx.crnl.clinica.beta.data.local.dao.ProfessionalAssignmentDao
import mx.crnl.clinica.beta.data.local.dao.ProfessionalOverrideRequestDao
import mx.crnl.clinica.beta.data.local.dao.SeedDao
import mx.crnl.clinica.beta.data.local.dao.UserDao
import mx.crnl.clinica.beta.data.local.entity.AccessGrantEntity
import mx.crnl.clinica.beta.data.local.entity.AccessRequestEntity
import mx.crnl.clinica.beta.data.local.entity.AppointmentEntity
import mx.crnl.clinica.beta.data.local.entity.AssessmentEntity
import mx.crnl.clinica.beta.data.local.entity.AssessmentResultEntity
import mx.crnl.clinica.beta.data.local.entity.AuditEntryEntity
import mx.crnl.clinica.beta.data.local.entity.ClinicalEncounterEntity
import mx.crnl.clinica.beta.data.local.entity.DemoCredentialEntity
import mx.crnl.clinica.beta.data.local.entity.DemoUserEntity
import mx.crnl.clinica.beta.data.local.entity.PatientContactEntity
import mx.crnl.clinica.beta.data.local.entity.PatientEntity
import mx.crnl.clinica.beta.data.local.entity.ProfessionalAssignmentEntity
import mx.crnl.clinica.beta.data.local.entity.ProfessionalOverrideRequestEntity

@Database(
    entities = [
        DemoUserEntity::class,
        DemoCredentialEntity::class,
        PatientEntity::class,
        PatientContactEntity::class,
        ProfessionalAssignmentEntity::class,
        AppointmentEntity::class,
        ClinicalEncounterEntity::class,
        AssessmentEntity::class,
        AssessmentResultEntity::class,
        AccessRequestEntity::class,
        AccessGrantEntity::class,
        ProfessionalOverrideRequestEntity::class,
        AuditEntryEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class ClinicalDatabase : RoomDatabase() {
    abstract fun seedDao(): SeedDao

    abstract fun userDao(): UserDao

    abstract fun credentialDao(): CredentialDao

    abstract fun auditDao(): AuditDao

    abstract fun patientDao(): PatientDao

    abstract fun patientDetailDao(): PatientDetailDao

    abstract fun appointmentDao(): AppointmentDao

    abstract fun assignmentDao(): ProfessionalAssignmentDao

    abstract fun encounterDao(): EncounterDao

    abstract fun accessRequestDao(): AccessRequestDao

    abstract fun accessGrantDao(): AccessGrantDao

    abstract fun overrideRequestDao(): ProfessionalOverrideRequestDao

    abstract fun assessmentDao(): AssessmentDao

    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        const val FILE_NAME = "clinical_beta.db"

        fun create(context: Context): ClinicalDatabase =
            Room.databaseBuilder(context.applicationContext, ClinicalDatabase::class.java, FILE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
