package mx.crnl.clinica.beta.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.core.datastore.createSessionDataStore
import mx.crnl.clinica.beta.core.demo.AssetSeedFileReader
import mx.crnl.clinica.beta.core.demo.DemoDataInitializer
import mx.crnl.clinica.beta.core.demo.DemoSeedLoader
import mx.crnl.clinica.beta.core.demo.LocalDataInitializer
import mx.crnl.clinica.beta.core.security.PasswordHasher
import mx.crnl.clinica.beta.core.util.ClinicTime
import mx.crnl.clinica.beta.data.repository.AuditRecorder
import mx.crnl.clinica.beta.data.repository.DataStoreSessionRepository
import mx.crnl.clinica.beta.data.repository.LocalAccessRequestRepository
import mx.crnl.clinica.beta.data.repository.LocalAccountAdministrationRepository
import mx.crnl.clinica.beta.data.repository.LocalAppointmentRepository
import mx.crnl.clinica.beta.data.repository.LocalAssessmentRepository
import mx.crnl.clinica.beta.data.repository.LocalAuditRepository
import mx.crnl.clinica.beta.data.repository.LocalAuthRepository
import mx.crnl.clinica.beta.data.repository.LocalBetaMaintenanceRepository
import mx.crnl.clinica.beta.data.repository.LocalEncounterRepository
import mx.crnl.clinica.beta.data.repository.LocalHomeRepository
import mx.crnl.clinica.beta.data.repository.LocalPatientRepository
import mx.crnl.clinica.beta.data.repository.LocalProfessionalAssignmentRepository
import mx.crnl.clinica.beta.data.repository.LocalProfessionalChangeRepository
import mx.crnl.clinica.beta.domain.repository.AccessRequestRepository
import mx.crnl.clinica.beta.domain.repository.AccountAdministrationRepository
import mx.crnl.clinica.beta.domain.repository.AppointmentRepository
import mx.crnl.clinica.beta.domain.repository.AssessmentRepository
import mx.crnl.clinica.beta.domain.repository.AuditRepository
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.BetaMaintenanceRepository
import mx.crnl.clinica.beta.domain.repository.EncounterRepository
import mx.crnl.clinica.beta.domain.repository.HomeRepository
import mx.crnl.clinica.beta.domain.repository.PatientRepository
import mx.crnl.clinica.beta.domain.repository.ProfessionalAssignmentRepository
import mx.crnl.clinica.beta.domain.repository.ProfessionalChangeRepository
import mx.crnl.clinica.beta.domain.repository.SessionRepository

/** Ensamblado manual de dependencias; cambiar de fuente de datos (p. ej. API remota) solo toca este archivo. */
interface AppContainer {
    val clock: Clock
    val sessionRepository: SessionRepository
    val authRepository: AuthRepository
    val patientRepository: PatientRepository
    val appointmentRepository: AppointmentRepository
    val assignmentRepository: ProfessionalAssignmentRepository
    val encounterRepository: EncounterRepository
    val accountAdministrationRepository: AccountAdministrationRepository
    val accessRequestRepository: AccessRequestRepository
    val professionalChangeRepository: ProfessionalChangeRepository
    val assessmentRepository: AssessmentRepository
    val auditRepository: AuditRepository
    val betaMaintenanceRepository: BetaMaintenanceRepository
    val homeRepository: HomeRepository
    val localDataInitializer: LocalDataInitializer
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val newId: () -> String = { UUID.randomUUID().toString() }

    private val database: ClinicalDatabase by lazy { ClinicalDatabase.create(appContext) }

    private val auditRecorder: AuditRecorder by lazy { AuditRecorder(database.auditDao(), clock, newId) }

    override val clock: Clock = ClinicTime.systemClock()

    override val sessionRepository: SessionRepository by lazy {
        DataStoreSessionRepository(
            createSessionDataStore(applicationScope) { appContext.preferencesDataStoreFile(SESSION_STORE_NAME) },
        )
    }

    override val authRepository: AuthRepository by lazy {
        LocalAuthRepository(database, sessionRepository, PasswordHasher(), auditRecorder, clock, newId)
    }

    override val patientRepository: PatientRepository by lazy {
        LocalPatientRepository(database, auditRecorder, clock, newId)
    }

    override val appointmentRepository: AppointmentRepository by lazy {
        LocalAppointmentRepository(database, auditRecorder, clock, newId)
    }

    override val assignmentRepository: ProfessionalAssignmentRepository by lazy {
        LocalProfessionalAssignmentRepository(database, auditRecorder, clock, newId)
    }

    override val encounterRepository: EncounterRepository by lazy {
        LocalEncounterRepository(database, auditRecorder, clock, newId)
    }

    override val homeRepository: HomeRepository by lazy { LocalHomeRepository(database, clock) }

    override val accountAdministrationRepository: AccountAdministrationRepository by lazy {
        LocalAccountAdministrationRepository(database, auditRecorder, clock)
    }

    override val accessRequestRepository: AccessRequestRepository by lazy {
        LocalAccessRequestRepository(database, auditRecorder, clock, newId)
    }

    override val professionalChangeRepository: ProfessionalChangeRepository by lazy {
        LocalProfessionalChangeRepository(database, auditRecorder, clock, newId)
    }

    override val assessmentRepository: AssessmentRepository by lazy { LocalAssessmentRepository(database, auditRecorder, clock) }

    override val auditRepository: AuditRepository by lazy { LocalAuditRepository(database, clock) }

    override val betaMaintenanceRepository: BetaMaintenanceRepository by lazy {
        LocalBetaMaintenanceRepository(database, sessionRepository, localDataInitializer, auditRecorder)
    }

    override val localDataInitializer: LocalDataInitializer by lazy {
        DemoDataInitializer(
            seedDao = database.seedDao(),
            sessionRepository = sessionRepository,
            loader = DemoSeedLoader(AssetSeedFileReader(appContext.assets)),
            clock = clock,
        )
    }

    private companion object {
        const val SESSION_STORE_NAME = "session"
    }
}
