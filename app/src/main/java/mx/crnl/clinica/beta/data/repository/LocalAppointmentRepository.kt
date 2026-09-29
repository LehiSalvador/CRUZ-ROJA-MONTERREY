package mx.crnl.clinica.beta.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mx.crnl.clinica.beta.data.local.dao.AppointmentDao
import mx.crnl.clinica.beta.data.local.mapper.toDomain
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.repository.AppointmentRepository

class LocalAppointmentRepository(private val appointmentDao: AppointmentDao) : AppointmentRepository {
    override fun observeAppointments(): Flow<List<AppointmentSummary>> =
        appointmentDao.observeRows().map { rows -> rows.map { it.toDomain() } }
}
