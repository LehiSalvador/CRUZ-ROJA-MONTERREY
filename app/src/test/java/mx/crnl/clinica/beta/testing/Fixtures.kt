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
import mx.crnl.clinica.beta.data.local.entity.DemoUserEntity
import mx.crnl.clinica.beta.data.local.entity.PatientEntity
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex

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
    patients: List<PatientEntity> = emptyList(),
    appointments: List<AppointmentEntity> = emptyList(),
): SeedRecords = SeedRecords(
    users = users,
    patients = patients,
    contacts = emptyList(),
    assignments = emptyList(),
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
): Patient = Patient(
    patientId = id,
    patientNumber = number,
    firstName = firstName,
    paternalSurname = paternalSurname,
    maternalSurname = maternalSurname,
    birthDate = birthDate,
    birthPlace = null,
    sex = Sex.FEMALE,
    municipality = "Monterrey",
    populationType = PopulationType.STUDENT,
    status = status,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

fun domainAppointment(
    id: String = "appointment-1",
    patientName: String = "Ana Cavazos Ibarra",
    start: Instant = TestNow.toInstant(),
    minutes: Long = 50,
    status: AppointmentStatus = AppointmentStatus.SCHEDULED,
): AppointmentSummary = AppointmentSummary(
    appointmentId = id,
    patientId = "patient-$id",
    patientName = patientName,
    patientNumber = "CRNL-000001",
    professionalName = "Mariana Elizondo",
    area = ClinicalArea.PSYCHOLOGY,
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
): DemoUserEntity = DemoUserEntity(
    userId = id,
    firstName = firstName,
    paternalSurname = paternalSurname,
    maternalSurname = null,
    email = "$id@example.org",
    roleCode = role,
    areaCode = area,
    status = "ACTIVE",
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
