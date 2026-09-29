package mx.crnl.clinica.beta.domain.model

import java.time.Instant

data class AppointmentSummary(
    val appointmentId: String,
    val patientId: String,
    val patientName: String,
    val patientNumber: String,
    val professionalName: String,
    val area: ClinicalArea,
    val start: Instant,
    val end: Instant,
    val modality: AppointmentModality,
    val location: String?,
    val status: AppointmentStatus,
)
