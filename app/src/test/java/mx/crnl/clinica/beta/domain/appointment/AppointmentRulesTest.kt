package mx.crnl.clinica.beta.domain.appointment

import java.time.Instant
import java.time.ZonedDateTime
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.TestZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppointmentRulesTest {
    private val now: Instant = TestNow.toInstant()

    private fun schedule(start: Instant = now.plusSeconds(3_600), minutes: Int = 50) =
        AppointmentRules.scheduleIssues(start, minutes, now)

    private fun logistics(
        modality: AppointmentModality? = AppointmentModality.IN_PERSON,
        location: String? = "Consultorio 2",
        url: String? = null,
        notes: String? = null,
    ) = AppointmentRules.logisticsIssues(modality, location, url, notes)

    @Test
    fun `una cita futura con duracion normal no tiene problemas`() {
        assertTrue(schedule().isEmpty())
    }

    @Test
    fun `una cita en un pasado evidente se rechaza pero la que acaba de empezar se admite`() {
        val longAgo = now.minusSeconds(3 * 3_600)
        val justStarted = now.minusSeconds(10 * 60)

        assertEquals(AppointmentIssue.IN_THE_PAST, schedule(start = longAgo)[AppointmentField.DATE])
        assertTrue(schedule(start = justStarted).isEmpty())
    }

    @Test
    fun `la duracion debe ser razonable`() {
        assertEquals(AppointmentIssue.TOO_SHORT, schedule(minutes = 0)[AppointmentField.DURATION])
        assertEquals(AppointmentIssue.TOO_SHORT, schedule(minutes = -30)[AppointmentField.DURATION])
        assertEquals(AppointmentIssue.TOO_SHORT, schedule(minutes = 5)[AppointmentField.DURATION])
        assertEquals(AppointmentIssue.TOO_LONG, schedule(minutes = 241)[AppointmentField.DURATION])
        assertTrue(schedule(minutes = 10).isEmpty())
        assertTrue(schedule(minutes = 240).isEmpty())
    }

    @Test
    fun `una cita presencial exige ubicacion y no acepta solo espacios`() {
        assertEquals(AppointmentIssue.REQUIRED, logistics(location = null)[AppointmentField.LOCATION])
        assertEquals(AppointmentIssue.REQUIRED, logistics(location = "   ")[AppointmentField.LOCATION])
        assertTrue(logistics(location = "Consultorio 2").isEmpty())
    }

    @Test
    fun `una cita en linea no exige ubicacion y su enlace es opcional`() {
        assertTrue(logistics(modality = AppointmentModality.ONLINE, location = null, url = null).isEmpty())
        assertTrue(logistics(modality = AppointmentModality.ONLINE, location = null, url = "  ").isEmpty())
    }

    @Test
    fun `el enlace debe ser http o https con servidor`() {
        val online = AppointmentModality.ONLINE
        assertTrue(logistics(online, null, "https://meet.example.org/crnl-cita-02").isEmpty())
        assertTrue(logistics(online, null, "http://meet.example.org/x").isEmpty())
        for (bad in listOf("meet.example.org", "javascript:alert(1)", "ftp://example.org/a", "https://", "https://con espacio.org", "not a url")) {
            assertEquals(bad, AppointmentIssue.INVALID, logistics(online, null, bad)[AppointmentField.MEETING_URL])
        }
        assertEquals(
            AppointmentIssue.TOO_LONG,
            logistics(online, null, "https://example.org/" + "a".repeat(600))[AppointmentField.MEETING_URL],
        )
    }

    @Test
    fun `una modalidad ausente y notas demasiado largas se reportan`() {
        assertEquals(AppointmentIssue.REQUIRED, logistics(modality = null)[AppointmentField.MODALITY])
        assertEquals(AppointmentIssue.TOO_LONG, logistics(notes = "n".repeat(501))[AppointmentField.NOTES])
        assertTrue(logistics(notes = "n".repeat(500)).isEmpty())
    }

    @Test
    fun `la ubicacion demasiado larga se rechaza`() {
        assertEquals(AppointmentIssue.TOO_LONG, logistics(location = "u".repeat(121))[AppointmentField.LOCATION])
    }

    @Test
    fun `normalizar recorta, colapsa espacios y descarta lo que no aplica a la modalidad`() {
        val inPerson = AppointmentRules.normalizeLogistics(
            AppointmentModality.IN_PERSON,
            location = "  Consultorio   2 ",
            meetingUrl = "https://meet.example.org/x",
            notes = "   ",
        )
        assertEquals("Consultorio 2", inPerson.location)
        assertNull(inPerson.meetingUrl)
        assertNull(inPerson.administrativeNotes)

        val online = AppointmentRules.normalizeLogistics(
            AppointmentModality.ONLINE,
            location = "Consultorio 2",
            meetingUrl = " https://meet.example.org/x ",
            notes = " Traer credencial ",
        )
        assertNull(online.location)
        assertEquals("https://meet.example.org/x", online.meetingUrl)
        assertEquals("Traer credencial", online.administrativeNotes)
    }

    @Test
    fun `el analizador convierte dia y hora capturados en un instante de Monterrey`() {
        val result = AppointmentScheduleParser.parse("30092026", "1030") as ScheduleParseResult.Parsed

        assertEquals(ZonedDateTime.of(2026, 9, 30, 10, 30, 0, 0, TestZone).toInstant(), result.start)
    }

    @Test
    fun `el analizador reporta fecha y hora vacias, incompletas o imposibles`() {
        fun issues(date: String, time: String) =
            (AppointmentScheduleParser.parse(date, time) as ScheduleParseResult.Invalid).issues

        assertEquals(AppointmentIssue.REQUIRED, issues("", "1030")[AppointmentField.DATE])
        assertEquals(AppointmentIssue.REQUIRED, issues("30092026", "")[AppointmentField.TIME])
        assertEquals(AppointmentIssue.INVALID, issues("3009", "1030")[AppointmentField.DATE])
        assertEquals(AppointmentIssue.INVALID, issues("31022026", "1030")[AppointmentField.DATE])
        assertEquals(AppointmentIssue.INVALID, issues("30092026", "2560")[AppointmentField.TIME])
        assertEquals(AppointmentIssue.INVALID, issues("30092026", "103")[AppointmentField.TIME])
        assertEquals(setOf(AppointmentField.DATE, AppointmentField.TIME), issues("", "").keys)
    }
}
