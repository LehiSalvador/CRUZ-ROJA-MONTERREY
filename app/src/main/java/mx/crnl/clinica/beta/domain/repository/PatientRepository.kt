package mx.crnl.clinica.beta.domain.repository

import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.domain.model.DuplicateCandidate
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.model.PatientDetail
import mx.crnl.clinica.beta.domain.model.PatientDraft
import mx.crnl.clinica.beta.domain.model.PatientSummary

sealed interface PatientFilter {
    data object All : PatientFilter

    /** Pacientes con una asignación vigente al profesional indicado. */
    data class AssignedTo(val professionalId: String) : PatientFilter
}

class PatientNotFoundException(patientId: String) : Exception("No existe el paciente $patientId")

interface PatientRepository {
    /** Pacientes que cumplen la búsqueda y el filtro, en orden alfabético; una búsqueda vacía no filtra. */
    fun observePatients(query: String = "", filter: PatientFilter = PatientFilter.All): Flow<List<PatientSummary>>

    suspend fun getPatient(patientId: String): Patient?

    /** Expediente de lectura; emite nulo si el paciente no existe. */
    fun observePatientDetail(patientId: String): Flow<PatientDetail?>

    /** Datos editables con los contactos principales vigentes, o nulo si no existe. */
    suspend fun getPatientDraft(patientId: String): PatientDraft?

    /** Posibles coincidencias con pacientes ya registrados; no bloquea nada. */
    suspend fun findDuplicateCandidates(draft: PatientDraft, excludePatientId: String? = null): List<DuplicateCandidate>

    /** Crea el paciente con su folio y sus contactos en una sola transacción, y registra la auditoría. */
    suspend fun createPatient(draft: PatientDraft, actorUserId: String): Patient

    /** Actualiza los datos editables conservando identificador, folio y datos de creación. */
    suspend fun updatePatient(patientId: String, draft: PatientDraft, actorUserId: String): Patient

    suspend fun recordPatientViewed(patientId: String, actorUserId: String)
}
