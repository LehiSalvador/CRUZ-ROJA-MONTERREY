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

data class PatientAssignment(val area: ClinicalArea, val professionalName: String, val since: Instant)

data class EncounterSummary(
    val encounterId: String,
    val area: ClinicalArea,
    val professionalName: String,
    val type: EncounterType,
    val status: EncounterStatus,
    val eventAt: Instant,
)

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

data class PatientDetail(
    val patient: Patient,
    val contacts: List<PatientContact>,
    val assignments: List<PatientAssignment>,
    val appointments: List<AppointmentSummary>,
    val encounters: List<EncounterSummary>,
    val assessments: List<AssessmentSummary>,
) {
    fun nextAppointment(now: Instant): AppointmentSummary? =
        appointments
            .filter { it.status in OPEN_APPOINTMENT_STATUSES && it.end >= now }
            .minByOrNull { it.start }
}

/** Estados de una cita que todavía puede ocurrir. */
val OPEN_APPOINTMENT_STATUSES: Set<AppointmentStatus> =
    setOf(AppointmentStatus.PENDING, AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED)
