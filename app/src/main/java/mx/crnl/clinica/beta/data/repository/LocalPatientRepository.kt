package mx.crnl.clinica.beta.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mx.crnl.clinica.beta.data.local.dao.PatientDao
import mx.crnl.clinica.beta.data.local.mapper.toDomain
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.repository.PatientRepository

class LocalPatientRepository(private val patientDao: PatientDao) : PatientRepository {
    override fun observePatients(): Flow<List<Patient>> =
        patientDao.observeAll().map { patients -> patients.map { it.toDomain() } }

    override suspend fun getPatient(patientId: String): Patient? = patientDao.getById(patientId)?.toDomain()
}
