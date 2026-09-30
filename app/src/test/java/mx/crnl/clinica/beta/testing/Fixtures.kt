package mx.crnl.clinica.beta.testing

import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import mx.crnl.clinica.beta.core.demo.SeedFileReader
import mx.crnl.clinica.beta.data.local.dao.SeedRecords
import mx.crnl.clinica.beta.data.local.entity.AppointmentEntity
import mx.crnl.clinica.beta.data.local.entity.DemoCredentialEntity
import mx.crnl.clinica.beta.data.local.entity.DemoUserEntity
import mx.crnl.clinica.beta.data.local.entity.PatientContactEntity
import mx.crnl.clinica.beta.data.local.entity.PatientEntity
import mx.crnl.clinica.beta.data.local.entity.ProfessionalAssignmentEntity
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ActiveAssignment
import mx.crnl.clinica.beta.domain.model.AreaActivity
import mx.crnl.clinica.beta.domain.model.AssignmentStatus
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.AssessmentSummary
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ContactType
import mx.crnl.clinica.beta.domain.model.EncounterSummary
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.model.PatientAssignment
import mx.crnl.clinica.beta.domain.model.PatientContact
import mx.crnl.clinica.beta.domain.model.PatientDetail
import mx.crnl.clinica.beta.domain.model.PatientDraft
import mx.crnl.clinica.beta.domain.model.PatientRecord
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole

val TestZone: ZoneId = ZoneId.of("America/Monterrey")

/** 2026-09-29 10:00 hora de Monterrey. */
val TestNow: ZonedDateTime = ZonedDateTime.of(2026, 9, 29, 10, 0, 0, 0, TestZone)

fun fixedClock(now: ZonedDateTime = TestNow): Clock = Clock.fixed(now.toInstant(), TestZone)

/** Lee los JSON reales de `src/main/assets` (el directorio de trabajo de las pruebas es el módulo `app`). */
class FileSystemSeedReader(private val directory: File = File("src/main/assets")) : SeedFileReader {
    override fun read(fileName: String): String = File(directory, fileName).readText(Charsets.UTF_8)
}

fun records(
    users: List<DemoUserEntity> = emptyList(),
    credentials: List<DemoCredentialEntity> = emptyList(),
    patients: List<PatientEntity> = emptyList(),
    contacts: List<PatientContactEntity> = emptyList(),
    assignments: List<ProfessionalAssignmentEntity> = emptyList(),
    appointments: List<AppointmentEntity> = emptyList(),
): SeedRecords = SeedRecords(
    users = users,
    credentials = credentials,
    patients = patients,
    contacts = contacts,
    assignments = assignments,
    appointments = appointments,
    encounters = emptyList(),
    assessments = emptyList(),
    results = emptyList(),
    auditEntries = emptyList(),
)

fun domainPatient(
    id: String = "patient-1",
    number: String = "CRNL-000001",
    firstName: String = "Ana",
    paternalSurname: String = "Cavazos",
    maternalSurname: String? = "Ibarra",
    birthDate: LocalDate = LocalDate.of(1998, 5, 14),
    status: PatientStatus = PatientStatus.ACTIVE,
    sex: Sex = Sex.FEMALE,
    updatedAt: Instant = Instant.EPOCH,
): Patient = Patient(
    patientId = id,
    patientNumber = number,
    firstName = firstName,
    paternalSurname = paternalSurname,
    maternalSurname = maternalSurname,
    birthDate = birthDate,
    birthPlace = null,
    sex = sex,
    municipality = "Monterrey",
    populationType = PopulationType.STUDENT,
    status = status,
    createdAt = Instant.EPOCH,
    updatedAt = updatedAt,
)

fun patientRecord(
    patient: Patient = domainPatient(),
    phone: String? = null,
    email: String? = null,
    assignments: List<ActiveAssignment> = emptyList(),
): PatientRecord = PatientRecord(
    patient = patient,
    contacts = listOfNotNull(
        phone?.let { PatientContact("phone-${patient.patientId}", ContactType.PHONE, it, true) },
        email?.let { PatientContact("email-${patient.patientId}", ContactType.EMAIL, it, true) },
    ),
    assignments = assignments,
)

fun patientDraft(
    firstName: String = "Beatriz",
    paternalSurname: String = "Lozano",
    maternalSurname: String? = "Garza",
    birthDate: LocalDate = LocalDate.of(1995, 3, 8),
    phone: String? = "8112345678",
    email: String? = "beatriz.lozano@example.org",
): PatientDraft = PatientDraft(
    firstName = firstName,
    paternalSurname = paternalSurname,
    maternalSurname = maternalSurname,
    birthDate = birthDate,
    birthPlace = "Monterrey, Nuevo León",
    sex = Sex.FEMALE,
    municipality = "Monterrey",
    populationType = PopulationType.GENERAL_PUBLIC,
    phone = phone,
    email = email,
)

fun userAccount(
    id: String = "user-1",
    firstName: String = "Mariana",
    role: UserRole = UserRole.PROFESSIONAL,
    area: ClinicalArea? = ClinicalArea.PSYCHOLOGY,
    status: AccountStatus = AccountStatus.ACTIVE,
    email: String = "$id@example.org",
    license: String? = "00000103",
): UserAccount = UserAccount(
    userId = id,
    firstName = firstName,
    paternalSurname = "Elizondo",
    maternalSurname = "Cantú",
    email = email,
    role = role,
    area = area,
    professionalLicense = license,
    status = status,
)

fun domainAppointment(
    id: String = "appointment-1",
    patientName: String = "Ana Cavazos Ibarra",
    start: Instant = TestNow.toInstant(),
    minutes: Long = 50,
    status: AppointmentStatus = AppointmentStatus.SCHEDULED,
    professionalId: String = "user-1",
    area: ClinicalArea = ClinicalArea.PSYCHOLOGY,
): AppointmentSummary = AppointmentSummary(
    appointmentId = id,
    patientId = "patient-$id",
    patientName = patientName,
    patientNumber = "CRNL-000001",
    professionalId = professionalId,
    professionalName = "Mariana Elizondo",
    area = area,
    start = start,
    end = start.plusSeconds(minutes * 60),
    modality = AppointmentModality.IN_PERSON,
    location = "Consultorio 2",
    status = status,
)

fun userEntity(
    id: String = "user-1",
    role: String = "PROFESSIONAL",
    area: String? = "PSYCHOLOGY",
    firstName: String = "Profesional",
    paternalSurname: String = "Prueba",
    status: String = "ACTIVE",
    email: String = "$id@example.org",
    license: String? = null,
): DemoUserEntity = DemoUserEntity(
    userId = id,
    firstName = firstName,
    paternalSurname = paternalSurname,
    maternalSurname = null,
    email = email,
    roleCode = role,
    areaCode = area,
    status = status,
    createdAt = 1_000L,
    updatedAt = 1_000L,
    professionalLicense = license,
)

fun credentialEntity(
    userId: String = "user-1",
    salt: String = "c2FsdA==",
    hash: String = "aGFzaA==",
): DemoCredentialEntity = DemoCredentialEntity(
    userId = userId,
    algorithm = "PBKDF2WithHmacSHA256",
    iterations = 1_000,
    salt = salt,
    passwordHash = hash,
    createdAt = 1_000L,
    updatedAt = 1_000L,
)

fun patientEntity(
    id: String = "patient-1",
    number: String = "CRNL-000001",
    firstName: String = "Paciente",
    paternalSurname: String = "Ejemplo",
    maternalSurname: String? = "Uno",
    createdBy: String = "user-1",
    status: String = "ACTIVE",
    birthDate: String = "1990-06-15",
): PatientEntity = PatientEntity(
    patientId = id,
    patientNumber = number,
    firstName = firstName,
    paternalSurname = paternalSurname,
    maternalSurname = maternalSurname,
    birthDate = birthDate,
    birthPlace = "Monterrey, Nuevo León",
    sexCode = "FEMALE",
    municipality = "Monterrey",
    populationTypeCode = "GENERAL_PUBLIC",
    status = status,
    createdAt = 2_000L,
    createdBy = createdBy,
    updatedAt = 2_000L,
    updatedBy = createdBy,
)

fun appointmentEntity(
    id: String = "appointment-1",
    patientId: String = "patient-1",
    professionalId: String = "user-1",
    start: Instant = TestNow.toInstant(),
    minutes: Long = 50,
    status: String = "SCHEDULED",
): AppointmentEntity = AppointmentEntity(
    appointmentId = id,
    patientId = patientId,
    areaCode = "PSYCHOLOGY",
    professionalId = professionalId,
    startDateTime = start.toEpochMilli(),
    endDateTime = start.plusSeconds(minutes * 60).toEpochMilli(),
    modality = "IN_PERSON",
    location = "Consultorio 1",
    meetingUrl = null,
    status = status,
    administrativeNotes = null,
    createdBy = professionalId,
    createdAt = 3_000L,
    updatedAt = 3_000L,
)

fun contactEntity(
    id: String,
    patientId: String = "patient-1",
    type: String = "PHONE",
    value: String = "+528100000101",
    primary: Boolean = true,
    status: String = "ACTIVE",
    createdAt: Long = 2_500L,
): PatientContactEntity = PatientContactEntity(
    contactId = id,
    patientId = patientId,
    contactType = type,
    contactValue = value,
    isPrimary = primary,
    status = status,
    createdAt = createdAt,
    updatedAt = createdAt,
)

fun assignmentEntity(
    id: String,
    patientId: String = "patient-1",
    professionalId: String = "user-1",
    area: String = "PSYCHOLOGY",
    status: String = "ACTIVE",
    assignedBy: String = "user-1",
): ProfessionalAssignmentEntity = ProfessionalAssignmentEntity(
    assignmentId = id,
    patientId = patientId,
    areaCode = area,
    professionalId = professionalId,
    startAt = 2_600L,
    endAt = if (status == "ENDED") 2_700L else null,
    status = status,
    reason = null,
    assignedBy = assignedBy,
    createdAt = 2_600L,
)

fun patientDetail(
    patient: Patient = domainPatient(),
    contacts: List<PatientContact> = emptyList(),
    assignments: List<PatientAssignment> = emptyList(),
    appointments: List<AppointmentSummary> = emptyList(),
    encounters: List<EncounterSummary> = emptyList(),
    assessments: List<AssessmentSummary> = emptyList(),
    assignmentHistory: List<PatientAssignment> = assignments,
    viewableAreas: Set<ClinicalArea> = ClinicalArea.entries.toSet(),
    restrictedAreas: List<AreaActivity> = emptyList(),
): PatientDetail = PatientDetail(
    patient, contacts, assignments, appointments, encounters, assessments, assignmentHistory, viewableAreas, restrictedAreas,
)

fun patientAssignment(
    area: ClinicalArea = ClinicalArea.PSYCHOLOGY,
    professionalId: String = "user-1",
    professionalName: String = "Mariana Elizondo",
    since: Instant = Instant.EPOCH,
    id: String = "assignment-$area",
) = PatientAssignment(
    assignmentId = id,
    area = area,
    professionalId = professionalId,
    professionalName = professionalName,
    since = since,
    until = null,
    status = AssignmentStatus.ACTIVE,
    reason = "Asignación inicial",
    assignedByName = "Claudia Benavides Rangel",
)
