package mx.crnl.clinica.beta.testing

import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import mx.crnl.clinica.beta.app.AppContainer
import mx.crnl.clinica.beta.core.demo.LocalDataInitializer
import mx.crnl.clinica.beta.core.demo.SeedOutcome
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.repository.AppointmentRepository
import mx.crnl.clinica.beta.domain.repository.PatientRepository
import mx.crnl.clinica.beta.domain.repository.SessionRepository

class FakeSessionRepository(
    active: Boolean = false,
    private var seedVersion: Int = 0,
) : SessionRepository {
    private val sessionActive = MutableStateFlow(active)

    /** Si no es nulo, las escrituras fallan con este error (simula un disco lleno o dañado). */
    var writeFailure: IOException? = null

    override val isSessionActive: Flow<Boolean> = sessionActive

    override suspend fun startSession() {
        writeFailure?.let { throw it }
        sessionActive.value = true
    }

    override suspend fun endSession() {
        writeFailure?.let { throw it }
        sessionActive.value = false
    }

    override suspend fun appliedSeedVersion(): Int = seedVersion

    override suspend fun recordAppliedSeedVersion(version: Int) {
        seedVersion = version
    }
}

class FakePatientRepository(initial: List<Patient> = emptyList()) : PatientRepository {
    val patients = MutableStateFlow(initial)

    /** Mientras sea true, cada observación falla; sirve para probar el estado de error y el reintento. */
    var failing = false

    override fun observePatients(): Flow<List<Patient>> = flow {
        if (failing) throw IOException("fallo simulado")
        emitAll(patients)
    }

    override suspend fun getPatient(patientId: String): Patient? = patients.value.firstOrNull { it.patientId == patientId }
}

class FakeAppointmentRepository(initial: List<AppointmentSummary> = emptyList()) : AppointmentRepository {
    val appointments = MutableStateFlow(initial)
    var failing = false

    override fun observeAppointments(): Flow<List<AppointmentSummary>> = flow {
        if (failing) throw IOException("fallo simulado")
        emitAll(appointments.map { list -> list.sortedBy { it.start } })
    }
}

class FakeAppContainer(
    override val sessionRepository: SessionRepository = FakeSessionRepository(),
    override val patientRepository: PatientRepository = FakePatientRepository(),
    override val appointmentRepository: AppointmentRepository = FakeAppointmentRepository(),
    override val localDataInitializer: LocalDataInitializer = LocalDataInitializer { SeedOutcome.AlreadyApplied },
    override val clock: Clock = fixedClock(),
) : AppContainer
