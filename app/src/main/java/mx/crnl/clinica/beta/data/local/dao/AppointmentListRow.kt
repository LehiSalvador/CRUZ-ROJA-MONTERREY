package mx.crnl.clinica.beta.data.local.dao

/** Proyección de una cita con los datos mínimos de paciente y profesional para listarla. */
data class AppointmentListRow(
    val appointmentId: String,
    val patientId: String,
    val patientFirstName: String,
    val patientPaternalSurname: String,
    val patientMaternalSurname: String?,
    val patientNumber: String,
    val professionalId: String,
    val professionalFirstName: String,
    val professionalPaternalSurname: String,
    val areaCode: String,
    val startDateTime: Long,
    val endDateTime: Long,
    val modality: String,
    val location: String?,
    val status: String,
)
