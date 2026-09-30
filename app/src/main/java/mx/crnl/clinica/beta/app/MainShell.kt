package mx.crnl.clinica.beta.app

import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
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
import mx.crnl.clinica.beta.core.navigation.BottomBarLayout
import mx.crnl.clinica.beta.core.navigation.TopLevelDestination
import mx.crnl.clinica.beta.core.ui.theme.isCompactLandscape
import mx.crnl.clinica.beta.feature.admin.AuditRoute
import mx.crnl.clinica.beta.feature.admin.BetaToolsRoute
import mx.crnl.clinica.beta.feature.admin.UserDirectoryRoute
import mx.crnl.clinica.beta.feature.appointments.AppointmentsRoute
import mx.crnl.clinica.beta.feature.appointments.detail.AppointmentDetailActions
import mx.crnl.clinica.beta.feature.appointments.detail.AppointmentDetailRoute
import mx.crnl.clinica.beta.feature.appointments.form.EditAppointmentRoute
import mx.crnl.clinica.beta.feature.appointments.form.NewAppointmentRoute
import mx.crnl.clinica.beta.feature.appointments.form.RescheduleAppointmentRoute
import mx.crnl.clinica.beta.feature.assessments.AssessmentDetailActions
import mx.crnl.clinica.beta.feature.assessments.AssessmentDetailRoute
import mx.crnl.clinica.beta.feature.assessments.SupervisedModeRoute
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
import mx.crnl.clinica.beta.feature.profile.ProfileActions
import mx.crnl.clinica.beta.feature.profile.ProfileRoute
import mx.crnl.clinica.beta.feature.requests.AccessRequestDetailRoute
import mx.crnl.clinica.beta.feature.requests.AccountDetailRoute
import mx.crnl.clinica.beta.feature.requests.ChangeRequestDetailRoute
import mx.crnl.clinica.beta.feature.requests.NewAccessRequestRoute
import mx.crnl.clinica.beta.feature.requests.NewProfessionalChangeRoute
import mx.crnl.clinica.beta.feature.requests.RequestsActions
import mx.crnl.clinica.beta.feature.requests.RequestsRoute
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
    // Tocar la pestaña ya abierta regresa a su pantalla raíz; tocar otra conserva lo que había abierto en ella.
    val onNavigate: (TopLevelDestination) -> Unit = { destination ->
        if (currentDestination.isIn(destination)) {
            navController.popBackStack(destination.root, inclusive = false)
        } else {
            navController.navigateToTopLevel(destination)
        }
    }

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
                            onOpenAdministration = { navController.navigateToTopLevel(TopLevelDestination.PROFILE) },
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
                                onRequestAccess = { patientId, area -> navController.navigate(AppRoute.NewAccessRequest(patientId, area.name)) },
                                onOpenAccessRequest = { requestId -> navController.openRequestsTab(AppRoute.AccessRequestDetail(requestId)) },
                                onRequestProfessionalChange = { patientId, area ->
                                    navController.navigate(AppRoute.NewProfessionalChange(patientId, area.name))
                                },
                                onOpenChangeRequest = { requestId -> navController.openRequestsTab(AppRoute.ChangeRequestDetail(requestId)) },
                                onOpenAssessment = { assessmentId -> navController.navigate(AppRoute.AssessmentDetail(assessmentId)) },
                            ),
                        )
                    }
                    composable<AppRoute.NewAccessRequest> {
                        NewAccessRequestRoute(
                            factory = factory,
                            onClose = { navController.popBackStack() },
                            onSent = {
                                announce(resources.getString(R.string.access_sent_message))
                                navController.popBackStack()
                            },
                        )
                    }
                    composable<AppRoute.NewProfessionalChange> {
                        NewProfessionalChangeRoute(
                            factory = factory,
                            onClose = { navController.popBackStack() },
                            onSent = {
                                announce(resources.getString(R.string.change_sent_message))
                                navController.popBackStack()
                            },
                        )
                    }
                    composable<AppRoute.AssessmentDetail> {
                        AssessmentDetailRoute(
                            factory = factory,
                            actions = AssessmentDetailActions(
                                onNavigateUp = { navController.popBackStack() },
                                onOpenEncounter = { encounterId -> navController.navigate(AppRoute.EncounterDetail(encounterId)) },
                                onOpenSupervised = { assessmentId -> navController.navigate(AppRoute.SupervisedMode(assessmentId)) },
                            ),
                        )
                    }
                    composable<AppRoute.SupervisedMode> {
                        SupervisedModeRoute(factory = factory, onExit = { navController.popBackStack() })
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
                navigation<AppRoute.RequestsGraph>(startDestination = AppRoute.Requests) {
                    composable<AppRoute.Requests> {
                        RequestsRoute(
                            factory = factory,
                            actions = RequestsActions(
                                onOpenAccount = { userId -> navController.navigate(AppRoute.AccountDetail(userId)) },
                                onOpenAccess = { requestId -> navController.navigate(AppRoute.AccessRequestDetail(requestId)) },
                                onOpenChange = { requestId -> navController.navigate(AppRoute.ChangeRequestDetail(requestId)) },
                            ),
                        )
                    }
                    composable<AppRoute.AccountDetail> {
                        AccountDetailRoute(factory = factory, onNavigateUp = { navController.popBackStack() })
                    }
                    composable<AppRoute.AccessRequestDetail> {
                        AccessRequestDetailRoute(
                            factory = factory,
                            onNavigateUp = { navController.popBackStack() },
                            onOpenPatient = { patientId, area -> navController.openPatientsTab(AppRoute.PatientDetail(patientId, area)) },
                        )
                    }
                    composable<AppRoute.ChangeRequestDetail> {
                        ChangeRequestDetailRoute(
                            factory = factory,
                            onNavigateUp = { navController.popBackStack() },
                            onOpenPatient = { patientId, area -> navController.openPatientsTab(AppRoute.PatientDetail(patientId, area)) },
                        )
                    }
                }
                navigation<AppRoute.ProfileGraph>(startDestination = AppRoute.Profile) {
                    composable<AppRoute.Profile> {
                        ProfileRoute(
                            factory = factory,
                            actions = ProfileActions(
                                onOpenUsers = { navController.navigate(AppRoute.UserDirectory) },
                                onOpenAudit = { navController.navigate(AppRoute.AuditLog) },
                                onOpenTools = { navController.navigate(AppRoute.BetaTools) },
                            ),
                        )
                    }
                    composable<AppRoute.UserDirectory> {
                        UserDirectoryRoute(
                            factory = factory,
                            onNavigateUp = { navController.popBackStack() },
                            onOpenUser = { userId -> navController.navigate(AppRoute.UserDirectoryDetail(userId)) },
                        )
                    }
                    composable<AppRoute.UserDirectoryDetail> {
                        AccountDetailRoute(factory = factory, onNavigateUp = { navController.popBackStack() })
                    }
                    composable<AppRoute.AuditLog> { AuditRoute(factory = factory, onNavigateUp = { navController.popBackStack() }) }
                    composable<AppRoute.BetaTools> { BetaToolsRoute(factory = factory, onNavigateUp = { navController.popBackStack() }) }
                }
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

/** Igual que [openPatientsTab] para la pestaña Solicitudes: Atrás vuelve a la bandeja. */
private fun NavController.openRequestsTab(destination: AppRoute) {
    navigate(AppRoute.RequestsGraph) {
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
    AppRoute.NewAccessRequest::class,
    AppRoute.NewProfessionalChange::class,
    AppRoute.SupervisedMode::class,
)

@Composable
private fun MainNavigationBar(currentDestination: NavDestination?, onNavigate: (TopLevelDestination) -> Unit) {
    // Con fuentes grandes cinco etiquetas no caben en el ancho de la barra: en lugar de cortarlas, se miden y, si alguna no
    // cabe, la barra muestra solo los iconos (cada uno conserva su nombre para lectores de pantalla y la pantalla abierta
    // dice en su título dónde está la persona).
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelMedium
    val labels = TopLevelDestination.entries.map { stringResource(it.labelRes) }
    BoxWithConstraints {
        val showLabels = BottomBarLayout.labelsFit(
            labelWidths = labels.map { label -> with(density) { measurer.measure(label, style, maxLines = 1).size.width.toDp() } },
            barWidth = maxWidth,
            itemPadding = LabelHorizontalPadding,
        )
        NavigationBar {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination.isIn(destination)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(destination) },
                    icon = { DestinationIcon(destination, selected, describe = !showLabels) },
                    label = if (showLabels) {
                        { DestinationLabel(destination) }
                    } else {
                        null
                    },
                    alwaysShowLabel = showLabels,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MainNavigationRail(currentDestination: NavDestination?, onNavigate: (TopLevelDestination) -> Unit) {
    // El riel apila los cinco destinos en el alto disponible; con fuentes grandes las etiquetas ya no caben y se muestran solo iconos.
    val showLabels = LocalDensity.current.fontScale <= RailLabelsMaxFontScale
    NavigationRail {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination.isIn(destination)
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = { DestinationIcon(destination, selected, describe = !showLabels) },
                label = if (showLabels) {
                    { DestinationLabel(destination) }
                } else {
                    null
                },
                alwaysShowLabel = showLabels,
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
private fun DestinationIcon(destination: TopLevelDestination, selected: Boolean, describe: Boolean) {
    Icon(
        imageVector = if (selected) destination.selectedIcon else destination.icon,
        // Sin etiqueta visible el icono es lo único que nombra al destino.
        contentDescription = if (describe) stringResource(destination.labelRes) else null,
    )
}

@Composable
private fun DestinationLabel(destination: TopLevelDestination) {
    Text(
        text = stringResource(destination.labelRes),
        maxLines = 1,
        softWrap = false,
    )
}

private val LabelHorizontalPadding = 8.dp

// Por encima de esta escala de fuente, cinco etiquetas apiladas ya no caben en el alto del riel.
private const val RailLabelsMaxFontScale = 1.3f

private fun NavDestination?.isIn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
