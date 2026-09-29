package mx.crnl.clinica.beta.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.core.datastore.createSessionDataStore
import mx.crnl.clinica.beta.core.demo.AssetSeedFileReader
import mx.crnl.clinica.beta.core.demo.DemoDataInitializer
import mx.crnl.clinica.beta.core.demo.DemoSeedLoader
import mx.crnl.clinica.beta.core.demo.LocalDataInitializer
import mx.crnl.clinica.beta.core.util.ClinicTime
import mx.crnl.clinica.beta.data.repository.DataStoreSessionRepository
import mx.crnl.clinica.beta.data.repository.LocalAppointmentRepository
import mx.crnl.clinica.beta.data.repository.LocalPatientRepository
import mx.crnl.clinica.beta.domain.repository.AppointmentRepository
import mx.crnl.clinica.beta.domain.repository.PatientRepository
import mx.crnl.clinica.beta.domain.repository.SessionRepository

/** Ensamblado manual de dependencias; cambiar de fuente de datos (p. ej. API remota) solo toca este archivo. */
interface AppContainer {
    val clock: Clock
    val sessionRepository: SessionRepository
    val patientRepository: PatientRepository
    val appointmentRepository: AppointmentRepository
    val localDataInitializer: LocalDataInitializer
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: ClinicalDatabase by lazy { ClinicalDatabase.create(appContext) }

    override val clock: Clock = ClinicTime.systemClock()

    override val sessionRepository: SessionRepository by lazy {
        DataStoreSessionRepository(
            createSessionDataStore(applicationScope) { appContext.preferencesDataStoreFile(SESSION_STORE_NAME) },
        )
    }

    override val patientRepository: PatientRepository by lazy { LocalPatientRepository(database.patientDao()) }

    override val appointmentRepository: AppointmentRepository by lazy {
        LocalAppointmentRepository(database.appointmentDao())
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
