package mx.crnl.clinica.beta.data.local.mapper

import java.time.Instant
import java.time.LocalDate
import mx.crnl.clinica.beta.data.local.dao.PatientAggregate
import mx.crnl.clinica.beta.data.local.entity.PatientContactEntity
import mx.crnl.clinica.beta.data.local.entity.PatientEntity
import mx.crnl.clinica.beta.domain.model.ActiveAssignment
import mx.crnl.clinica.beta.domain.model.AssignmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ContactType
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.model.PatientContact
import mx.crnl.clinica.beta.domain.model.PatientRecord
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.RecordStatus
import mx.crnl.clinica.beta.domain.model.Sex

fun PatientEntity.toDomain(): Patient = Patient(
    patientId = patientId,
    patientNumber = patientNumber,
    firstName = firstName,
    paternalSurname = paternalSurname,
    maternalSurname = maternalSurname,
    birthDate = LocalDate.parse(birthDate),
    birthPlace = birthPlace,
    sex = Sex.valueOf(sexCode),
    municipality = municipality,
    populationType = PopulationType.valueOf(populationTypeCode),
    status = PatientStatus.valueOf(status),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun PatientContactEntity.toDomain(): PatientContact = PatientContact(
    contactId = contactId,
    type = ContactType.valueOf(contactType),
    value = contactValue,
    isPrimary = isPrimary,
)

/** Solo se consideran los contactos y asignaciones vigentes. */
fun PatientAggregate.toRecord(): PatientRecord = PatientRecord(
    patient = patient.toDomain(),
    contacts = contacts.filter { it.status == RecordStatus.ACTIVE.name }.map { it.toDomain() },
    assignments = assignments
        .filter { it.status == AssignmentStatus.ACTIVE.name }
        .map { ActiveAssignment(area = ClinicalArea.valueOf(it.areaCode), professionalId = it.professionalId) },
)
