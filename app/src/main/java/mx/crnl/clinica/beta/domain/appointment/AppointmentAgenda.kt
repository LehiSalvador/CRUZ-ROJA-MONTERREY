package mx.crnl.clinica.beta.domain.appointment

import java.time.Instant
import java.time.ZoneId
import mx.crnl.clinica.beta.core.util.ClinicTime
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.OPEN_APPOINTMENT_STATUSES

enum class AgendaFilter { UPCOMING, TODAY, HISTORY }

/**
 * Reparte las citas de la agenda. Una cita en curso todavía es próxima y solo pasa al historial cuando termina;
 * las canceladas, realizadas o sin asistencia van siempre al historial (nunca se borran). «Hoy» es la agenda del día
 * de Monterrey: incluye lo ya atendido y deja fuera lo cancelado.
 */
object AppointmentAgenda {
    fun select(
        appointments: List<AppointmentSummary>,
        filter: AgendaFilter,
        now: Instant,
        zone: ZoneId = ClinicTime.zone,
    ): List<AppointmentSummary> = when (filter) {
        AgendaFilter.UPCOMING -> appointments.filter { it.isUpcoming(now) }.sortedBy { it.start }
        AgendaFilter.HISTORY -> appointments.filterNot { it.isUpcoming(now) }.sortedByDescending { it.start }
        AgendaFilter.TODAY -> {
            val today = now.atZone(zone).toLocalDate()
            appointments
                .filter { it.status != AppointmentStatus.CANCELLED && it.start.atZone(zone).toLocalDate() == today }
                .sortedBy { it.start }
        }
    }

    private fun AppointmentSummary.isUpcoming(now: Instant): Boolean = status in OPEN_APPOINTMENT_STATUSES && end >= now
}
