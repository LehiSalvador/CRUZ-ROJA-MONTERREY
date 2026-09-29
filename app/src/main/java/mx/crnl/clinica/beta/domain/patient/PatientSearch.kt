package mx.crnl.clinica.beta.domain.patient

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import mx.crnl.clinica.beta.domain.model.ContactType
import mx.crnl.clinica.beta.domain.model.PatientRecord
import mx.crnl.clinica.beta.domain.text.TextNormalizer

/**
 * Búsqueda de pacientes por folio, nombre, apellidos, teléfono, correo y fecha de nacimiento.
 * El tipo de consulta se deduce del propio texto para que cada criterio sea predecible:
 * una fecha completa, un correo (contiene «@»), un número (folio o teléfono), un folio con prefijo
 * o, en cualquier otro caso, palabras del nombre sin distinguir mayúsculas ni acentos.
 */
object PatientSearch {
    fun filter(records: List<PatientRecord>, rawQuery: String): List<PatientRecord> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return records
        parseBirthDate(query)?.let { date -> return records.filter { it.patient.birthDate == date } }
        return when {
            query.contains('@') -> records.filter { matchesEmail(it, query) }
            NUMERIC_QUERY.matches(query) -> filterByNumber(records, query)
            FOLIO_QUERY.matches(query) -> records.filter { matchesFolio(it, query) }
            else -> records.filter { matchesName(it, query) }
        }
    }

    /** Acepta día/mes/año con «/», «-» o «.», y también el formato interno año-mes-día. */
    fun parseBirthDate(text: String): LocalDate? =
        DATE_FORMATS.firstNotNullOfOrNull { formatter ->
            try {
                LocalDate.parse(text.trim(), formatter)
            } catch (_: DateTimeParseException) {
                null
            }
        }

    private fun matchesEmail(record: PatientRecord, query: String): Boolean {
        val wanted = TextNormalizer.email(query)
        return record.contacts.any { it.type == ContactType.EMAIL && TextNormalizer.email(it.value).contains(wanted) }
    }

    // Un número que es el folio de alguien se busca solo como folio: los mismos dígitos dentro de un teléfono
    // («000001» dentro de +52 81 0000 0102) serían ruido. Si nadie tiene ese folio y son cuatro dígitos o más,
    // se busca dentro de los teléfonos, por ejemplo los últimos dígitos.
    private fun filterByNumber(records: List<PatientRecord>, query: String): List<PatientRecord> {
        val digits = query.filter(Char::isDigit)
        if (digits.isEmpty()) return emptyList()
        val byFolio = records.filter { folioSequence(it) == digits.padStart(FOLIO_DIGITS, '0') }
        if (byFolio.isNotEmpty() || digits.length < MIN_PHONE_QUERY_DIGITS) return byFolio
        return records.filter { record ->
            record.contacts.any { it.type == ContactType.PHONE && it.value.filter(Char::isDigit).contains(digits) }
        }
    }

    private fun matchesFolio(record: PatientRecord, query: String): Boolean {
        val digits = query.filter(Char::isDigit)
        if (digits.isEmpty()) return true
        val sequence = folioSequence(record)
        return sequence == digits.padStart(FOLIO_DIGITS, '0') || sequence.startsWith(digits)
    }

    private fun folioSequence(record: PatientRecord): String = record.patient.patientNumber.substringAfter('-')

    // Los fragmentos de una o dos letras solo cuentan como inicio de palabra: «an» encuentra a Ana pero no a Diana.
    private fun matchesName(record: PatientRecord, query: String): Boolean {
        val words = TextNormalizer.fold(record.patient.fullName).split(' ')
        val tokens = TextNormalizer.fold(query).split(' ').filter(String::isNotEmpty)
        if (tokens.isEmpty()) return true
        val nameMatches = tokens.all { token ->
            if (token.length >= MIN_SUBSTRING_LENGTH) words.any { it.contains(token) } else words.any { it.startsWith(token) }
        }
        if (nameMatches) return true
        return tokens.size == 1 && record.contacts.any {
            it.type == ContactType.EMAIL && TextNormalizer.email(it.value).substringBefore('@').startsWith(tokens.single())
        }
    }

    private val NUMERIC_QUERY = Regex("^[+(\\d][\\d\\s()+.-]*$")
    private val FOLIO_QUERY = Regex("^(?i)crnl[-\\s]?\\d*$")
    private const val FOLIO_DIGITS = 6
    private const val MIN_PHONE_QUERY_DIGITS = 4
    private const val MIN_SUBSTRING_LENGTH = 3

    private val DATE_FORMATS: List<DateTimeFormatter> = listOf("d/M/uuuu", "d-M-uuuu", "d.M.uuuu", "uuuu-M-d")
        .map { DateTimeFormatter.ofPattern(it).withResolverStyle(ResolverStyle.STRICT) }
}
