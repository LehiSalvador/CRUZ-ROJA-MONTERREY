package mx.crnl.clinica.beta.domain.contact

import java.time.ZonedDateTime
import mx.crnl.clinica.beta.testing.TestZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppointmentContactMessageBuilderTest {
    private val start = ZonedDateTime.of(2026, 9, 30, 10, 0, 0, 0, TestZone).toInstant()

    @Test
    fun `el mensaje es neutro y trae nombre, fecha y hora`() {
        val message = AppointmentContactMessageBuilder.build(patientFirstName = "Ana Lucía", start = start)

        assertEquals(
            "Hola Ana Lucía. Te contactamos de Cruz Roja Nuevo León para dar seguimiento a tu cita del " +
                "30 de septiembre de 2026 a las 10:00.",
            message,
        )
    }

    @Test
    fun `el mensaje no menciona area clinica ni datos clinicos`() {
        val message = AppointmentContactMessageBuilder.build("Ana", start).lowercase()

        for (forbidden in listOf(
            "psicolog", "nutrici", "medicina", "diagn", "resultado", "evaluaci", "motivo", "nota", "terapia",
            "consulta", "tratamiento", "crnl-", "folio", "profesional",
        )) {
            assertFalse("no debe contener «$forbidden»", message.contains(forbidden))
        }
    }

    @Test
    fun `la hora se presenta en zona de Monterrey aunque el instante sea UTC`() {
        val utcNoon = java.time.Instant.parse("2026-09-30T16:30:00Z")

        assertTrue(AppointmentContactMessageBuilder.build("Ana", utcNoon).endsWith("a las 10:30."))
    }

    @Test
    fun `un telefono nacional de diez digitos recibe la lada de Mexico`() {
        assertEquals("528112345678", PhoneNumbers.whatsAppNumber("8112345678"))
        assertEquals("528112345678", PhoneNumbers.whatsAppNumber("(81) 1234-5678"))
        assertEquals("528112345678", PhoneNumbers.whatsAppNumber("81 1234 5678"))
    }

    @Test
    fun `un telefono ya internacional se conserva y el prefijo movil antiguo se normaliza`() {
        assertEquals("528112345678", PhoneNumbers.whatsAppNumber("+52 81 1234 5678"))
        assertEquals("528112345678", PhoneNumbers.whatsAppNumber("528112345678"))
        assertEquals("528112345678", PhoneNumbers.whatsAppNumber("+52 1 81 1234 5678"))
    }

    @Test
    fun `un telefono ausente o invalido no genera numero`() {
        assertNull(PhoneNumbers.whatsAppNumber(null))
        assertNull(PhoneNumbers.whatsAppNumber(""))
        assertNull(PhoneNumbers.whatsAppNumber("   "))
        assertNull(PhoneNumbers.whatsAppNumber("12345"))
        assertNull(PhoneNumbers.whatsAppNumber("sin telefono"))
        assertNull(PhoneNumbers.whatsAppNumber("+1 415 555 2671"))
    }

    @Test
    fun `el enlace usa el esquema de WhatsApp y codifica el texto sin caracteres peligrosos`() {
        val link = WhatsAppLink.create("8112345678", "Hola Ana & Luis. ¿Cita a las 10:00? 100%")!!

        assertTrue(link.startsWith("whatsapp://send?phone=528112345678&text="))
        val query = link.substringAfter("&text=")
        assertFalse(query.contains(' '))
        assertFalse(query.contains('&'))
        assertFalse(query.contains('¿'))
        assertTrue(query.contains("%20"))
        assertTrue(query.contains("%26"))
        assertTrue(query.contains("%25"))
        assertEquals("Hola Ana & Luis. ¿Cita a las 10:00? 100%", java.net.URLDecoder.decode(query, "UTF-8"))
    }

    @Test
    fun `sin telefono valido no hay enlace`() {
        assertNull(WhatsAppLink.create(null, "Hola"))
        assertNull(WhatsAppLink.create("123", "Hola"))
    }

    @Test
    fun `el enlace nunca lleva un envio automatico ni el telefono en el texto`() {
        val link = WhatsAppLink.create("8112345678", AppointmentContactMessageBuilder.build("Ana", start))!!

        assertFalse(link.substringAfter("&text=").contains("52811"))
    }
}
