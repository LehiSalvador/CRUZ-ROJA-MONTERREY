package mx.crnl.clinica.beta.domain.model

import java.time.Instant
import java.time.LocalDate

data class PatientContact(
    val contactId: String,
    val type: ContactType,
    val value: String,
    val isPrimary: Boolean,
)

/** Asignación vigente de un paciente a un profesional en un área; se usa para filtrar y rotular. */
data class ActiveAssignment(val area: ClinicalArea, val professionalId: String)

/** Un paciente con lo necesario para buscarlo, filtrarlo y rotularlo en un listado. */
data class PatientRecord(
    val patient: Patient,
    val contacts: List<PatientContact>,
    val assignments: List<ActiveAssignment>,
)

data class PatientSummary(val patient: Patient, val areas: List<ClinicalArea>)

/** Datos editables de un paciente; el teléfono y el correo son los contactos principales. */
data class PatientDraft(
    val firstName: String,
    val paternalSurname: String,
    val maternalSurname: String?,
    val birthDate: LocalDate,
    val birthPlace: String?,
    val sex: Sex,
    val municipality: String?,
    val populationType: PopulationType,
    val phone: String?,
    val email: String?,
)

enum class DuplicateReason { SAME_EMAIL, SAME_PHONE, SAME_NAME_AND_BIRTH_DATE, SIMILAR_NAME_AND_BIRTH_DATE }

data class DuplicateCandidate(val patient: Patient, val reasons: Set<DuplicateReason>)

/** Una asignación de un profesional a un paciente en un área; el historial conserva las cerradas (`until`). */
data class PatientAssignment(
    val assignmentId: String,
    val area: ClinicalArea,
    val professionalId: String,
    val professionalName: String,
    val since: Instant,
    val until: Instant?,
    val status: AssignmentStatus,
    val reason: String?,
    val assignedByName: String,
)

data class EncounterSummary(
    val encounterId: String,
    val area: ClinicalArea,
    val professionalName: String,
    val type: EncounterType,
    val status: EncounterStatus,
    val eventAt: Instant,
    val appointmentId: String? = null,
)

/** Lo único que otra área deja ver: que hay atención registrada, cuántos encuentros y cuándo fue el último. */
data class AreaActivity(val area: ClinicalArea, val encounterCount: Int, val lastActivityAt: Instant?)

/** `classificationLabel` es nulo mientras el resultado no tenga una clasificación definida. */
data class AssessmentSummary(
    val assessmentId: String,
    val area: ClinicalArea?,
    val professionalName: String,
    val status: AssessmentStatus,
    val startedAt: Instant,
    val hasResult: Boolean,
    val classificationLabel: String?,
)

/**
 * Expediente de un paciente tal como lo puede ver quien lo consulta: los datos generales son para todos; lo
 * clínico (asignaciones, citas, encuentros, evaluaciones) solo llega de las [viewableAreas]. De las demás áreas
 * únicamente queda [restrictedAreas]. Lo arma [mx.crnl.clinica.beta.domain.clinical.PatientDetailAssembler].
 */
data class PatientDetail(
    val patient: Patient,
    val contacts: List<PatientContact>,
    /** Asignaciones vigentes de las áreas visibles. */
    val assignments: List<PatientAssignment>,
    val appointments: List<AppointmentSummary>,
    val encounters: List<EncounterSummary>,
    val assessments: List<AssessmentSummary>,
    /** Todas las asignaciones (vigentes y cerradas) de las áreas visibles, la más reciente primero. */
    val assignmentHistory: List<PatientAssignment>,
    val viewableAreas: Set<ClinicalArea>,
    val restrictedAreas: List<AreaActivity>,
) {
    fun nextAppointment(now: Instant): AppointmentSummary? =
        appointments
            .filter { it.status in OPEN_APPOINTMENT_STATUSES && it.end >= now }
            .minByOrNull { it.start }

    /** La cita más reciente que ya empezó y no se canceló. */
    fun lastAppointment(now: Instant): AppointmentSummary? =
        appointments
            .filter { it.start <= now && it.status != AppointmentStatus.CANCELLED }
            .maxByOrNull { it.start }

    fun activeAssignment(area: ClinicalArea): PatientAssignment? = assignments.firstOrNull { it.area == area }
}

/** Estados de una cita que todavía puede ocurrir (una reprogramada sigue por ocurrir). */
val OPEN_APPOINTMENT_STATUSES: Set<AppointmentStatus> = setOf(
    AppointmentStatus.PENDING,
    AppointmentStatus.SCHEDULED,
    AppointmentStatus.CONFIRMED,
    AppointmentStatus.RESCHEDULED,
)
