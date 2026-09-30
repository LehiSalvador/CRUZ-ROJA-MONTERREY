package mx.crnl.clinica.beta.domain.clinical

import java.time.Instant
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.EncounterType

enum class EncounterField { TYPE, DATE, TIME, APPOINTMENT }

enum class EncounterIssue { REQUIRED, INVALID, IN_THE_FUTURE, TOO_OLD }

/** Reglas de captura del encuentro base; comparten el formulario y el repositorio. No hay contenido clínico que validar. */
object EncounterRules {
    /** Un encuentro ya ocurrió: solo se admite una pequeña diferencia de reloj hacia adelante. */
    const val FUTURE_TOLERANCE_MINUTES = 15L

    private val earliestEventAt: Instant = Instant.parse("2000-01-01T00:00:00Z")

    /** Estados en los que una cita puede ser el motivo de un encuentro: la atención pudo ocurrir o ya ocurrió. */
    val LINKABLE_APPOINTMENT_STATUSES: Set<AppointmentStatus> = setOf(
        AppointmentStatus.SCHEDULED,
        AppointmentStatus.CONFIRMED,
        AppointmentStatus.RESCHEDULED,
        AppointmentStatus.COMPLETED,
    )

    fun issues(type: EncounterType?, eventAt: Instant, now: Instant): Map<EncounterField, EncounterIssue> = buildMap {
        if (type == null) put(EncounterField.TYPE, EncounterIssue.REQUIRED)
        when {
            eventAt > now.plusSeconds(FUTURE_TOLERANCE_MINUTES * SECONDS_PER_MINUTE) -> put(EncounterField.DATE, EncounterIssue.IN_THE_FUTURE)
            eventAt < earliestEventAt -> put(EncounterField.DATE, EncounterIssue.TOO_OLD)
        }
    }

    private const val SECONDS_PER_MINUTE = 60L
}
