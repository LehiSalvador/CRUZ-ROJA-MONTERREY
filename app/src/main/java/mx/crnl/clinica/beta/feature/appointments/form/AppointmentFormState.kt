package mx.crnl.clinica.beta.feature.appointments.form

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import mx.crnl.clinica.beta.core.util.ClinicTime
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.appointment.AppointmentField
import mx.crnl.clinica.beta.domain.appointment.AppointmentIssue
import mx.crnl.clinica.beta.domain.appointment.AppointmentRules
import mx.crnl.clinica.beta.domain.appointment.AppointmentScheduleParser
import mx.crnl.clinica.beta.domain.appointment.ScheduleParseResult
import mx.crnl.clinica.beta.domain.model.AppointmentModality

/** Duraciones que ofrece el formulario; una cita existente con otra duración la conserva como opción adicional. */
val StandardDurationMinutes: List<Int> = listOf(30, 45, 50, 60, 90)

const val DEFAULT_DURATION_MINUTES = 50

private const val DATE_DIGITS = 8
private const val TIME_DIGITS = 4
private val dateDigitsFormat = DateTimeFormatter.ofPattern("ddMMuuuu")
private val timeDigitsFormat = DateTimeFormatter.ofPattern("HHmm")

/** Resultado de leer el horario capturado: el inicio ya validado o los problemas de cada campo. */
sealed interface ScheduleInput {
    data class Valid(val start: Instant, val durationMinutes: Int) : ScheduleInput

    data class Invalid(val issues: Map<AppointmentField, AppointmentIssue>) : ScheduleInput
}

/** Día y hora como los teclea la persona (solo dígitos, con máscara al mostrarse) y la duración elegida. */
data class ScheduleFormState(
    val dateDigits: String = "",
    val timeDigits: String = "",
    val durationMinutes: Int = DEFAULT_DURATION_MINUTES,
    val issues: Map<AppointmentField, AppointmentIssue> = emptyMap(),
) {
    fun withDate(value: String) = copy(dateDigits = value.filter(Char::isDigit).take(DATE_DIGITS), issues = issues - AppointmentField.DATE)

    fun withTime(value: String) = copy(timeDigits = value.filter(Char::isDigit).take(TIME_DIGITS), issues = issues - AppointmentField.TIME)

    fun withDuration(minutes: Int) = copy(durationMinutes = minutes, issues = issues - AppointmentField.DURATION)

    fun withPickedDate(date: LocalDate) = withDate(date.format(dateDigitsFormat))

    fun withPickedTime(time: LocalTime) = withTime(time.format(timeDigitsFormat))

    fun withIssues(newIssues: Map<AppointmentField, AppointmentIssue>) = copy(issues = newIssues)

    /** Lee el horario y aplica las reglas de la cita; el repositorio vuelve a aplicarlas al guardar. */
    fun validate(now: Instant): ScheduleInput {
        val start = when (val parsed = AppointmentScheduleParser.parse(dateDigits, timeDigits)) {
            is ScheduleParseResult.Invalid -> {
                val duration = AppointmentRules.durationIssue(durationMinutes)?.let { mapOf(AppointmentField.DURATION to it) }.orEmpty()
                return ScheduleInput.Invalid(parsed.issues + duration)
            }
            is ScheduleParseResult.Parsed -> parsed.start
        }
        val issues = AppointmentRules.scheduleIssues(start, durationMinutes, now)
        return if (issues.isEmpty()) ScheduleInput.Valid(start, durationMinutes) else ScheduleInput.Invalid(issues)
    }

    companion object {
        fun from(start: Instant, durationMinutes: Int, zone: ZoneId = ClinicTime.zone): ScheduleFormState {
            val local = start.atZone(zone)
            return ScheduleFormState(
                dateDigits = local.format(dateDigitsFormat),
                timeDigits = local.format(timeDigitsFormat),
                durationMinutes = durationMinutes,
            )
        }
    }
}

/** Modalidad, ubicación o enlace y notas administrativas. Nunca contenido clínico. */
data class LogisticsFormState(
    val modality: AppointmentModality? = AppointmentModality.IN_PERSON,
    val location: String = "",
    val meetingUrl: String = "",
    val notes: String = "",
    val issues: Map<AppointmentField, AppointmentIssue> = emptyMap(),
) {
    fun withModality(value: AppointmentModality) = copy(modality = value, issues = issues - AppointmentField.MODALITY - AppointmentField.LOCATION - AppointmentField.MEETING_URL)

    fun withLocation(value: String) = copy(location = value, issues = issues - AppointmentField.LOCATION)

    fun withMeetingUrl(value: String) = copy(meetingUrl = value, issues = issues - AppointmentField.MEETING_URL)

    fun withNotes(value: String) = copy(notes = value, issues = issues - AppointmentField.NOTES)

    fun withIssues(newIssues: Map<AppointmentField, AppointmentIssue>) = copy(issues = newIssues)

    fun validate(): Map<AppointmentField, AppointmentIssue> =
        AppointmentRules.logisticsIssues(modality, location, meetingUrl, notes)

    companion object {
        fun from(modality: AppointmentModality, location: String?, meetingUrl: String?, notes: String?) = LogisticsFormState(
            modality = modality,
            location = location.orEmpty(),
            meetingUrl = meetingUrl.orEmpty(),
            notes = notes.orEmpty(),
        )
    }
}

/** Lo que se le muestra a la persona cuando una escritura no se pudo completar. */
sealed interface WriteState {
    data object Idle : WriteState

    data object Saving : WriteState

    data class Failed(val error: OperationError) : WriteState

    data class Unavailable(val cause: Throwable) : WriteState
}
