package mx.crnl.clinica.beta.core.demo

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.AdministrationMode
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AssessmentStatus
import mx.crnl.clinica.beta.domain.model.AssignmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ContactType
import mx.crnl.clinica.beta.domain.model.EncounterStatus
import mx.crnl.clinica.beta.domain.model.EncounterType
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.domain.model.UserRole

/**
 * Garantiza que el conjunto inicial es ficticio, coherente y cargable antes de tocar la base:
 * llaves y referencias válidas, códigos de catálogo conocidos, contactos en rangos reservados y
 * ningún instrumento clínico real. Devuelve todos los problemas encontrados, no solo el primero.
 */
class DemoSeedValidator(private val today: LocalDate) {

    fun validate(seed: DemoSeed): List<String> {
        val report = Report()
        validateMetas(seed, report)
        val users = validateUsers(seed.users, report)
        val patients = validatePatients(seed.patients, users, report)
        validateAssignments(seed.assignments, patients, users, report)
        val appointments = validateAppointments(seed.appointments, patients, users, report)
        val encounters = validateEncounters(seed.encounters, patients, users, appointments, report)
        validateAssessments(seed.assessments, patients, users, encounters, report)
        return report.issues
    }

    private fun validateMetas(seed: DemoSeed, report: Report) {
        seed.metas.forEach { (file, meta) ->
            if (!meta.fictitious) report.add(file, "meta.fictitious", "debe ser true: solo se admiten datos ficticios")
            if (meta.seedVersion != DemoSeed.SUPPORTED_VERSION) {
                report.add(file, "meta.seedVersion", "${meta.seedVersion} no coincide con la versión soportada ${DemoSeed.SUPPORTED_VERSION}")
            }
        }
    }

    private fun validateUsers(users: List<SeedUser>, report: Report): Map<String, SeedUser> {
        val file = DemoSeed.USERS_FILE
        report.checkIds(file, "users", users) { it.userId }
        val emails = HashSet<String>()
        users.forEachIndexed { index, user ->
            val at = "users[$index]"
            if (user.firstName.isBlank() || user.paternalSurname.isBlank()) {
                report.add(file, at, "nombre y apellido paterno son obligatorios")
            }
            if (!isFictitiousEmail(user.email)) {
                report.add(file, "$at.email", "debe usar un dominio reservado para ejemplos (example.org, example.com o example.net)")
            }
            if (!emails.add(user.email.lowercase())) report.add(file, "$at.email", "correo duplicado '${user.email}'")
            val role = report.enumValue<UserRole>(file, "$at.role", user.role)
            val area = report.enumValue<ClinicalArea>(file, "$at.area", user.area)
            report.enumValue<AccountStatus>(file, "$at.status", user.status)
            if (role == UserRole.PROFESSIONAL && area == null) report.add(file, at, "un profesional requiere área")
        }
        return users.associateBy { it.userId }
    }

    private fun validatePatients(
        patients: List<SeedPatient>,
        users: Map<String, SeedUser>,
        report: Report,
    ): Map<String, SeedPatient> {
        val file = DemoSeed.PATIENTS_FILE
        report.checkIds(file, "patients", patients) { it.patientId }
        val folios = HashSet<String>()
        val contactIds = HashSet<String>()
        patients.forEachIndexed { index, patient ->
            val at = "patients[$index]"
            if (!FOLIO_PATTERN.matches(patient.patientNumber)) {
                report.add(file, "$at.patientNumber", "folio '${patient.patientNumber}' no cumple el formato CRNL-000000")
            }
            if (!folios.add(patient.patientNumber)) report.add(file, "$at.patientNumber", "folio duplicado '${patient.patientNumber}'")
            if (patient.firstName.isBlank() || patient.paternalSurname.isBlank()) {
                report.add(file, at, "nombre y apellido paterno son obligatorios")
            }
            val birthDate = report.date(file, "$at.birthDate", patient.birthDate)
            if (birthDate != null && birthDate.isAfter(today)) report.add(file, "$at.birthDate", "la fecha de nacimiento no puede ser futura")
            report.enumValue<Sex>(file, "$at.sex", patient.sex)
            report.enumValue<PopulationType>(file, "$at.populationType", patient.populationType)
            report.enumValue<PatientStatus>(file, "$at.status", patient.status)
            if (patient.createdBy !in users) report.add(file, "$at.createdBy", "usuario '${patient.createdBy}' inexistente")

            patient.contacts.forEachIndexed { contactIndex, contact ->
                val contactAt = "$at.contacts[$contactIndex]"
                if (!UUID_PATTERN.matches(contact.contactId)) report.add(file, contactAt, "id '${contact.contactId}' no es un UUID en minúsculas")
                if (!contactIds.add(contact.contactId)) report.add(file, contactAt, "id duplicado '${contact.contactId}'")
                when (report.enumValue<ContactType>(file, "$contactAt.type", contact.type)) {
                    ContactType.PHONE -> if (!FICTITIOUS_PHONE.matches(contact.value)) {
                        report.add(file, "$contactAt.value", "el teléfono debe estar en el rango ficticio reservado +52810000XXXX")
                    }
                    ContactType.EMAIL -> if (!isFictitiousEmail(contact.value)) {
                        report.add(file, "$contactAt.value", "el correo debe usar un dominio reservado para ejemplos")
                    }
                    null -> Unit
                }
            }
            patient.contacts.filter { it.isPrimary }.groupingBy { it.type }.eachCount()
                .filterValues { it > 1 }.keys
                .forEach { type -> report.add(file, at, "más de un contacto principal de tipo $type") }
        }
        return patients.associateBy { it.patientId }
    }

    private fun validateAssignments(
        assignments: List<SeedAssignment>,
        patients: Map<String, SeedPatient>,
        users: Map<String, SeedUser>,
        report: Report,
    ) {
        val file = DemoSeed.ASSIGNMENTS_FILE
        report.checkIds(file, "assignments", assignments) { it.assignmentId }
        assignments.forEachIndexed { index, assignment ->
            val at = "assignments[$index]"
            if (assignment.patientId !in patients) report.add(file, "$at.patientId", "paciente '${assignment.patientId}' inexistente")
            val area = report.enumValue<ClinicalArea>(file, "$at.area", assignment.area)
            val status = report.enumValue<AssignmentStatus>(file, "$at.status", assignment.status)
            report.professional(file, "$at.professionalId", assignment.professionalId, area, users)
            if (assignment.assignedBy !in users) report.add(file, "$at.assignedBy", "usuario '${assignment.assignedBy}' inexistente")
            if (assignment.startDayOffset > 0) report.add(file, "$at.startDayOffset", "una asignación no puede iniciar en el futuro")
            val end = assignment.endDayOffset
            if (end != null && end <= assignment.startDayOffset) report.add(file, "$at.endDayOffset", "debe ser posterior al inicio")
            if (status == AssignmentStatus.ENDED && end == null) report.add(file, at, "una asignación ENDED requiere endDayOffset")
            if (status == AssignmentStatus.ACTIVE && end != null) report.add(file, at, "una asignación ACTIVE no puede tener fin")
        }
        assignments.filter { it.status == AssignmentStatus.ACTIVE.name }
            .groupingBy { it.patientId to it.area }.eachCount()
            .filterValues { it > 1 }.keys
            .forEach { (patientId, area) -> report.add(file, "assignments", "el paciente $patientId tiene más de una asignación ACTIVE en $area") }
    }

    private fun validateAppointments(
        appointments: List<SeedAppointment>,
        patients: Map<String, SeedPatient>,
        users: Map<String, SeedUser>,
        report: Report,
    ): Map<String, SeedAppointment> {
        val file = DemoSeed.APPOINTMENTS_FILE
        report.checkIds(file, "appointments", appointments) { it.appointmentId }
        appointments.forEachIndexed { index, appointment ->
            val at = "appointments[$index]"
            if (appointment.patientId !in patients) report.add(file, "$at.patientId", "paciente '${appointment.patientId}' inexistente")
            val area = report.enumValue<ClinicalArea>(file, "$at.area", appointment.area)
            report.professional(file, "$at.professionalId", appointment.professionalId, area, users)
            if (appointment.createdBy !in users) report.add(file, "$at.createdBy", "usuario '${appointment.createdBy}' inexistente")
            report.time(file, "$at.startTime", appointment.startTime)
            if (appointment.durationMinutes !in 15..180) report.add(file, "$at.durationMinutes", "debe estar entre 15 y 180")

            when (report.enumValue<AppointmentModality>(file, "$at.modality", appointment.modality)) {
                AppointmentModality.IN_PERSON -> {
                    if (appointment.location.isNullOrBlank()) report.add(file, "$at.location", "una cita presencial requiere ubicación")
                    if (appointment.meetingUrl != null) report.add(file, "$at.meetingUrl", "una cita presencial no lleva enlace")
                }
                AppointmentModality.ONLINE -> {
                    val url = appointment.meetingUrl
                    if (url == null || !FICTITIOUS_MEETING_URL.matches(url)) {
                        report.add(file, "$at.meetingUrl", "una cita en línea requiere un enlace https en un dominio reservado para ejemplos")
                    }
                    if (appointment.location != null) report.add(file, "$at.location", "una cita en línea no lleva ubicación")
                }
                null -> Unit
            }

            val status = report.enumValue<AppointmentStatus>(file, "$at.status", appointment.status)
            if (status != null) {
                val concluded = status in CONCLUDED_STATUSES
                if (appointment.dayOffset < 0 && status !in PAST_STATUSES) {
                    report.add(file, "$at.status", "una cita pasada no puede estar $status")
                }
                if (appointment.dayOffset > 0 && concluded) report.add(file, "$at.status", "una cita futura no puede estar $status")
            }
        }
        return appointments.associateBy { it.appointmentId }
    }

    private fun validateEncounters(
        encounters: List<SeedEncounter>,
        patients: Map<String, SeedPatient>,
        users: Map<String, SeedUser>,
        appointments: Map<String, SeedAppointment>,
        report: Report,
    ): Map<String, SeedEncounter> {
        val file = DemoSeed.ENCOUNTERS_FILE
        report.checkIds(file, "encounters", encounters) { it.encounterId }
        encounters.forEachIndexed { index, encounter ->
            val at = "encounters[$index]"
            if (encounter.patientId !in patients) report.add(file, "$at.patientId", "paciente '${encounter.patientId}' inexistente")
            val area = report.enumValue<ClinicalArea>(file, "$at.area", encounter.area)
            report.professional(file, "$at.professionalId", encounter.professionalId, area, users)
            if (encounter.createdBy !in users) report.add(file, "$at.createdBy", "usuario '${encounter.createdBy}' inexistente")
            report.enumValue<EncounterType>(file, "$at.type", encounter.type)
            report.enumValue<EncounterStatus>(file, "$at.status", encounter.status)
            report.time(file, "$at.time", encounter.time)
            if (encounter.dayOffset > 0) report.add(file, "$at.dayOffset", "un encuentro registrado no puede ocurrir en el futuro")

            val appointmentId = encounter.appointmentId
            if (appointmentId != null) {
                val appointment = appointments[appointmentId]
                when {
                    appointment == null -> report.add(file, "$at.appointmentId", "cita '$appointmentId' inexistente")
                    appointment.patientId != encounter.patientId ||
                        appointment.professionalId != encounter.professionalId ||
                        appointment.area != encounter.area ->
                        report.add(file, "$at.appointmentId", "la cita no corresponde al mismo paciente, profesional y área")
                    appointment.dayOffset != encounter.dayOffset ->
                        report.add(file, "$at.dayOffset", "no coincide con el día de la cita asociada")
                }
            }
        }
        return encounters.associateBy { it.encounterId }
    }

    private fun validateAssessments(
        assessments: List<SeedAssessment>,
        patients: Map<String, SeedPatient>,
        users: Map<String, SeedUser>,
        encounters: Map<String, SeedEncounter>,
        report: Report,
    ) {
        val file = DemoSeed.ASSESSMENTS_FILE
        report.checkIds(file, "assessments", assessments) { it.assessmentId }
        val resultIds = HashSet<String>()
        assessments.forEachIndexed { index, assessment ->
            val at = "assessments[$index]"
            if (assessment.patientId !in patients) report.add(file, "$at.patientId", "paciente '${assessment.patientId}' inexistente")
            if (assessment.professionalId !in users) report.add(file, "$at.professionalId", "usuario '${assessment.professionalId}' inexistente")
            val encounterId = assessment.encounterId
            if (encounterId != null) {
                val encounter = encounters[encounterId]
                when {
                    encounter == null -> report.add(file, "$at.encounterId", "encuentro '$encounterId' inexistente")
                    encounter.patientId != assessment.patientId -> report.add(file, "$at.encounterId", "el encuentro pertenece a otro paciente")
                }
            }
            // Los instrumentos reales (BAI, BDI-II, etc.) son contenido licenciado: solo se admiten marcadores.
            if (!assessment.instrumentCode.startsWith(PLACEHOLDER_INSTRUMENT_PREFIX)) {
                report.add(file, "$at.instrumentCode", "solo se admiten instrumentos marcadores ($PLACEHOLDER_INSTRUMENT_PREFIX*)")
            }
            report.enumValue<AdministrationMode>(file, "$at.administrationMode", assessment.administrationMode)
            report.time(file, "$at.time", assessment.time)
            if (assessment.durationMinutes <= 0) report.add(file, "$at.durationMinutes", "debe ser mayor que cero")
            if (assessment.dayOffset > 0) report.add(file, "$at.dayOffset", "una evaluación aplicada no puede ocurrir en el futuro")

            val status = report.enumValue<AssessmentStatus>(file, "$at.status", assessment.status)
            val result = assessment.result
            if (status == AssessmentStatus.COMPLETED && result == null) report.add(file, at, "una evaluación COMPLETED requiere resultado")
            if (status != null && status != AssessmentStatus.COMPLETED && result != null) {
                report.add(file, at, "solo una evaluación COMPLETED puede tener resultado")
            }
            if (result != null) {
                if (!UUID_PATTERN.matches(result.resultId)) report.add(file, "$at.result", "id '${result.resultId}' no es un UUID en minúsculas")
                if (!resultIds.add(result.resultId)) report.add(file, "$at.result", "id duplicado '${result.resultId}'")
                if (!result.scoringVersion.startsWith(PLACEHOLDER_SCORING_PREFIX)) {
                    report.add(file, "$at.result.scoringVersion", "debe iniciar con '$PLACEHOLDER_SCORING_PREFIX': no hay scoring autorizado")
                }
            }
        }
    }

    private fun Report.professional(
        file: String,
        path: String,
        userId: String,
        area: ClinicalArea?,
        users: Map<String, SeedUser>,
    ) {
        val user = users[userId]
        when {
            user == null -> add(file, path, "usuario '$userId' inexistente")
            user.role != UserRole.PROFESSIONAL.name -> add(file, path, "el usuario '$userId' no es PROFESSIONAL")
            area != null && user.area != area.name -> add(file, path, "el profesional pertenece a ${user.area}, no a ${area.name}")
        }
    }

    private class Report {
        val issues = mutableListOf<String>()

        fun add(file: String, path: String, message: String) {
            issues += "$file $path: $message"
        }

        fun <T> checkIds(file: String, collection: String, items: List<T>, id: (T) -> String) {
            val seen = HashSet<String>()
            items.forEachIndexed { index, item ->
                val value = id(item)
                if (!UUID_PATTERN.matches(value)) add(file, "$collection[$index]", "id '$value' no es un UUID en minúsculas")
                if (!seen.add(value)) add(file, "$collection[$index]", "id duplicado '$value'")
            }
        }

        inline fun <reified E : Enum<E>> enumValue(file: String, path: String, value: String?): E? {
            if (value == null) return null
            val parsed = enumValues<E>().firstOrNull { it.name == value }
            if (parsed == null) add(file, path, "valor '$value' no válido (${enumValues<E>().joinToString("|")})")
            return parsed
        }

        fun date(file: String, path: String, value: String): LocalDate? = try {
            LocalDate.parse(value)
        } catch (error: DateTimeParseException) {
            add(file, path, "fecha '$value' inválida (yyyy-MM-dd)")
            null
        }

        fun time(file: String, path: String, value: String): LocalTime? = try {
            LocalTime.parse(value)
        } catch (error: DateTimeParseException) {
            add(file, path, "hora '$value' inválida (HH:mm)")
            null
        }
    }

    private companion object {
        val UUID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        val FOLIO_PATTERN = Regex("^CRNL-\\d{6}$")
        val FICTITIOUS_PHONE = Regex("^\\+52810000\\d{4}$")
        val FICTITIOUS_MEETING_URL = Regex("^https://([a-z0-9-]+\\.)*example\\.(org|com|net)(/.*)?$")
        val EXAMPLE_DOMAINS = setOf("example.org", "example.com", "example.net")
        val PAST_STATUSES = setOf(
            AppointmentStatus.COMPLETED,
            AppointmentStatus.NO_SHOW,
            AppointmentStatus.CANCELLED,
            AppointmentStatus.RESCHEDULED,
        )
        val CONCLUDED_STATUSES = setOf(AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW)
        const val PLACEHOLDER_INSTRUMENT_PREFIX = "DEV_PLACEHOLDER_"
        const val PLACEHOLDER_SCORING_PREFIX = "placeholder"

        fun isFictitiousEmail(value: String): Boolean =
            value.contains('@') && value.substringAfterLast('@').lowercase() in EXAMPLE_DOMAINS
    }
}
