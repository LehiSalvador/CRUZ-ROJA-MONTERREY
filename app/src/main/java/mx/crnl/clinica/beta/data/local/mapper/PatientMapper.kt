package mx.crnl.clinica.beta.data.local.mapper

import java.time.Instant
import java.time.LocalDate
import mx.crnl.clinica.beta.data.local.entity.PatientEntity
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.PopulationType
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
