package mx.crnl.clinica.beta.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import mx.crnl.clinica.beta.domain.patient.PatientFormValidator
import mx.crnl.clinica.beta.feature.appointments.AppointmentsViewModel
import mx.crnl.clinica.beta.feature.appointments.detail.AppointmentDetailViewModel
import mx.crnl.clinica.beta.feature.appointments.form.EditAppointmentViewModel
import mx.crnl.clinica.beta.feature.appointments.form.NewAppointmentViewModel
import mx.crnl.clinica.beta.feature.appointments.form.RescheduleAppointmentViewModel
import mx.crnl.clinica.beta.feature.assignments.AssignProfessionalViewModel
import mx.crnl.clinica.beta.feature.auth.LoginViewModel
import mx.crnl.clinica.beta.feature.auth.RequestAccountViewModel
import mx.crnl.clinica.beta.feature.encounters.EncounterDetailViewModel
import mx.crnl.clinica.beta.feature.encounters.NewEncounterViewModel
import mx.crnl.clinica.beta.feature.home.HomeViewModel
import mx.crnl.clinica.beta.feature.patients.PatientsViewModel
import mx.crnl.clinica.beta.feature.patients.create.NewPatientViewModel
import mx.crnl.clinica.beta.feature.patients.detail.PatientDetailViewModel
import mx.crnl.clinica.beta.feature.patients.edit.EditPatientViewModel
import mx.crnl.clinica.beta.feature.profile.ProfileViewModel
import mx.crnl.clinica.beta.feature.session.SessionGuardViewModel
import mx.crnl.clinica.beta.feature.splash.SplashViewModel

fun clinicalViewModelFactory(
    container: AppContainer,
    splashMinimumDisplayMillis: Long = SplashViewModel.DEFAULT_MINIMUM_DISPLAY_MILLIS,
    searchDebounceMillis: Long = PatientsViewModel.DEFAULT_SEARCH_DEBOUNCE_MILLIS,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        SplashViewModel(container.localDataInitializer, container.authRepository, splashMinimumDisplayMillis)
    }
    initializer { LoginViewModel(container.authRepository) }
    initializer { RequestAccountViewModel(container.authRepository) }
    initializer { SessionGuardViewModel(container.authRepository) }
    initializer { HomeViewModel(container.authRepository, container.homeRepository, container.clock) }
    initializer {
        PatientsViewModel(
            createSavedStateHandle(),
            container.authRepository,
            container.patientRepository,
            container.clock,
            searchDebounceMillis,
        )
    }
    initializer {
        PatientDetailViewModel(createSavedStateHandle(), container.authRepository, container.patientRepository, container.clock)
    }
    initializer {
        NewPatientViewModel(container.authRepository, container.patientRepository, PatientFormValidator(container.clock))
    }
    initializer {
        EditPatientViewModel(
            createSavedStateHandle(),
            container.authRepository,
            container.patientRepository,
            PatientFormValidator(container.clock),
        )
    }
    initializer {
        AppointmentsViewModel(createSavedStateHandle(), container.authRepository, container.appointmentRepository, container.clock)
    }
    initializer {
        AppointmentDetailViewModel(
            createSavedStateHandle(),
            container.authRepository,
            container.appointmentRepository,
            container.assignmentRepository,
            container.clock,
        )
    }
    initializer {
        NewAppointmentViewModel(
            createSavedStateHandle(),
            container.authRepository,
            container.patientRepository,
            container.assignmentRepository,
            container.appointmentRepository,
            container.clock,
        )
    }
    initializer { EditAppointmentViewModel(createSavedStateHandle(), container.authRepository, container.appointmentRepository) }
    initializer {
        RescheduleAppointmentViewModel(createSavedStateHandle(), container.authRepository, container.appointmentRepository, container.clock)
    }
    initializer {
        AssignProfessionalViewModel(
            createSavedStateHandle(),
            container.authRepository,
            container.patientRepository,
            container.assignmentRepository,
        )
    }
    initializer { NewEncounterViewModel(createSavedStateHandle(), container.authRepository, container.encounterRepository, container.clock) }
    initializer { EncounterDetailViewModel(createSavedStateHandle(), container.authRepository, container.encounterRepository) }
    initializer { ProfileViewModel(container.authRepository) }
}
