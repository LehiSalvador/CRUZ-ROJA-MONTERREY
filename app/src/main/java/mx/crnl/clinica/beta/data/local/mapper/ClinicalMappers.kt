package mx.crnl.clinica.beta.data.local.mapper

import java.time.Instant
import mx.crnl.clinica.beta.data.local.dao.AppointmentDetailRow
import mx.crnl.clinica.beta.data.local.dao.AppointmentListRow
import mx.crnl.clinica.beta.data.local.dao.AssignmentRow
import mx.crnl.clinica.beta.data.local.dao.EncounterDetailRow
import mx.crnl.clinica.beta.data.local.entity.DemoUserEntity
import mx.crnl.clinica.beta.domain.model.AppointmentDetail
import mx.crnl.clinica.beta.domain.model.AssignmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.EncounterDetail
import mx.crnl.clinica.beta.domain.model.EncounterStatus
import mx.crnl.clinica.beta.domain.model.EncounterSummary
import mx.crnl.clinica.beta.domain.model.EncounterType
import mx.crnl.clinica.beta.domain.model.PatientAssignment
import mx.crnl.clinica.beta.domain.model.ProfessionalOption

fun AssignmentRow.toDomain(): PatientAssignment = PatientAssignment(
    assignmentId = assignmentId,
    area = ClinicalArea.valueOf(areaCode),
    professionalId = professionalId,
    professionalName = personName(professionalFirstName, professionalPaternalSurname, null),
    since = Instant.ofEpochMilli(startAt),
    until = endAt?.let(Instant::ofEpochMilli),
    status = AssignmentStatus.valueOf(status),
    reason = reason,
    assignedByName = personName(assignedByFirstName, assignedByPaternalSurname, assignedByMaternalSurname),
)

fun AppointmentDetailRow.toDomain(encounters: List<EncounterSummary>): AppointmentDetail = AppointmentDetail(
    summary = AppointmentListRow(
        appointmentId = appointmentId,
        patientId = patientId,
        patientFirstName = patientFirstName,
        patientPaternalSurname = patientPaternalSurname,
        patientMaternalSurname = patientMaternalSurname,
        patientNumber = patientNumber,
        professionalId = professionalId,
        professionalFirstName = professionalFirstName,
        professionalPaternalSurname = professionalPaternalSurname,
        areaCode = areaCode,
        startDateTime = startDateTime,
        endDateTime = endDateTime,
        modality = modality,
        location = location,
        status = status,
    ).toDomain(),
    patientFirstName = patientFirstName,
    contactPhone = contactPhone,
    meetingUrl = meetingUrl,
    administrativeNotes = administrativeNotes,
    createdByName = personName(createdByFirstName, createdByPaternalSurname, createdByMaternalSurname),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    encounters = encounters,
)

fun EncounterDetailRow.toSummary(): EncounterSummary = EncounterSummary(
    encounterId = encounterId,
    area = ClinicalArea.valueOf(areaCode),
    professionalName = personName(professionalFirstName, professionalPaternalSurname, null),
    type = EncounterType.valueOf(encounterTypeCode),
    status = EncounterStatus.valueOf(status),
    eventAt = Instant.ofEpochMilli(eventAt),
    appointmentId = appointmentId,
)

fun EncounterDetailRow.toDomain(): EncounterDetail = EncounterDetail(
    summary = toSummary(),
    patientId = patientId,
    patientName = personName(patientFirstName, patientPaternalSurname, patientMaternalSurname),
    patientNumber = patientNumber,
    professionalId = professionalId,
    appointmentStart = appointmentStart?.let(Instant::ofEpochMilli),
    recordedAt = Instant.ofEpochMilli(recordedAt),
    createdByName = personName(createdByFirstName, createdByPaternalSurname, createdByMaternalSurname),
)

fun DemoUserEntity.toProfessionalOption(): ProfessionalOption = ProfessionalOption(
    userId = userId,
    fullName = personName(firstName, paternalSurname, maternalSurname),
    professionalLicense = professionalLicense,
    area = ClinicalArea.valueOf(requireNotNull(areaCode)),
)

