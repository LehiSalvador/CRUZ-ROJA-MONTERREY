package mx.crnl.clinica.beta.data.local.mapper

import java.time.Instant
import mx.crnl.clinica.beta.data.local.dao.AppointmentListRow
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.ClinicalArea

fun AppointmentListRow.toDomain(): AppointmentSummary = AppointmentSummary(
    appointmentId = appointmentId,
    patientId = patientId,
    patientName = personName(patientFirstName, patientPaternalSurname, patientMaternalSurname),
    patientNumber = patientNumber,
    professionalId = professionalId,
    professionalName = personName(professionalFirstName, professionalPaternalSurname, null),
    area = ClinicalArea.valueOf(areaCode),
    start = Instant.ofEpochMilli(startDateTime),
    end = Instant.ofEpochMilli(endDateTime),
    modality = AppointmentModality.valueOf(modality),
    location = location,
    status = AppointmentStatus.valueOf(status),
)

internal fun personName(firstName: String, paternalSurname: String, maternalSurname: String?): String =
    listOfNotNull(firstName, paternalSurname, maternalSurname).joinToString(" ")
