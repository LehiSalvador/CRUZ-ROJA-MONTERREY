package mx.crnl.clinica.beta.core.demo

import kotlinx.serialization.Serializable

/*
 * Formato de intercambio de los JSON de assets. Los códigos se leen como texto y se validan en
 * DemoSeedValidator para poder reportar todos los problemas de una vez y con el archivo de origen.
 * Los momentos se expresan como días relativos al día de la primera carga, para que la agenda
 * ficticia nunca quede "vieja".
 */

@Serializable
data class SeedMeta(val fictitious: Boolean, val seedVersion: Int, val description: String)

@Serializable
data class SeedUsersFile(val meta: SeedMeta, val users: List<SeedUser>)

@Serializable
data class SeedUser(
    val userId: String,
    val firstName: String,
    val paternalSurname: String,
    val maternalSurname: String? = null,
    val email: String,
    val role: String,
    val area: String? = null,
    val status: String,
)

@Serializable
data class SeedPatientsFile(val meta: SeedMeta, val patients: List<SeedPatient>)

@Serializable
data class SeedPatient(
    val patientId: String,
    val patientNumber: String,
    val firstName: String,
    val paternalSurname: String,
    val maternalSurname: String? = null,
    val birthDate: String,
    val birthPlace: String? = null,
    val sex: String,
    val municipality: String? = null,
    val populationType: String,
    val status: String,
    val createdBy: String,
    val contacts: List<SeedContact> = emptyList(),
)

@Serializable
data class SeedContact(
    val contactId: String,
    val type: String,
    val value: String,
    val isPrimary: Boolean = false,
)

@Serializable
data class SeedAssignmentsFile(val meta: SeedMeta, val assignments: List<SeedAssignment>)

@Serializable
data class SeedAssignment(
    val assignmentId: String,
    val patientId: String,
    val area: String,
    val professionalId: String,
    val startDayOffset: Int,
    val endDayOffset: Int? = null,
    val status: String,
    val reason: String? = null,
    val assignedBy: String,
)

@Serializable
data class SeedAppointmentsFile(val meta: SeedMeta, val appointments: List<SeedAppointment>)

@Serializable
data class SeedAppointment(
    val appointmentId: String,
    val patientId: String,
    val area: String,
    val professionalId: String,
    val dayOffset: Int,
    val startTime: String,
    val durationMinutes: Int,
    val modality: String,
    val location: String? = null,
    val meetingUrl: String? = null,
    val status: String,
    val administrativeNotes: String? = null,
    val createdBy: String,
)

@Serializable
data class SeedEncountersFile(val meta: SeedMeta, val encounters: List<SeedEncounter>)

@Serializable
data class SeedEncounter(
    val encounterId: String,
    val patientId: String,
    val area: String,
    val professionalId: String,
    val appointmentId: String? = null,
    val type: String,
    val status: String,
    val dayOffset: Int,
    val time: String,
    val createdBy: String,
)

@Serializable
data class SeedAssessmentsFile(val meta: SeedMeta, val assessments: List<SeedAssessment>)

@Serializable
data class SeedAssessment(
    val assessmentId: String,
    val patientId: String,
    val encounterId: String? = null,
    val professionalId: String,
    val instrumentCode: String,
    val instrumentVersion: String,
    val administrationMode: String,
    val dayOffset: Int,
    val time: String,
    val durationMinutes: Int,
    val status: String,
    val result: SeedAssessmentResult? = null,
)

@Serializable
data class SeedAssessmentResult(
    val resultId: String,
    val rawScore: Double,
    val classificationCode: String,
    val classificationLabel: String,
    val scoringVersion: String,
)
