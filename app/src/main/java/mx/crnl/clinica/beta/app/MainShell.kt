package mx.crnl.clinica.beta.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.navigation.AppRoute
import mx.crnl.clinica.beta.core.navigation.TopLevelDestination
import mx.crnl.clinica.beta.core.ui.theme.isCompactLandscape
import mx.crnl.clinica.beta.feature.appointments.AppointmentsRoute
import mx.crnl.clinica.beta.feature.appointments.detail.AppointmentDetailActions
import mx.crnl.clinica.beta.feature.appointments.detail.AppointmentDetailRoute
import mx.crnl.clinica.beta.feature.appointments.form.EditAppointmentRoute
import mx.crnl.clinica.beta.feature.appointments.form.NewAppointmentRoute
import mx.crnl.clinica.beta.feature.appointments.form.RescheduleAppointmentRoute
import mx.crnl.clinica.beta.feature.assignments.AssignProfessionalRoute
import mx.crnl.clinica.beta.feature.encounters.EncounterDetailRoute
import mx.crnl.clinica.beta.feature.encounters.NewEncounterRoute
import mx.crnl.clinica.beta.feature.home.HomeActions
import mx.crnl.clinica.beta.feature.home.HomeRoute
import mx.crnl.clinica.beta.feature.patients.PatientsRoute
import mx.crnl.clinica.beta.feature.patients.create.NewPatientRoute
import mx.crnl.clinica.beta.feature.patients.detail.PatientDetailActions
import mx.crnl.clinica.beta.feature.patients.detail.PatientDetailRoute
import mx.crnl.clinica.beta.feature.patients.edit.EditPatientRoute
import mx.crnl.clinica.beta.feature.profile.ProfileRoute
import mx.crnl.clinica.beta.feature.requests.RequestsScreen
import mx.crnl.clinica.beta.feature.session.SessionGuardViewModel

/** Shell principal: barra inferior (riel lateral en un teléfono horizontal) con un back stack independiente por pestaña. */
@Composable
fun MainShell(factory: ViewModelProvider.Factory, onSessionEnded: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val sessionGuard: SessionGuardViewModel = viewModel(factory = factory)
    val hasSession by sessionGuard.hasSession.collectAsStateWithLifecycle()
    LaunchedEffect(hasSession) {
        if (hasSession == false) onSessionEnded()
    }

    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val announce: (String) -> Unit = { message ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // Los formularios ocupan la pantalla completa: sin barra inferior no se abandonan por accidente.
    val showBottomBar = currentDestination?.let { destination -> FORM_ROUTES.none { destination.hasRoute(it) } } ?: true

    // Un aviso que sigue en pantalla quedaría sobre las acciones fijas del formulario y las dejaría sin respuesta.
    LaunchedEffect(showBottomBar) {
        if (!showBottomBar) snackbarHostState.currentSnackbarData?.dismiss()
    }

    // En un teléfono horizontal la barra inferior gastaría casi una cuarta parte del alto: se usa un riel lateral.
    val useRail = isCompactLandscape()
    val onNavigate: (TopLevelDestination) -> Unit = { destination -> navController.navigateToTopLevel(destination) }

    Scaffold(
        bottomBar = {
            if (showBottomBar && !useRail) MainNavigationBar(currentDestination, onNavigate)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Row(modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)) {
            if (showBottomBar && useRail) MainNavigationRail(currentDestination, onNavigate)
            NavHost(
                navController = navController,
                startDestination = AppRoute.Home,
                // El riel ya respeta el margen lateral del sistema (muesca): las pantallas no lo repiten. El de arriba sí lo aplican.
                modifier = Modifier.weight(1f).fillMaxHeight().then(
                    if (showBottomBar && useRail) {
                        Modifier.consumeWindowInsets(NavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start))
                    } else {
                        Modifier
                    },
                ),
            ) {
                composable<AppRoute.Home> {
                    HomeRoute(
                        factory = factory,
                        actions = HomeActions(
                            onNewPatient = { navController.openPatientsTab(AppRoute.NewPatient) },
                            onSearchPatients = { navController.navigateToTopLevel(TopLevelDestination.PATIENTS) },
                            onOpenPatients = { navController.navigateToTopLevel(TopLevelDestination.PATIENTS) },
                            onOpenPatient = { patientId -> navController.openPatientsTab(AppRoute.PatientDetail(patientId)) },
                            onOpenAppointments = { navController.navigateToTopLevel(TopLevelDestination.APPOINTMENTS) },
                            onOpenAppointment = { appointmentId -> navController.openAppointmentsTab(AppRoute.AppointmentDetail(appointmentId)) },
                            onNewAppointment = { navController.openAppointmentsTab(AppRoute.NewAppointment()) },
                            onOpenRequests = { navController.navigateToTopLevel(TopLevelDestination.REQUESTS) },
                        ),
                    )
                }
                navigation<AppRoute.PatientsGraph>(startDestination = AppRoute.Patients) {
                    composable<AppRoute.Patients> {
                        PatientsRoute(
                            factory = factory,
                            onOpenPatient = { patientId -> navController.navigate(AppRoute.PatientDetail(patientId)) },
                            onNewPatient = { navController.navigate(AppRoute.NewPatient) },
                        )
                    }
                    composable<AppRoute.PatientDetail> {
                        PatientDetailRoute(
                            factory = factory,
                            actions = PatientDetailActions(
                                onNavigateUp = { navController.popBackStack() },
                                onEdit = { patientId -> navController.navigate(AppRoute.EditPatient(patientId)) },
                                onNewAppointment = { patientId, area ->
                                    navController.navigate(AppRoute.NewAppointment(patientId, area?.name))
                                },
                                onAssignProfessional = { patientId, area ->
                                    navController.navigate(AppRoute.AssignProfessional(patientId, area.name))
                                },
                                onRegisterEncounter = { patientId, area ->
                                    navController.navigate(AppRoute.NewEncounter(patientId, area.name))
                                },
                                onOpenAppointment = { appointmentId -> navController.navigate(AppRoute.AppointmentDetail(appointmentId)) },
                                onOpenEncounter = { encounterId -> navController.navigate(AppRoute.EncounterDetail(encounterId)) },
                            ),
                        )
                    }
                    composable<AppRoute.AssignProfessional> {
                        AssignProfessionalRoute(
                            factory = factory,
                            onClose = { navController.popBackStack() },
                            onAssigned = {
                                announce(resources.getString(R.string.assign_done_message))
                                navController.popBackStack()
                            },
                        )
                    }
                    composable<AppRoute.NewEncounter> {
                        NewEncounterRoute(
                            factory = factory,
                            onClose = { navController.popBackStack() },
                            onSaved = { patientId, area ->
                                announce(resources.getString(R.string.encounter_saved_message))
                                // Se sale del formulario y se abre el expediente en la línea de atención del área.
                                navController.popBackStack()
                                navController.navigate(AppRoute.PatientDetail(patientId, area.name)) {
                                    popUpTo<AppRoute.PatientDetail> { inclusive = true }
                                }
                            },
                        )
                    }
                    composable<AppRoute.EncounterDetail> {
                        EncounterDetailRoute(
                            factory = factory,
                            onNavigateUp = { navController.popBackStack() },
                            onOpenAppointment = { appointmentId -> navController.navigate(AppRoute.AppointmentDetail(appointmentId)) },
                        )
                    }
                    composable<AppRoute.NewPatient> {
                        NewPatientRoute(
                            factory = factory,
                            onClose = { navController.popBackStack() },
                            onOpenPatient = { patientId -> navController.navigate(AppRoute.PatientDetail(patientId)) },
                            onCreated = { created ->
                                announce(resources.getString(R.string.patient_created_message, created.patientNumber))
                                navController.navigate(AppRoute.PatientDetail(created.patientId)) {
                                    popUpTo<AppRoute.NewPatient> { inclusive = true }
                                }
                            },
                        )
                    }
                    composable<AppRoute.EditPatient> {
                        EditPatientRoute(
                            factory = factory,
                            onClose = { navController.popBackStack() },
                            onSaved = {
                                announce(resources.getString(R.string.edit_saved_message))
                                navController.popBackStack()
                            },
                            onOpenPatient = { patientId -> navController.navigate(AppRoute.PatientDetail(patientId)) },
                        )
                    }
                }
                navigation<AppRoute.AppointmentsGraph>(startDestination = AppRoute.Appointments) {
                    composable<AppRoute.Appointments> {
                        AppointmentsRoute(
                            factory = factory,
                            onOpenAppointment = { appointmentId -> navController.navigate(AppRoute.AppointmentDetail(appointmentId)) },
                            onNewAppointment = { navController.navigate(AppRoute.NewAppointment()) },
                        )
                    }
                    composable<AppRoute.AppointmentDetail> {
                        AppointmentDetailRoute(
                            factory = factory,
                            actions = AppointmentDetailActions(
                                onNavigateUp = { navController.popBackStack() },
                                onOpenPatient = { patientId -> navController.navigate(AppRoute.PatientDetail(patientId)) },
                                onEdit = { appointmentId -> navController.navigate(AppRoute.EditAppointment(appointmentId)) },
                                onReschedule = { appointmentId -> navController.navigate(AppRoute.RescheduleAppointment(appointmentId)) },
                                onRegisterEncounter = { patientId, area, appointmentId ->
                                    navController.navigate(AppRoute.NewEncounter(patientId, area.name, appointmentId))
                                },
                                onOpenEncounter = { encounterId -> navController.navigate(AppRoute.EncounterDetail(encounterId)) },
                            ),
                        )
                    }
                    composable<AppRoute.NewAppointment> {
                        NewAppointmentRoute(
                            factory = factory,
                            onClose = { navController.popBackStack() },
                            onCreated = { appointmentId ->
                                announce(resources.getString(R.string.appointment_created_message))
                                navController.navigate(AppRoute.AppointmentDetail(appointmentId)) {
                                    popUpTo<AppRoute.NewAppointment> { inclusive = true }
                                }
                            },
                            onOpenAppointment = { appointmentId -> navController.navigate(AppRoute.AppointmentDetail(appointmentId)) },
                            onAssignProfessional = { patientId, area ->
                                navController.navigate(AppRoute.AssignProfessional(patientId, area.name))
                            },
                        )
                    }
                    composable<AppRoute.EditAppointment> {
                        EditAppointmentRoute(
                            factory = factory,
                            onClose = { navController.popBackStack() },
                            onSaved = {
                                announce(resources.getString(R.string.edit_saved_message))
                                navController.popBackStack()
                            },
                        )
                    }
                    composable<AppRoute.RescheduleAppointment> {
                        RescheduleAppointmentRoute(
                            factory = factory,
                            onClose = { navController.popBackStack() },
                            onSaved = {
                                announce(resources.getString(R.string.appointment_rescheduled_message))
                                navController.popBackStack()
                            },
                            onOpenAppointment = { appointmentId -> navController.navigate(AppRoute.AppointmentDetail(appointmentId)) },
                        )
                    }
                }
                composable<AppRoute.Requests> { RequestsScreen() }
                composable<AppRoute.Profile> { ProfileRoute(factory) }
            }
        }
    }
}

private fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        // Inicio es la raíz de la pila y no tiene pantallas propias que restaurar: restaurar el estado guardado
        // al volver a ella reabriría la pestaña de la que se viene.
        restoreState = destination != TopLevelDestination.HOME
    }
}

/** Abre la pestaña Pacientes desde su raíz y coloca [destination] encima, de modo que Atrás vuelve al listado. */
private fun NavController.openPatientsTab(destination: AppRoute) {
    navigate(AppRoute.PatientsGraph) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
    navigate(destination)
}

/** Igual que [openPatientsTab] para la pestaña Citas: Atrás vuelve a la agenda. */
private fun NavController.openAppointmentsTab(destination: AppRoute) {
    navigate(AppRoute.AppointmentsGraph) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
    navigate(destination)
}

// Pantallas de formulario: ocupan todo el alto y ocultan la barra inferior para no abandonarse por accidente.
private val FORM_ROUTES = listOf(
    AppRoute.NewPatient::class,
    AppRoute.EditPatient::class,
    AppRoute.NewAppointment::class,
    AppRoute.EditAppointment::class,
    AppRoute.RescheduleAppointment::class,
    AppRoute.AssignProfessional::class,
    AppRoute.NewEncounter::class,
)

@Composable
private fun MainNavigationBar(currentDestination: NavDestination?, onNavigate: (TopLevelDestination) -> Unit) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination.isIn(destination)
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = { DestinationIcon(destination, selected) },
                // Con fuentes grandes cinco etiquetas no caben: se recorta con puntos suspensivos en lugar de partir la palabra.
                label = { DestinationLabel(destination) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun MainNavigationRail(currentDestination: NavDestination?, onNavigate: (TopLevelDestination) -> Unit) {
    NavigationRail {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination.isIn(destination)
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = { DestinationIcon(destination, selected) },
                label = { DestinationLabel(destination) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun DestinationIcon(destination: TopLevelDestination, selected: Boolean) {
    Icon(
        imageVector = if (selected) destination.selectedIcon else destination.icon,
        contentDescription = null,
    )
}

@Composable
private fun DestinationLabel(destination: TopLevelDestination) {
    Text(
        text = stringResource(destination.labelRes),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun NavDestination?.isIn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
