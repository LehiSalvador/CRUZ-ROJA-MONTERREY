package mx.crnl.clinica.beta.app

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import mx.crnl.clinica.beta.core.navigation.AppRoute
import mx.crnl.clinica.beta.feature.auth.LoginRoute
import mx.crnl.clinica.beta.feature.splash.SplashDestination
import mx.crnl.clinica.beta.feature.splash.SplashRoute
import mx.crnl.clinica.beta.feature.splash.SplashViewModel

private const val TRANSITION_MILLIS = 220

@Composable
fun ClinicalApp(
    container: AppContainer,
    splashMinimumDisplayMillis: Long = SplashViewModel.DEFAULT_MINIMUM_DISPLAY_MILLIS,
) {
    val factory = remember(container, splashMinimumDisplayMillis) {
        clinicalViewModelFactory(container, splashMinimumDisplayMillis)
    }
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoute.Splash,
        enterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
        exitTransition = { fadeOut(tween(TRANSITION_MILLIS)) },
        popEnterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
        popExitTransition = { fadeOut(tween(TRANSITION_MILLIS)) },
    ) {
        composable<AppRoute.Splash> {
            SplashRoute(factory) { destination ->
                val next = when (destination) {
                    SplashDestination.LOGIN -> AppRoute.Login
                    SplashDestination.MAIN -> AppRoute.Main
                }
                navController.navigate(next) { popUpTo<AppRoute.Splash> { inclusive = true } }
            }
        }
        composable<AppRoute.Login> {
            LoginRoute(factory) {
                navController.navigate(AppRoute.Main) { popUpTo<AppRoute.Login> { inclusive = true } }
            }
        }
        composable<AppRoute.Main> {
            MainShell(factory) {
                navController.navigate(AppRoute.Login) { popUpTo<AppRoute.Main> { inclusive = true } }
            }
        }
    }
}
