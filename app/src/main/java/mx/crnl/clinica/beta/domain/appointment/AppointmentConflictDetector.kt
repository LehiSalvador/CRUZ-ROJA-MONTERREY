package mx.crnl.clinica.beta.domain.appointment

import java.time.Instant
import mx.crnl.clinica.beta.domain.model.AppointmentStatus

enum class ConflictKind { PROFESSIONAL, PATIENT }

/** Lo mínimo de una cita existente para decidir si choca con otra. */
data class ScheduleSlot(
    val appointmentId: String,
    val patientId: String,
    val professionalId: String,
    val start: Instant,
    val end: Instant,
    val status: AppointmentStatus,
)

data class DetectedConflict(val kind: ConflictKind, val slot: ScheduleSlot)

/**
 * Detecta solapamientos básicos del mismo profesional o del mismo paciente. No es un motor de disponibilidad:
 * las citas canceladas no ocupan horario y las contiguas (una termina cuando la otra empieza) no chocan.
 */
object AppointmentConflictDetector {
    fun detect(
        patientId: String,
        professionalId: String,
        start: Instant,
        end: Instant,
        existing: List<ScheduleSlot>,
        excludeAppointmentId: String? = null,
    ): List<DetectedConflict> = buildList {
        for (slot in existing) {
            if (slot.appointmentId == excludeAppointmentId) continue
            if (slot.status == AppointmentStatus.CANCELLED) continue
            if (!(slot.start < end && start < slot.end)) continue
            if (slot.professionalId == professionalId) add(DetectedConflict(ConflictKind.PROFESSIONAL, slot))
            if (slot.patientId == patientId) add(DetectedConflict(ConflictKind.PATIENT, slot))
        }
    }
}
