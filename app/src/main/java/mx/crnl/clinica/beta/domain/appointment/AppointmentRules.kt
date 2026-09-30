package mx.crnl.clinica.beta.domain.appointment

import java.net.URI
import java.net.URISyntaxException
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import mx.crnl.clinica.beta.core.util.ClinicTime
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.text.TextNormalizer

enum class AppointmentField { PATIENT, AREA, PROFESSIONAL, DATE, TIME, DURATION, MODALITY, LOCATION, MEETING_URL, NOTES }

enum class AppointmentIssue { REQUIRED, INVALID, TOO_LONG, TOO_SHORT, IN_THE_PAST }

/** Datos logísticos ya recortados; lo que no aplica a la modalidad se descarta. */
data class AppointmentLogistics(
    val modality: AppointmentModality,
    val location: String?,
    val meetingUrl: String?,
    val administrativeNotes: String?,
)

/**
 * Reglas de una cita que comparten el formulario y el repositorio: el formulario las usa para señalar cada campo
 * y el repositorio las vuelve a aplicar, porque ocultar un botón no valida nada. No capturan contenido clínico.
 */
object AppointmentRules {
    const val MIN_DURATION_MINUTES = 10
    const val MAX_DURATION_MINUTES = 240
    const val MAX_LOCATION_LENGTH = 120
    const val MAX_URL_LENGTH = 500
    const val MAX_NOTES_LENGTH = 500

    /** Una cita recién iniciada todavía se puede registrar; un pasado evidente (otro día, otra hora) no. */
    const val PAST_TOLERANCE_MINUTES = 30L

    fun scheduleIssues(start: Instant, durationMinutes: Int, now: Instant): Map<AppointmentField, AppointmentIssue> = buildMap {
        if (start < now.minusSeconds(PAST_TOLERANCE_MINUTES * SECONDS_PER_MINUTE)) put(AppointmentField.DATE, AppointmentIssue.IN_THE_PAST)
        durationIssue(durationMinutes)?.let { put(AppointmentField.DURATION, it) }
    }

    fun durationIssue(durationMinutes: Int): AppointmentIssue? = when {
        durationMinutes < MIN_DURATION_MINUTES -> AppointmentIssue.TOO_SHORT
        durationMinutes > MAX_DURATION_MINUTES -> AppointmentIssue.TOO_LONG
        else -> null
    }

    fun logisticsIssues(
        modality: AppointmentModality?,
        location: String?,
        meetingUrl: String?,
        notes: String?,
    ): Map<AppointmentField, AppointmentIssue> = buildMap {
        if (modality == null) put(AppointmentField.MODALITY, AppointmentIssue.REQUIRED)
        if (modality == AppointmentModality.IN_PERSON) {
            val tidy = TextNormalizer.tidy(location.orEmpty())
            when {
                tidy.isEmpty() -> put(AppointmentField.LOCATION, AppointmentIssue.REQUIRED)
                tidy.length > MAX_LOCATION_LENGTH -> put(AppointmentField.LOCATION, AppointmentIssue.TOO_LONG)
            }
        }
        if (modality == AppointmentModality.ONLINE) {
            val url = meetingUrl.orEmpty().trim()
            when {
                url.isEmpty() -> Unit
                url.length > MAX_URL_LENGTH -> put(AppointmentField.MEETING_URL, AppointmentIssue.TOO_LONG)
                !isWebUrl(url) -> put(AppointmentField.MEETING_URL, AppointmentIssue.INVALID)
            }
        }
        if (TextNormalizer.tidy(notes.orEmpty()).length > MAX_NOTES_LENGTH) put(AppointmentField.NOTES, AppointmentIssue.TOO_LONG)
    }

    fun normalizeLogistics(
        modality: AppointmentModality,
        location: String?,
        meetingUrl: String?,
        notes: String?,
    ): AppointmentLogistics = AppointmentLogistics(
        modality = modality,
        location = if (modality == AppointmentModality.IN_PERSON) optional(location) else null,
        meetingUrl = if (modality == AppointmentModality.ONLINE) meetingUrl?.trim()?.ifEmpty { null } else null,
        administrativeNotes = optional(notes),
    )

    private fun optional(text: String?): String? = text?.let(TextNormalizer::tidy)?.ifEmpty { null }

    private fun isWebUrl(text: String): Boolean {
        if (text.any { it.isWhitespace() }) return false
        return try {
            val uri = URI(text)
            uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty()
        } catch (_: URISyntaxException) {
            false
        }
    }

    private const val SECONDS_PER_MINUTE = 60L
}

sealed interface ScheduleParseResult {
    data class Parsed(val start: Instant) : ScheduleParseResult

    data class Invalid(val issues: Map<AppointmentField, AppointmentIssue>) : ScheduleParseResult
}

/** Convierte el día (ddMMaaaa) y la hora (HHmm) capturados, en hora de Monterrey, en el instante que se persiste. */
object AppointmentScheduleParser {
    private const val DATE_DIGITS = 8
    private const val TIME_DIGITS = 4

    fun parse(dateDigits: String, timeDigits: String, zone: ZoneId = ClinicTime.zone): ScheduleParseResult {
        val date = parseDate(dateDigits)
        val time = parseTime(timeDigits)
        val issues = buildMap {
            if (date !is Parsed.Value) put(AppointmentField.DATE, (date as Parsed.Failed).issue)
            if (time !is Parsed.Value) put(AppointmentField.TIME, (time as Parsed.Failed).issue)
        }
        if (issues.isNotEmpty()) return ScheduleParseResult.Invalid(issues)
        val local = ZonedDateTime.of((date as Parsed.Value<LocalDate>).value, (time as Parsed.Value<LocalTime>).value, zone)
        return ScheduleParseResult.Parsed(local.toInstant())
    }

    private sealed interface Parsed<out T> {
        data class Value<T>(val value: T) : Parsed<T>

        data class Failed(val issue: AppointmentIssue) : Parsed<Nothing>
    }

    private fun parseDate(digits: String): Parsed<LocalDate> {
        if (digits.isEmpty()) return Parsed.Failed(AppointmentIssue.REQUIRED)
        if (digits.length != DATE_DIGITS || !digits.all(Char::isDigit)) return Parsed.Failed(AppointmentIssue.INVALID)
        return try {
            Parsed.Value(LocalDate.of(digits.substring(4).toInt(), digits.substring(2, 4).toInt(), digits.substring(0, 2).toInt()))
        } catch (_: DateTimeException) {
            Parsed.Failed(AppointmentIssue.INVALID)
        }
    }

    private fun parseTime(digits: String): Parsed<LocalTime> {
        if (digits.isEmpty()) return Parsed.Failed(AppointmentIssue.REQUIRED)
        if (digits.length != TIME_DIGITS || !digits.all(Char::isDigit)) return Parsed.Failed(AppointmentIssue.INVALID)
        return try {
            Parsed.Value(LocalTime.of(digits.substring(0, 2).toInt(), digits.substring(2).toInt()))
        } catch (_: DateTimeException) {
            Parsed.Failed(AppointmentIssue.INVALID)
        }
    }
}
