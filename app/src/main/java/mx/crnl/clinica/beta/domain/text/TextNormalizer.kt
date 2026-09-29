package mx.crnl.clinica.beta.domain.text

import java.text.Normalizer
import java.util.Locale

/** Formas canónicas con las que se comparan nombres, teléfonos y correos; nunca se usan para almacenar. */
object TextNormalizer {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespace = Regex("\\s+")

    /** Minúsculas, sin diacríticos y con los espacios colapsados: "Treviño  Ávila" y "trevino avila" coinciden. */
    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .replace(whitespace, " ")
            .trim()

    /** Solo dígitos; con lada o prefijo de país (más de 10 dígitos) se conservan los 10 últimos. */
    fun phoneDigits(text: String): String {
        val digits = text.filter(Char::isDigit)
        return if (digits.length > NATIONAL_PHONE_DIGITS) digits.takeLast(NATIONAL_PHONE_DIGITS) else digits
    }

    fun email(text: String): String = text.trim().lowercase(Locale.ROOT)

    /** Recorta y colapsa espacios conservando mayúsculas y acentos: es la forma que sí se almacena. */
    fun tidy(text: String): String = text.trim().replace(whitespace, " ")

    private const val NATIONAL_PHONE_DIGITS = 10
}
