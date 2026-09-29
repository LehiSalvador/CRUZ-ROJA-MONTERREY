package mx.crnl.clinica.beta.domain.repository

import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.domain.model.Patient

interface PatientRepository {
    fun observePatients(): Flow<List<Patient>>

    suspend fun getPatient(patientId: String): Patient?
}
