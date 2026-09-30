package mx.crnl.clinica.beta.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DateTimeFormats {
    private val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM", ClinicTime.locale)
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM uuuu", ClinicTime.locale)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", ClinicTime.locale)
    private val birthDateFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu", ClinicTime.locale)

    fun day(instant: Instant): String = dayFormatter.format(instant.atZone(ClinicTime.zone))

    fun date(instant: Instant): String = dateFormatter.format(instant.atZone(ClinicTime.zone))

    /** Fecha de nacimiento en la forma en que se captura y se busca: dd/mm/aaaa. */
    fun birthDate(date: LocalDate): String = birthDateFormatter.format(date)

    fun time(instant: Instant): String = timeFormatter.format(instant.atZone(ClinicTime.zone))

    fun timeRange(start: Instant, end: Instant): String =
        "${timeFormatter.format(start.atZone(ClinicTime.zone))}–${timeFormatter.format(end.atZone(ClinicTime.zone))}"
}
