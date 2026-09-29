package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import mx.crnl.clinica.beta.data.local.entity.AssessmentEntity
import mx.crnl.clinica.beta.data.local.entity.AssessmentResultEntity
import mx.crnl.clinica.beta.data.local.entity.AppointmentEntity
import mx.crnl.clinica.beta.data.local.entity.AuditEntryEntity
import mx.crnl.clinica.beta.data.local.entity.ClinicalEncounterEntity
import mx.crnl.clinica.beta.data.local.entity.DemoUserEntity
import mx.crnl.clinica.beta.data.local.entity.PatientContactEntity
import mx.crnl.clinica.beta.data.local.entity.PatientEntity
import mx.crnl.clinica.beta.data.local.entity.ProfessionalAssignmentEntity

/** Conjunto de registros iniciales a cargar en una sola transacción. */
class SeedRecords(
    val users: List<DemoUserEntity>,
    val patients: List<PatientEntity>,
    val contacts: List<PatientContactEntity>,
    val assignments: List<ProfessionalAssignmentEntity>,
    val appointments: List<AppointmentEntity>,
    val encounters: List<ClinicalEncounterEntity>,
    val assessments: List<AssessmentEntity>,
    val results: List<AssessmentResultEntity>,
    val auditEntries: List<AuditEntryEntity>,
)

/** Filas realmente insertadas por tabla; los registros ya existentes no cuentan. */
data class SeedCounts(
    val users: Int,
    val patients: Int,
    val contacts: Int,
    val assignments: Int,
    val appointments: Int,
    val encounters: Int,
    val assessments: Int,
    val results: Int,
    val auditEntries: Int,
) {
    val total: Int
        get() = users + patients + contacts + assignments + appointments + encounters + assessments + results + auditEntries
}

/**
 * Escrituras del conjunto inicial. Todo entra en una transacción y con IGNORE sobre llaves
 * determinísticas: repetir la carga no puede duplicar ni sobrescribir registros.
 */
@Dao
abstract class SeedDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertUsers(items: List<DemoUserEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertPatients(items: List<PatientEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertContacts(items: List<PatientContactEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertAssignments(items: List<ProfessionalAssignmentEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertAppointments(items: List<AppointmentEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertEncounters(items: List<ClinicalEncounterEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertAssessments(items: List<AssessmentEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertResults(items: List<AssessmentResultEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertAuditEntries(items: List<AuditEntryEntity>): List<Long>

    @Transaction
    open suspend fun insertAll(records: SeedRecords): SeedCounts = SeedCounts(
        users = insertUsers(records.users).inserted(),
        patients = insertPatients(records.patients).inserted(),
        contacts = insertContacts(records.contacts).inserted(),
        assignments = insertAssignments(records.assignments).inserted(),
        appointments = insertAppointments(records.appointments).inserted(),
        encounters = insertEncounters(records.encounters).inserted(),
        assessments = insertAssessments(records.assessments).inserted(),
        results = insertResults(records.results).inserted(),
        auditEntries = insertAuditEntries(records.auditEntries).inserted(),
    )

    // SQLite devuelve rowId -1 cuando IGNORE descarta la fila.
    private fun List<Long>.inserted(): Int = count { it != -1L }
}
