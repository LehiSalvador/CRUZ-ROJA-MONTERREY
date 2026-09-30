package mx.crnl.clinica.beta.domain.appointment

import java.time.Duration
import java.time.Instant
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.TestZone
import mx.crnl.clinica.beta.testing.domainAppointment
import org.junit.Assert.assertEquals
import org.junit.Test

class AppointmentAgendaTest {
    private val now: Instant = TestNow.toInstant() // 2026-09-29 10:00 Monterrey

    private fun at(hoursFromNow: Long) = now.plus(Duration.ofHours(hoursFromNow))

    private fun appointment(id: String, start: Instant, status: AppointmentStatus = AppointmentStatus.SCHEDULED, minutes: Long = 50) =
        domainAppointment(id = id, start = start, status = status, minutes = minutes)

    private fun ids(list: List<AppointmentSummary>) = list.map { it.appointmentId }

    private fun select(filter: AgendaFilter, vararg appointments: AppointmentSummary) =
        ids(AppointmentAgenda.select(appointments.toList(), filter, now, TestZone))

    @Test
    fun `proximas son las abiertas que no han terminado, de la mas cercana a la mas lejana`() {
        val result = select(
            AgendaFilter.UPCOMING,
            appointment("lejana", at(240)),
            appointment("cercana", at(3)),
            appointment("en-curso", now.minusSeconds(20 * 60)),
            appointment("terminada", now.minusSeconds(51 * 60)),
        )

        assertEquals(listOf("en-curso", "cercana", "lejana"), result)
    }

    @Test
    fun `una cita reprogramada sigue siendo proxima`() {
        assertEquals(
            listOf("rep"),
            select(AgendaFilter.UPCOMING, appointment("rep", at(48), AppointmentStatus.RESCHEDULED)),
        )
    }

    @Test
    fun `las citas canceladas, realizadas o sin asistencia van al historial aunque sean futuras`() {
        val cancelled = appointment("cancelada", at(5), AppointmentStatus.CANCELLED)
        val noShow = appointment("no-asistio", at(6), AppointmentStatus.NO_SHOW)
        val completed = appointment("realizada", at(7), AppointmentStatus.COMPLETED)

        assertEquals(emptyList<String>(), select(AgendaFilter.UPCOMING, cancelled, noShow, completed))
        assertEquals(setOf("cancelada", "no-asistio", "realizada"), select(AgendaFilter.HISTORY, cancelled, noShow, completed).toSet())
    }

    @Test
    fun `el historial ordena de la mas reciente a la mas antigua e incluye abiertas ya vencidas`() {
        val result = select(
            AgendaFilter.HISTORY,
            appointment("vieja", now.minus(Duration.ofDays(20)), AppointmentStatus.COMPLETED),
            appointment("vencida", now.minus(Duration.ofDays(2))),
            appointment("futura", at(24)),
        )

        assertEquals(listOf("vencida", "vieja"), result)
    }

    @Test
    fun `hoy muestra las citas del dia de Monterrey que no se cancelaron, sin importar su estado, por hora`() {
        val result = select(
            AgendaFilter.TODAY,
            appointment("tarde", at(5)), // 15:00
            appointment("manana-temprano", now.minusSeconds(60 * 60), AppointmentStatus.COMPLETED), // 09:00
            appointment("cancelada-hoy", at(1), AppointmentStatus.CANCELLED),
            appointment("ayer", now.minus(Duration.ofDays(1))),
            appointment("manana", now.plus(Duration.ofDays(1))),
        )

        assertEquals(listOf("manana-temprano", "tarde"), result)
    }

    @Test
    fun `el dia se decide en hora de Monterrey y no en UTC`() {
        // 2026-09-29 23:30 en Monterrey ya es 2026-09-30 05:30 UTC: sigue siendo «hoy».
        val lateTonight = java.time.ZonedDateTime.of(2026, 9, 29, 23, 30, 0, 0, TestZone).toInstant()

        assertEquals(listOf("noche"), select(AgendaFilter.TODAY, appointment("noche", lateTonight)))
    }
}
