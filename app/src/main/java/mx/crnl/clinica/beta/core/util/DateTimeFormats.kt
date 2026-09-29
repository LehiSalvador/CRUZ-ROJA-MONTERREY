package mx.crnl.clinica.beta.core.util

import java.time.Instant
import java.time.format.DateTimeFormatter

object DateTimeFormats {
    private val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM", ClinicTime.locale)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", ClinicTime.locale)

    fun day(instant: Instant): String = dayFormatter.format(instant.atZone(ClinicTime.zone))

    fun timeRange(start: Instant, end: Instant): String =
        "${timeFormatter.format(start.atZone(ClinicTime.zone))}–${timeFormatter.format(end.atZone(ClinicTime.zone))}"
}
