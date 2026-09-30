package mx.crnl.clinica.beta.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import mx.crnl.clinica.beta.R

/** Destinos de navegación, independientes de las pantallas que los implementan. */
@Serializable
sealed interface AppRoute {
    @Serializable
    data object Splash : AppRoute

    @Serializable
    data object Login : AppRoute

    @Serializable
    data object RequestAccount : AppRoute

    @Serializable
    data object Main : AppRoute

    @Serializable
    data object Home : AppRoute

    /** Grafo de la pestaña Pacientes: agrupa el listado, el expediente y los formularios de alta y edición. */
    @Serializable
    data object PatientsGraph : AppRoute

    @Serializable
    data object Patients : AppRoute

    /** [area] (código de `ClinicalArea`) abre el expediente en la pestaña de esa área. */
    @Serializable
    data class PatientDetail(val patientId: String, val area: String? = null) : AppRoute

    @Serializable
    data object NewPatient : AppRoute

    @Serializable
    data class EditPatient(val patientId: String) : AppRoute

    /** Grafo de la pestaña Citas: agenda, detalle y formularios de cita. */
    @Serializable
    data object AppointmentsGraph : AppRoute

    @Serializable
    data object Appointments : AppRoute

    @Serializable
    data class AppointmentDetail(val appointmentId: String) : AppRoute

    /** [patientId] y [area] (código de `ClinicalArea`) preseleccionan el paciente y el área al abrir desde el expediente. */
    @Serializable
    data class NewAppointment(val patientId: String? = null, val area: String? = null) : AppRoute

    @Serializable
    data class EditAppointment(val appointmentId: String) : AppRoute

    @Serializable
    data class RescheduleAppointment(val appointmentId: String) : AppRoute

    @Serializable
    data class AssignProfessional(val patientId: String, val area: String) : AppRoute

    @Serializable
    data class NewEncounter(val patientId: String, val area: String, val appointmentId: String? = null) : AppRoute

    @Serializable
    data class EncounterDetail(val encounterId: String) : AppRoute

    /** Grafo de la pestaña Solicitudes: bandeja por categoría y el detalle de cada solicitud. */
    @Serializable
    data object RequestsGraph : AppRoute

    @Serializable
    data object Requests : AppRoute

    @Serializable
    data class AccountDetail(val userId: String) : AppRoute

    @Serializable
    data class AccessRequestDetail(val requestId: String) : AppRoute

    @Serializable
    data class ChangeRequestDetail(val requestId: String) : AppRoute

    /** Solicitud de lectura de un [area] (código de `ClinicalArea`) que la persona no ve por su rol. */
    @Serializable
    data class NewAccessRequest(val patientId: String, val area: String) : AppRoute

    /** Solicitud de cambio del profesional vigente del [area] (código de `ClinicalArea`). */
    @Serializable
    data class NewProfessionalChange(val patientId: String, val area: String) : AppRoute

    @Serializable
    data class AssessmentDetail(val assessmentId: String) : AppRoute

    /** Pantalla aislada del modo supervisado: sin barra inferior ni datos del paciente. */
    @Serializable
    data class SupervisedMode(val assessmentId: String) : AppRoute

    /** Grafo de la pestaña Perfil: la cuenta, más las herramientas de administración si la persona las tiene. */
    @Serializable
    data object ProfileGraph : AppRoute

    @Serializable
    data object Profile : AppRoute

    @Serializable
    data object UserDirectory : AppRoute

    @Serializable
    data class UserDirectoryDetail(val userId: String) : AppRoute

    @Serializable
    data object AuditLog : AppRoute

    @Serializable
    data object BetaTools : AppRoute
}

/** Destinos de la barra inferior de la shell principal: icono delineado en reposo y relleno al seleccionarse. */
enum class TopLevelDestination(
    val route: AppRoute,
    /** Pantalla raíz de la pestaña: volver a tocar la pestaña abierta regresa a ella. */
    val root: AppRoute,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME(AppRoute.Home, AppRoute.Home, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    PATIENTS(AppRoute.PatientsGraph, AppRoute.Patients, R.string.nav_patients, Icons.Outlined.Person, Icons.Filled.Person),
    APPOINTMENTS(AppRoute.AppointmentsGraph, AppRoute.Appointments, R.string.nav_appointments, Icons.Outlined.DateRange, Icons.Filled.DateRange),
    REQUESTS(AppRoute.RequestsGraph, AppRoute.Requests, R.string.nav_requests, Icons.Outlined.Email, Icons.Filled.Email),
    PROFILE(AppRoute.ProfileGraph, AppRoute.Profile, R.string.nav_profile, Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
}
