package mx.crnl.clinica.beta.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import mx.crnl.clinica.beta.core.navigation.AppRoute
import mx.crnl.clinica.beta.core.navigation.TopLevelDestination
import mx.crnl.clinica.beta.feature.appointments.AppointmentsRoute
import mx.crnl.clinica.beta.feature.home.HomeScreen
import mx.crnl.clinica.beta.feature.patients.PatientsRoute
import mx.crnl.clinica.beta.feature.profile.ProfileRoute
import mx.crnl.clinica.beta.feature.requests.RequestsScreen

/** Shell principal: barra inferior con un back stack independiente por pestaña. */
@Composable
fun MainShell(factory: ViewModelProvider.Factory, onSignedOut: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTopLevel(destination) },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.icon,
                                contentDescription = null,
                            )
                        },
                        // Con fuentes grandes cinco etiquetas no caben: se recorta con puntos suspensivos en lugar de partir la palabra.
                        label = {
                            Text(
                                text = stringResource(destination.labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Home,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable<AppRoute.Home> { HomeScreen() }
            composable<AppRoute.Patients> { PatientsRoute(factory) }
            composable<AppRoute.Appointments> { AppointmentsRoute(factory) }
            composable<AppRoute.Requests> { RequestsScreen() }
            composable<AppRoute.Profile> { ProfileRoute(factory, onSignedOut) }
        }
    }
}

private fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
