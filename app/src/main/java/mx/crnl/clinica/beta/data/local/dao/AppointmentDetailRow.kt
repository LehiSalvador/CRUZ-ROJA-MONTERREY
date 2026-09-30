package mx.crnl.clinica.beta.data.local.dao

/** Una cita completa con los nombres ya resueltos; el teléfono es el contacto principal vigente del paciente. */
data class AppointmentDetailRow(
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
    val meetingUrl: String?,
    val status: String,
    val administrativeNotes: String?,
    val createdByFirstName: String,
    val createdByPaternalSurname: String,
    val createdByMaternalSurname: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val contactPhone: String?,
)
