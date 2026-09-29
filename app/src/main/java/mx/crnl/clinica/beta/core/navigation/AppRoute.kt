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

    @Serializable
    data class PatientDetail(val patientId: String) : AppRoute

    @Serializable
    data object NewPatient : AppRoute

    @Serializable
    data class EditPatient(val patientId: String) : AppRoute

    @Serializable
    data object Appointments : AppRoute

    @Serializable
    data object Requests : AppRoute

    @Serializable
    data object Profile : AppRoute
}

/** Destinos de la barra inferior de la shell principal: icono delineado en reposo y relleno al seleccionarse. */
enum class TopLevelDestination(
    val route: AppRoute,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME(AppRoute.Home, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    PATIENTS(AppRoute.PatientsGraph, R.string.nav_patients, Icons.Outlined.Person, Icons.Filled.Person),
    APPOINTMENTS(AppRoute.Appointments, R.string.nav_appointments, Icons.Outlined.DateRange, Icons.Filled.DateRange),
    REQUESTS(AppRoute.Requests, R.string.nav_requests, Icons.Outlined.Email, Icons.Filled.Email),
    PROFILE(AppRoute.Profile, R.string.nav_profile, Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
}
