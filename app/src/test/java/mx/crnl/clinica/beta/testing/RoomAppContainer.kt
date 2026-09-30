package mx.crnl.clinica.beta.testing

import java.time.Clock
import mx.crnl.clinica.beta.app.AppContainer
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.core.demo.LocalDataInitializer
import mx.crnl.clinica.beta.core.demo.SeedOutcome
import mx.crnl.clinica.beta.core.security.PasswordHasher
import mx.crnl.clinica.beta.data.repository.AuditRecorder
import mx.crnl.clinica.beta.data.repository.LocalAppointmentRepository
import mx.crnl.clinica.beta.data.repository.LocalAuthRepository
import mx.crnl.clinica.beta.data.repository.LocalEncounterRepository
import mx.crnl.clinica.beta.data.repository.LocalHomeRepository
import mx.crnl.clinica.beta.data.repository.LocalPatientRepository
import mx.crnl.clinica.beta.data.repository.LocalProfessionalAssignmentRepository
import mx.crnl.clinica.beta.domain.repository.SessionRepository

/** El ensamblado real de la app (repositorios locales sobre Room) con sesión en memoria y reloj fijo. */
class RoomAppContainer(
    database: ClinicalDatabase,
    override val sessionRepository: SessionRepository = FakeSessionRepository(),
    override val clock: Clock = fixedClock(),
) : AppContainer {
    private var sequence = 0
    private val newId: () -> String = { "ui-${sequence++}" }
    private val audit = AuditRecorder(database.auditDao(), clock, newId)

    override val authRepository = LocalAuthRepository(database, sessionRepository, PasswordHasher(defaultIterations = 1_000), audit, clock, newId)
    override val patientRepository = LocalPatientRepository(database, audit, clock, newId)
    override val appointmentRepository = LocalAppointmentRepository(database, audit, clock, newId)
    override val assignmentRepository = LocalProfessionalAssignmentRepository(database, audit, clock, newId)
    override val encounterRepository = LocalEncounterRepository(database, audit, clock, newId)
    override val homeRepository = LocalHomeRepository(database, clock)
    override val localDataInitializer = LocalDataInitializer { SeedOutcome.AlreadyApplied }
}
