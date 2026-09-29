package mx.crnl.clinica.beta.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import mx.crnl.clinica.beta.feature.appointments.AppointmentsViewModel
import mx.crnl.clinica.beta.feature.auth.LoginViewModel
import mx.crnl.clinica.beta.feature.patients.PatientsViewModel
import mx.crnl.clinica.beta.feature.profile.ProfileViewModel
import mx.crnl.clinica.beta.feature.splash.SplashViewModel

fun clinicalViewModelFactory(
    container: AppContainer,
    splashMinimumDisplayMillis: Long = SplashViewModel.DEFAULT_MINIMUM_DISPLAY_MILLIS,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        SplashViewModel(container.localDataInitializer, container.sessionRepository, splashMinimumDisplayMillis)
    }
    initializer { LoginViewModel(container.sessionRepository) }
    initializer { PatientsViewModel(container.patientRepository, container.clock) }
    initializer { AppointmentsViewModel(container.appointmentRepository, container.clock) }
    initializer { ProfileViewModel(container.sessionRepository) }
}
