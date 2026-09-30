package mx.crnl.clinica.beta.domain.model

import java.time.Instant

/**
 * Encuentro clínico base: solo el hecho de que hubo atención, quién, cuándo y de qué tipo. No contiene contenido
 * clínico; los formularios oficiales de cada área todavía no existen. Es de solo lectura una vez guardado.
 */
data class EncounterDetail(
    val summary: EncounterSummary,
    val patientId: String,
    val patientName: String,
    val patientNumber: String,
    val professionalId: String,
    val appointmentStart: Instant?,
    val recordedAt: Instant,
    val createdByName: String,
)

/** `eventAt` es cuándo ocurrió la atención; el momento de captura lo pone el repositorio. */
data class EncounterDraft(
    val patientId: String,
    val area: ClinicalArea,
    val professionalId: String,
    val appointmentId: String?,
    val type: EncounterType,
    val eventAt: Instant,
)

/** Lo que necesita el formulario de un encuentro: el paciente, el profesional asignado y las citas que se pueden vincular. */
data class EncounterFormContext(
    val patientId: String,
    val patientName: String,
    val patientNumber: String,
    val area: ClinicalArea,
    val assignment: PatientAssignment?,
    val linkableAppointments: List<AppointmentSummary>,
    val canCreate: Boolean,
)
