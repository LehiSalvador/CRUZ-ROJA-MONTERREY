package mx.crnl.clinica.beta.domain.home

import java.time.Instant
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.OPEN_APPOINTMENT_STATUSES
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.model.PatientRecord
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole

/**
 * Deriva los indicadores de Inicio de los datos vigentes. El alcance se adapta al rol sin llegar a ser
 * un modelo de permisos: profesional ve lo propio, coordinación lo de su área y administración todo.
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
    ): HomeSummary {
        val (metric, patientCount) = when (user.role) {
            UserRole.PROFESSIONAL -> PatientMetric.ASSIGNED_TO_USER to
                patients.count { record -> record.assignments.any { it.professionalId == user.userId } }
            UserRole.AREA_COORDINATOR -> PatientMetric.AREA_PATIENTS to
                patients.count { record -> record.assignments.any { it.area == user.area } }
            UserRole.CLINICAL_ADMIN, UserRole.SYSTEM_ADMIN -> PatientMetric.ALL_ACTIVE_PATIENTS to
                patients.count { it.patient.status == PatientStatus.ACTIVE }
        }

        val upcoming = appointments
            .filter { it.status in OPEN_APPOINTMENT_STATUSES && it.end >= now && it.isRelevantTo(user) }
            .sortedBy { it.start }

        return HomeSummary(
            patientMetric = metric,
            patientCount = patientCount,
            upcomingAppointmentCount = upcoming.size,
            pendingRequestCount = pendingRequests.count { it.isRelevantTo(user) },
            recentPatients = patients
                .map { it.patient }
                .sortedWith(compareByDescending<Patient> { it.updatedAt }.thenByDescending { it.patientNumber })
                .take(RECENT_PATIENT_LIMIT),
            upcomingAppointments = upcoming.take(UPCOMING_APPOINTMENT_LIMIT),
        )
    }

    private fun AppointmentSummary.isRelevantTo(user: UserAccount): Boolean = when (user.role) {
        UserRole.PROFESSIONAL -> professionalId == user.userId
        UserRole.AREA_COORDINATOR -> area == user.area
        UserRole.CLINICAL_ADMIN, UserRole.SYSTEM_ADMIN -> true
    }

    private fun PendingAccessRequest.isRelevantTo(user: UserAccount): Boolean = when (user.role) {
        UserRole.PROFESSIONAL -> requesterUserId == user.userId
        UserRole.AREA_COORDINATOR -> ownerArea == user.area
        UserRole.CLINICAL_ADMIN, UserRole.SYSTEM_ADMIN -> true
    }
}
