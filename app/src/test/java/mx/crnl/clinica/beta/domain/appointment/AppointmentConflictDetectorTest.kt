package mx.crnl.clinica.beta.domain.appointment

import java.time.Instant
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.testing.TestNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppointmentConflictDetectorTest {
    private val base: Instant = TestNow.toInstant()

    private fun slot(
        id: String,
        patient: String = "patient-a",
        professional: String = "pro-a",
        startMinutes: Long = 0,
        minutes: Long = 50,
        status: AppointmentStatus = AppointmentStatus.SCHEDULED,
    ) = ScheduleSlot(
        appointmentId = id,
        patientId = patient,
        professionalId = professional,
        start = base.plusSeconds(startMinutes * 60),
        end = base.plusSeconds((startMinutes + minutes) * 60),
        status = status,
    )

    private fun detect(
        existing: List<ScheduleSlot>,
        patient: String = "patient-a",
        professional: String = "pro-a",
        startMinutes: Long = 0,
        minutes: Long = 50,
        exclude: String? = null,
    ) = AppointmentConflictDetector.detect(
        patientId = patient,
        professionalId = professional,
        start = base.plusSeconds(startMinutes * 60),
        end = base.plusSeconds((startMinutes + minutes) * 60),
        existing = existing,
        excludeAppointmentId = exclude,
    )

    @Test
    fun `mismo profesional con horario solapado es conflicto de profesional`() {
        val conflicts = detect(listOf(slot("x", patient = "otro", startMinutes = 30)))

        assertEquals(listOf(ConflictKind.PROFESSIONAL), conflicts.map { it.kind })
        assertEquals("x", conflicts.single().slot.appointmentId)
    }

    @Test
    fun `mismo paciente con horario solapado es conflicto de paciente aunque cambie el profesional`() {
        val conflicts = detect(listOf(slot("x", professional = "otro", startMinutes = -20)))

        assertEquals(listOf(ConflictKind.PATIENT), conflicts.map { it.kind })
    }

    @Test
    fun `una cita que comparte paciente y profesional reporta ambos conflictos`() {
        val conflicts = detect(listOf(slot("x", startMinutes = 10)))

        assertEquals(setOf(ConflictKind.PROFESSIONAL, ConflictKind.PATIENT), conflicts.map { it.kind }.toSet())
    }

    @Test
    fun `citas contiguas no chocan`() {
        val before = slot("antes", startMinutes = -50, minutes = 50)
        val after = slot("despues", startMinutes = 50, minutes = 50)

        assertTrue(detect(listOf(before, after)).isEmpty())
    }

    @Test
    fun `un solape de un solo minuto si es conflicto`() {
        val conflicts = detect(listOf(slot("x", startMinutes = 49)))

        assertEquals(1, conflicts.map { it.slot.appointmentId }.distinct().size)
    }

    @Test
    fun `una cita que contiene a la nueva o esta contenida en ella choca`() {
        assertEquals(listOf("grande"), detect(listOf(slot("grande", patient = "otro", startMinutes = -30, minutes = 120))).map { it.slot.appointmentId })
        assertEquals(listOf("chica"), detect(listOf(slot("chica", patient = "otro", startMinutes = 10, minutes = 10))).map { it.slot.appointmentId })
    }

    @Test
    fun `las citas canceladas se ignoran`() {
        assertTrue(detect(listOf(slot("x", status = AppointmentStatus.CANCELLED))).isEmpty())
    }

    @Test
    fun `una cita realizada o sin asistencia fuera del rango no bloquea, dentro del rango si`() {
        val far = slot("lejos", startMinutes = -600, status = AppointmentStatus.COMPLETED)
        val farNoShow = slot("lejos2", startMinutes = 600, status = AppointmentStatus.NO_SHOW)
        val inside = slot("dentro", startMinutes = 5, status = AppointmentStatus.NO_SHOW)

        assertTrue(detect(listOf(far, farNoShow)).isEmpty())
        assertEquals(1, detect(listOf(inside)).map { it.slot.appointmentId }.distinct().size)
    }

    @Test
    fun `personas distintas en el mismo horario no chocan`() {
        assertTrue(detect(listOf(slot("x", patient = "otro-paciente", professional = "otro-pro"))).isEmpty())
    }

    @Test
    fun `al reprogramar una cita no choca consigo misma`() {
        val own = slot("propia", startMinutes = 0)

        assertTrue(detect(listOf(own), exclude = "propia").isEmpty())
        assertEquals(2, detect(listOf(own)).size)
    }
}
