package mx.crnl.clinica.beta.testing

import java.io.IOException
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import mx.crnl.clinica.beta.app.AppContainer
import mx.crnl.clinica.beta.core.demo.LocalDataInitializer
import mx.crnl.clinica.beta.core.demo.SeedOutcome
import mx.crnl.clinica.beta.domain.account.AccountRequest
import mx.crnl.clinica.beta.domain.account.AccountRequestValidator
import mx.crnl.clinica.beta.domain.home.HomeSummary
import mx.crnl.clinica.beta.domain.home.PatientMetric
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.ContactType
import mx.crnl.clinica.beta.domain.model.DuplicateCandidate
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.model.PatientContact
import mx.crnl.clinica.beta.domain.model.PatientDetail
import mx.crnl.clinica.beta.domain.model.PatientDraft
import mx.crnl.clinica.beta.domain.model.PatientRecord
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.PatientSummary
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.domain.patient.DuplicateDetector
import mx.crnl.clinica.beta.domain.patient.PatientNumber
import mx.crnl.clinica.beta.domain.patient.PatientSearch
import mx.crnl.clinica.beta.domain.repository.AccountRequestResult
import mx.crnl.clinica.beta.domain.repository.AppointmentRepository
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.HomeRepository
import mx.crnl.clinica.beta.domain.repository.PatientFilter
import mx.crnl.clinica.beta.domain.repository.PatientNotFoundException
import mx.crnl.clinica.beta.domain.repository.PatientRepository
import mx.crnl.clinica.beta.domain.repository.SessionRepository
import mx.crnl.clinica.beta.domain.repository.SignInResult
import mx.crnl.clinica.beta.domain.text.TextNormalizer

class FakeSessionRepository(
    userId: String? = null,
    private var seedVersion: Int = 0,
) : SessionRepository {
    private val session = MutableStateFlow(userId)

    /** Si no es nulo, las escrituras fallan con este error (simula un disco lleno o dañado). */
    var writeFailure: IOException? = null

    override val sessionUserId: Flow<String?> = session

    override suspend fun startSession(userId: String) {
        writeFailure?.let { throw it }
        session.value = userId
    }

    override suspend fun endSession() {
        writeFailure?.let { throw it }
        session.value = null
    }

    override suspend fun appliedSeedVersion(): Int = seedVersion

    override suspend fun recordAppliedSeedVersion(version: Int) {
        seedVersion = version
    }
}

/** Cuentas en memoria con su contraseña; reproduce las reglas de acceso del repositorio real. */
class FakeAuthRepository(
    accounts: List<Pair<UserAccount, String>> = emptyList(),
    initialUserId: String? = null,
) : AuthRepository {
    private val passwords = accounts.associate { (user, password) -> user.userId to password }.toMutableMap()
    val users = MutableStateFlow(accounts.map { it.first })
    private val sessionUserId = MutableStateFlow(initialUserId)

    var signInFailure: IOException? = null
    var signOutFailure: IOException? = null
    val signInAttempts = mutableListOf<Pair<String, String>>()
    val submittedRequests = mutableListOf<AccountRequest>()
    var restoreCalls = 0

    override val currentUser: Flow<UserAccount?> = combine(sessionUserId, users) { id, list ->
        list.firstOrNull { it.userId == id }?.takeIf { it.status == AccountStatus.ACTIVE }
    }

    override suspend fun restoreSession(): UserAccount? {
        restoreCalls++
        val user = users.value.firstOrNull { it.userId == sessionUserId.value }?.takeIf { it.status == AccountStatus.ACTIVE }
        if (user == null) sessionUserId.value = null
        return user
    }

    override suspend fun signIn(email: String, password: String): SignInResult {
        signInAttempts += email to password
        signInFailure?.let { throw it }
        val user = users.value.firstOrNull { it.email == TextNormalizer.email(email) }
        if (user == null || passwords[user.userId] != password) return SignInResult.InvalidCredentials
        if (user.status != AccountStatus.ACTIVE) return SignInResult.AccountNotActive(user.status)
        sessionUserId.value = user.userId
        return SignInResult.Success(user)
    }

    override suspend fun signOut() {
        signOutFailure?.let { throw it }
        sessionUserId.value = null
    }

    override suspend fun requestAccount(request: AccountRequest): AccountRequestResult {
        submittedRequests += request
        val issues = AccountRequestValidator.validate(request)
        if (issues.isNotEmpty()) return AccountRequestResult.Invalid(issues)
        val email = TextNormalizer.email(request.email)
        if (users.value.any { it.email == email }) return AccountRequestResult.EmailAlreadyRegistered
        val user = UserAccount(
            userId = "requested-${users.value.size}",
            firstName = request.firstName,
            paternalSurname = request.paternalSurname,
            maternalSurname = request.maternalSurname.ifBlank { null },
            email = email,
            role = requireNotNull(request.role),
            area = request.area,
            professionalLicense = request.professionalLicense.ifBlank { null },
            status = AccountStatus.PENDING_APPROVAL,
        )
        passwords[user.userId] = request.password
        users.value = users.value + user
        return AccountRequestResult.Submitted
    }
}

class FakePatientRepository(initial: List<PatientRecord> = emptyList()) : PatientRepository {
    val records = MutableStateFlow(initial)
    val details = MutableStateFlow<Map<String, PatientDetail>>(emptyMap())

    /** Mientras sea true, cada observación falla; sirve para probar el estado de error y el reintento. */
    var failing = false
    var failingWrites = false
    var duplicateLookupFailure: IOException? = null

    val created = mutableListOf<Pair<PatientDraft, String>>()
    val updated = mutableListOf<Triple<String, PatientDraft, String>>()
    val viewed = mutableListOf<Pair<String, String>>()
    val duplicateLookups = mutableListOf<Pair<PatientDraft, String?>>()

    override fun observePatients(query: String, filter: PatientFilter): Flow<List<PatientSummary>> = flow {
        if (failing) throw IOException("fallo simulado")
        emitAll(
            records.map { list ->
                val scoped = list.filter { record ->
                    when (filter) {
                        PatientFilter.All -> true
                        is PatientFilter.AssignedTo -> record.assignments.any { it.professionalId == filter.professionalId }
                    }
                }
                PatientSearch.filter(scoped, query)
                    .map { PatientSummary(it.patient, it.assignments.map { assignment -> assignment.area }.distinct()) }
            },
        )
    }

    override suspend fun getPatient(patientId: String): Patient? = records.value.firstOrNull { it.patient.patientId == patientId }?.patient

    override fun observePatientDetail(patientId: String): Flow<PatientDetail?> = flow {
        if (failing) throw IOException("fallo simulado")
        emitAll(details.map { it[patientId] })
    }

    override suspend fun getPatientDraft(patientId: String): PatientDraft? {
        if (failing) throw IOException("fallo simulado")
        val record = records.value.firstOrNull { it.patient.patientId == patientId } ?: return null
        return record.patient.toDraft(record.contacts)
    }

    override suspend fun findDuplicateCandidates(draft: PatientDraft, excludePatientId: String?): List<DuplicateCandidate> {
        duplicateLookups += draft to excludePatientId
        duplicateLookupFailure?.let { throw it }
        return DuplicateDetector.detect(draft, records.value, excludePatientId)
    }

    override suspend fun createPatient(draft: PatientDraft, actorUserId: String): Patient {
        if (failingWrites) throw IOException("fallo simulado")
        created += draft to actorUserId
        val patient = Patient(
            patientId = "created-${created.size}",
            patientNumber = PatientNumber.next(records.value.map { it.patient.patientNumber }),
            firstName = draft.firstName,
            paternalSurname = draft.paternalSurname,
            maternalSurname = draft.maternalSurname,
            birthDate = draft.birthDate,
            birthPlace = draft.birthPlace,
            sex = draft.sex,
            municipality = draft.municipality,
            populationType = draft.populationType,
            status = PatientStatus.ACTIVE,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val contacts = listOfNotNull(
            draft.phone?.let { PatientContact("phone-${created.size}", ContactType.PHONE, it, true) },
            draft.email?.let { PatientContact("email-${created.size}", ContactType.EMAIL, it, true) },
        )
        records.value = records.value + PatientRecord(patient, contacts, emptyList())
        return patient
    }

    override suspend fun updatePatient(patientId: String, draft: PatientDraft, actorUserId: String): Patient {
        if (failingWrites) throw IOException("fallo simulado")
        val current = records.value.firstOrNull { it.patient.patientId == patientId } ?: throw PatientNotFoundException(patientId)
        updated += Triple(patientId, draft, actorUserId)
        val patient = current.patient.copy(
            firstName = draft.firstName,
            paternalSurname = draft.paternalSurname,
            maternalSurname = draft.maternalSurname,
            birthDate = draft.birthDate,
            birthPlace = draft.birthPlace,
            sex = draft.sex,
            municipality = draft.municipality,
            populationType = draft.populationType,
        )
        val contacts = listOfNotNull(
            draft.phone?.let { PatientContact("phone-$patientId", ContactType.PHONE, it, true) },
            draft.email?.let { PatientContact("email-$patientId", ContactType.EMAIL, it, true) },
        )
        records.value = records.value.map { if (it.patient.patientId == patientId) PatientRecord(patient, contacts, it.assignments) else it }
        return patient
    }

    override suspend fun recordPatientViewed(patientId: String, actorUserId: String) {
        viewed += patientId to actorUserId
    }

    private fun Patient.toDraft(contacts: List<PatientContact>) = PatientDraft(
        firstName = firstName,
        paternalSurname = paternalSurname,
        maternalSurname = maternalSurname,
        birthDate = birthDate,
        birthPlace = birthPlace,
        sex = sex,
        municipality = municipality,
        populationType = populationType,
        phone = contacts.firstOrNull { it.type == ContactType.PHONE }?.value,
        email = contacts.firstOrNull { it.type == ContactType.EMAIL }?.value,
    )
}

class FakeAppointmentRepository(initial: List<AppointmentSummary> = emptyList()) : AppointmentRepository {
    val appointments = MutableStateFlow(initial)
    var failing = false

    override fun observeAppointments(): Flow<List<AppointmentSummary>> = flow {
        if (failing) throw IOException("fallo simulado")
        emitAll(appointments.map { list -> list.sortedBy { it.start } })
    }
}

class FakeHomeRepository(initial: HomeSummary = emptyHomeSummary()) : HomeRepository {
    val summary = MutableStateFlow(initial)
    var failing = false
    val requestedFor = mutableListOf<UserAccount>()

    override fun observeSummary(user: UserAccount): Flow<HomeSummary> = flow {
        requestedFor += user
        if (failing) throw IOException("fallo simulado")
        emitAll(summary)
    }
}

fun emptyHomeSummary(role: UserRole = UserRole.PROFESSIONAL) = HomeSummary(
    patientMetric = if (role == UserRole.PROFESSIONAL) PatientMetric.ASSIGNED_TO_USER else PatientMetric.ALL_ACTIVE_PATIENTS,
    patientCount = 0,
    upcomingAppointmentCount = 0,
    pendingRequestCount = 0,
    recentPatients = emptyList(),
    upcomingAppointments = emptyList(),
)

class FakeAppContainer(
    override val sessionRepository: SessionRepository = FakeSessionRepository(),
    override val authRepository: AuthRepository = FakeAuthRepository(),
    override val patientRepository: PatientRepository = FakePatientRepository(),
    override val appointmentRepository: AppointmentRepository = FakeAppointmentRepository(),
    override val homeRepository: HomeRepository = FakeHomeRepository(),
    override val localDataInitializer: LocalDataInitializer = LocalDataInitializer { SeedOutcome.AlreadyApplied },
    override val clock: Clock = fixedClock(),
) : AppContainer

fun fakePatientRepository(vararg patients: Patient) =
    FakePatientRepository(patients.map { PatientRecord(it, emptyList(), emptyList()) })
