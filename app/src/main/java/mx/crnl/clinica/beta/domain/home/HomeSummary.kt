package mx.crnl.clinica.beta.domain.home

import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.model.UserAccount

/** Qué cuenta el indicador de pacientes de Inicio según el rol de quien lo consulta. */
enum class PatientMetric { ASSIGNED_TO_USER, AREA_PATIENTS, ALL_ACTIVE_PATIENTS }

/** Solicitud de acceso interárea todavía sin resolver. */
data class PendingAccessRequest(val requesterUserId: String, val ownerArea: ClinicalArea?)

/** Solicitud de cambio de profesional todavía sin resolver. */
data class PendingChangeRequest(val requesterUserId: String, val area: ClinicalArea?)

/** Lo que está por resolver en toda la Beta; el constructor de Inicio recorta lo que le toca a cada persona. */
data class PendingWork(
    val accounts: List<UserAccount> = emptyList(),
    val accessRequests: List<PendingAccessRequest> = emptyList(),
    val changeRequests: List<PendingChangeRequest> = emptyList(),
)

data class HomeSummary(
    val patientMetric: PatientMetric,
    val patientCount: Int,
    val upcomingAppointmentCount: Int,
    /** Citas del día (hora de Monterrey) que no se cancelaron, dentro de lo que la persona puede ver. */
    val todayAppointmentCount: Int,
    /** Todo lo pendiente que le concierne: cuentas por revisar más solicitudes propias o por revisar. */
    val pendingRequestCount: Int,
    val recentPatients: List<Patient>,
    val upcomingAppointments: List<AppointmentSummary>,
    /** Cuentas que la persona puede aprobar o rechazar hoy. */
    val pendingAccountCount: Int = 0,
    val pendingAccessCount: Int = 0,
    val pendingChangeCount: Int = 0,
)
