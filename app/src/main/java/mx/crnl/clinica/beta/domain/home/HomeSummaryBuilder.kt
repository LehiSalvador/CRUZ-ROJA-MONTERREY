package mx.crnl.clinica.beta.domain.home

import java.time.Instant
import java.time.ZoneId
import mx.crnl.clinica.beta.core.util.ClinicTime
import mx.crnl.clinica.beta.domain.access.BetaClinicalAccessPolicy
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.OPEN_APPOINTMENT_STATUSES
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.model.PatientRecord
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole

/**
 * Deriva los indicadores de Inicio de los datos vigentes. El alcance se adapta al rol sin llegar a ser
 * un modelo de permisos: profesional ve lo propio, coordinación lo de su área y administración clínica todo; las citas
 * siguen el alcance de la agenda, por lo que el administrador del sistema no las recibe.
 */
object HomeSummaryBuilder {
    const val RECENT_PATIENT_LIMIT = 5
    const val UPCOMING_APPOINTMENT_LIMIT = 3

    fun build(
        user: UserAccount,
        patients: List<PatientRecord>,
        appointments: List<AppointmentSummary>,
        pendingRequests: List<PendingAccessRequest>,
        now: Instant,
        zone: ZoneId = ClinicTime.zone,
    ): HomeSummary {
        val (metric, patientCount) = when (user.role) {
            UserRole.PROFESSIONAL -> PatientMetric.ASSIGNED_TO_USER to
                patients.count { record -> record.assignments.any { it.professionalId == user.userId } }
            UserRole.AREA_COORDINATOR -> PatientMetric.AREA_PATIENTS to
                patients.count { record -> record.assignments.any { it.area == user.area } }
            UserRole.CLINICAL_ADMIN, UserRole.SYSTEM_ADMIN -> PatientMetric.ALL_ACTIVE_PATIENTS to
                patients.count { it.patient.status == PatientStatus.ACTIVE }
        }

        val scope = BetaClinicalAccessPolicy.appointmentScope(user)
        val visible = appointments.filter { scope.accepts(it.area, it.professionalId) }
        val upcoming = visible
            .filter { it.status in OPEN_APPOINTMENT_STATUSES && it.end >= now }
            .sortedBy { it.start }
        val today = now.atZone(zone).toLocalDate()

        return HomeSummary(
            patientMetric = metric,
            patientCount = patientCount,
            upcomingAppointmentCount = upcoming.size,
            todayAppointmentCount = visible.count {
                it.status != AppointmentStatus.CANCELLED && it.start.atZone(zone).toLocalDate() == today
            },
            pendingRequestCount = pendingRequests.count { it.isRelevantTo(user) },
            recentPatients = patients
                .map { it.patient }
                .sortedWith(compareByDescending<Patient> { it.updatedAt }.thenByDescending { it.patientNumber })
                .take(RECENT_PATIENT_LIMIT),
            upcomingAppointments = upcoming.take(UPCOMING_APPOINTMENT_LIMIT),
        )
    }

    private fun PendingAccessRequest.isRelevantTo(user: UserAccount): Boolean = when (user.role) {
        UserRole.PROFESSIONAL -> requesterUserId == user.userId
        UserRole.AREA_COORDINATOR -> ownerArea == user.area
        UserRole.CLINICAL_ADMIN, UserRole.SYSTEM_ADMIN -> true
    }
}
