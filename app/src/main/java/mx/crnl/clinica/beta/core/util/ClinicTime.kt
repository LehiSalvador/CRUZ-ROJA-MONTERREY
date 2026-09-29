package mx.crnl.clinica.beta.core.util

import java.time.Clock
import java.time.ZoneId
import java.util.Locale

/** Los instantes se persisten en UTC; esta es la zona en la que se presentan y se interpretan los días. */
object ClinicTime {
    val zone: ZoneId = ZoneId.of("America/Monterrey")
    val locale: Locale = Locale.forLanguageTag("es-MX")

    fun systemClock(): Clock = Clock.system(zone)
}
