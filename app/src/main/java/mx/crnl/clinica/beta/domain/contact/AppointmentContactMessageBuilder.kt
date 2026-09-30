package mx.crnl.clinica.beta.domain.contact

import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import mx.crnl.clinica.beta.core.util.ClinicTime

/**
 * Texto que se ofrece al contactar a una persona por su cita. Es deliberadamente neutro: sin área clínica,
 * folio, profesional, motivo ni ningún dato clínico. Nada se envía automáticamente: la persona lo revisa y lo
 * manda desde la aplicación externa.
 */
object AppointmentContactMessageBuilder {
    private val dateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu", ClinicTime.locale)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", ClinicTime.locale)

    fun build(patientFirstName: String, start: Instant): String {
        val local = start.atZone(ClinicTime.zone)
        return "Hola $patientFirstName. Te contactamos de Cruz Roja Nuevo León para dar seguimiento a tu cita del " +
            "${dateFormatter.format(local)} a las ${timeFormatter.format(local)}."
    }
}

object PhoneNumbers {
    private const val NATIONAL_DIGITS = 10
    private const val COUNTRY_CODE = "52"

    /**
     * Número en el formato que espera WhatsApp (código de país sin «+»). Solo se aceptan teléfonos nacionales de
     * México (10 dígitos, con o sin 52 y con el «1» móvil antiguo); cualquier otra cosa se considera no válida.
     */
    fun whatsAppNumber(raw: String?): String? {
        val digits = raw.orEmpty().filter(Char::isDigit)
        return when {
            digits.length == NATIONAL_DIGITS -> COUNTRY_CODE + digits
            digits.length == NATIONAL_DIGITS + 2 && digits.startsWith(COUNTRY_CODE) -> digits
            digits.length == NATIONAL_DIGITS + 3 && digits.startsWith(COUNTRY_CODE + "1") -> COUNTRY_CODE + digits.takeLast(NATIONAL_DIGITS)
            else -> null
        }
    }
}

object WhatsAppLink {
    // La sobrecarga que recibe un Charset exige API 33; el nombre del juego de caracteres funciona desde la 1.
    private const val UTF_8_NAME = "UTF-8"

    /**
     * Enlace `whatsapp://send` que solo abre la aplicación de WhatsApp (sin navegador, sin red desde esta app). Nulo si el
     * teléfono no es utilizable.
     */
    fun create(rawPhone: String?, message: String): String? {
        val number = PhoneNumbers.whatsAppNumber(rawPhone) ?: return null
        // URLEncoder usa «+» para el espacio; el esquema de WhatsApp lo lee literal, por eso se pide %20.
        val text = URLEncoder.encode(message, UTF_8_NAME).replace("+", "%20")
        return "whatsapp://send?phone=$number&text=$text"
    }
}
