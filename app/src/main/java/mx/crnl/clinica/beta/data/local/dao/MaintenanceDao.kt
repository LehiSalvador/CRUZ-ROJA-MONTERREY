package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Vaciado de la base local de la Beta, usado solo al restablecer los datos ficticios. Borra tabla por tabla, de las
 * dependientes a las referenciadas, porque las claves foráneas restringen el borrado; una prueba comprueba que no
 * quede ninguna tabla del esquema fuera de esta lista.
 */
@Dao
abstract class MaintenanceDao {
    @Query("DELETE FROM access_grants")
    protected abstract suspend fun deleteAccessGrants()

    @Query("DELETE FROM professional_override_requests")
    protected abstract suspend fun deleteOverrideRequests()

    @Query("DELETE FROM interarea_access_requests")
    protected abstract suspend fun deleteAccessRequests()

    @Query("DELETE FROM assessment_results")
    protected abstract suspend fun deleteAssessmentResults()

    @Query("DELETE FROM assessments")
    protected abstract suspend fun deleteAssessments()

    @Query("DELETE FROM clinical_encounters")
    protected abstract suspend fun deleteEncounters()

    @Query("DELETE FROM appointments")
    protected abstract suspend fun deleteAppointments()

    @Query("DELETE FROM professional_assignments")
    protected abstract suspend fun deleteAssignments()

    @Query("DELETE FROM patient_contacts")
    protected abstract suspend fun deleteContacts()

    @Query("DELETE FROM audit_entries")
    protected abstract suspend fun deleteAuditEntries()

    @Query("DELETE FROM patients")
    protected abstract suspend fun deletePatients()

    @Query("DELETE FROM demo_credentials")
    protected abstract suspend fun deleteCredentials()

    @Query("DELETE FROM demo_users")
    protected abstract suspend fun deleteUsers()

    @Transaction
    open suspend fun deleteAllRows() {
        deleteAccessGrants()
        deleteOverrideRequests()
        deleteAccessRequests()
        deleteAssessmentResults()
        deleteAssessments()
        deleteEncounters()
        deleteAppointments()
        deleteAssignments()
        deleteContacts()
        deleteAuditEntries()
        deletePatients()
        deleteCredentials()
        deleteUsers()
    }
}
