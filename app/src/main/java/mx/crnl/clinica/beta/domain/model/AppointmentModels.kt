package mx.crnl.clinica.beta.domain.model

import java.time.Instant
import mx.crnl.clinica.beta.domain.appointment.ConflictKind

/**
 * Una cita con lo necesario para verla completa. El teléfono del paciente viaja solo para poder ofrecer el contacto
 * externo; nunca se registra ni se muestra sin que la persona lo pida.
 */
data class AppointmentDetail(
    val summary: AppointmentSummary,
    val patientFirstName: String,
    val contactPhone: String?,
    val meetingUrl: String?,
    val administrativeNotes: String?,
    val createdByName: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Encuentros vinculados a esta cita; una cita puede existir sin encuentro. */
    val encounters: List<EncounterSummary>,
) {
    val appointmentId: String get() = summary.appointmentId
    val status: AppointmentStatus get() = summary.status
}

/** Datos para agendar una cita nueva. La duración define el fin; no se capturan motivos ni contenido clínico. */
data class AppointmentDraft(
    val patientId: String,
    val area: ClinicalArea,
    val professionalId: String,
    val start: Instant,
    val durationMinutes: Int,
    val modality: AppointmentModality,
    val location: String?,
    val meetingUrl: String?,
    val administrativeNotes: String?,
)

/** Datos administrativos que se pueden corregir sin mover la cita: no incluyen paciente, área, profesional ni horario. */
data class AppointmentAdminUpdate(
    val modality: AppointmentModality,
    val location: String?,
    val meetingUrl: String?,
    val administrativeNotes: String?,
)

data class AppointmentReschedule(val start: Instant, val durationMinutes: Int)

/**
 * Otra cita con la que choca un horario. Los datos de identificación solo vienen cuando quien consulta puede ver esa
 * cita; si pertenece a otra área solo se informa que el horario está ocupado.
 */
data class ScheduleConflict(
    val kind: ConflictKind,
    val start: Instant,
    val end: Instant,
    val appointmentId: String?,
    val patientName: String?,
    val professionalName: String?,
)
