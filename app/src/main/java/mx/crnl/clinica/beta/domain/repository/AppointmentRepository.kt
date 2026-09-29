package mx.crnl.clinica.beta.domain.repository

import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.domain.model.AppointmentSummary

interface AppointmentRepository {
    /** Todas las citas, de la más antigua a la más reciente. */
    fun observeAppointments(): Flow<List<AppointmentSummary>>
}
