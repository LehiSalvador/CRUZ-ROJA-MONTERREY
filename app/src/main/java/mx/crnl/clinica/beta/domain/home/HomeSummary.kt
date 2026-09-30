package mx.crnl.clinica.beta.domain.home

import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.Patient

/** Qué cuenta el indicador de pacientes de Inicio según el rol de quien lo consulta. */
enum class PatientMetric { ASSIGNED_TO_USER, AREA_PATIENTS, ALL_ACTIVE_PATIENTS }

/** Solicitud de acceso interárea todavía sin resolver. */
data class PendingAccessRequest(val requesterUserId: String, val ownerArea: ClinicalArea?)

data class HomeSummary(
    val patientMetric: PatientMetric,
    val patientCount: Int,
    val upcomingAppointmentCount: Int,
    /** Citas del día (hora de Monterrey) que no se cancelaron, dentro de lo que la persona puede ver. */
    val todayAppointmentCount: Int,
    val pendingRequestCount: Int,
    val recentPatients: List<Patient>,
    val upcomingAppointments: List<AppointmentSummary>,
)
