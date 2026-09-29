package mx.crnl.clinica.beta.core.demo

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import mx.crnl.clinica.beta.data.local.dao.SeedRecords
import mx.crnl.clinica.beta.data.local.entity.AppointmentEntity
import mx.crnl.clinica.beta.data.local.entity.AssessmentEntity
import mx.crnl.clinica.beta.data.local.entity.AssessmentResultEntity
import mx.crnl.clinica.beta.data.local.entity.AuditEntryEntity
import mx.crnl.clinica.beta.data.local.entity.ClinicalEncounterEntity
import mx.crnl.clinica.beta.data.local.entity.DemoCredentialEntity
import mx.crnl.clinica.beta.data.local.entity.DemoUserEntity
import mx.crnl.clinica.beta.data.local.entity.PatientContactEntity
import mx.crnl.clinica.beta.data.local.entity.PatientEntity
import mx.crnl.clinica.beta.data.local.entity.ProfessionalAssignmentEntity
import mx.crnl.clinica.beta.domain.model.AssessmentStatus
import mx.crnl.clinica.beta.domain.model.RecordStatus

/** Traduce el conjunto ya validado a filas de Room, resolviendo los días relativos contra [clock]. */
class DemoSeedMapper(private val clock: Clock) {
    private val zone = clock.zone
    private val today: LocalDate = LocalDate.now(clock)
    private val nowMillis: Long = clock.millis()

    fun toRecords(seed: DemoSeed): SeedRecords = SeedRecords(
        users = seed.users.map { user ->
            DemoUserEntity(
                userId = user.userId,
                firstName = user.firstName,
                paternalSurname = user.paternalSurname,
                maternalSurname = user.maternalSurname,
                email = user.email,
                roleCode = user.role,
                areaCode = user.area,
                status = user.status,
                createdAt = nowMillis,
                updatedAt = nowMillis,
                professionalLicense = user.professionalLicense,
            )
        },
        credentials = seed.credentials.map { credential ->
            DemoCredentialEntity(
                userId = credential.userId,
                algorithm = credential.algorithm,
                iterations = credential.iterations,
                salt = credential.salt,
                passwordHash = credential.hash,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            )
        },
        patients = seed.patients.map { patient ->
            PatientEntity(
                patientId = patient.patientId,
                patientNumber = patient.patientNumber,
                firstName = patient.firstName,
                paternalSurname = patient.paternalSurname,
                maternalSurname = patient.maternalSurname,
                birthDate = patient.birthDate,
                birthPlace = patient.birthPlace,
                sexCode = patient.sex,
                municipality = patient.municipality,
                populationTypeCode = patient.populationType,
                status = patient.status,
                createdAt = nowMillis,
                createdBy = patient.createdBy,
                updatedAt = nowMillis,
                updatedBy = patient.createdBy,
            )
        },
        contacts = seed.patients.flatMap { patient ->
            patient.contacts.map { contact ->
                PatientContactEntity(
                    contactId = contact.contactId,
                    patientId = patient.patientId,
                    contactType = contact.type,
                    contactValue = contact.value,
                    isPrimary = contact.isPrimary,
                    status = RecordStatus.ACTIVE.name,
                    createdAt = nowMillis,
                    updatedAt = nowMillis,
                )
            }
        },
        assignments = seed.assignments.map { assignment ->
            ProfessionalAssignmentEntity(
                assignmentId = assignment.assignmentId,
                patientId = assignment.patientId,
                areaCode = assignment.area,
                professionalId = assignment.professionalId,
                startAt = at(assignment.startDayOffset, ASSIGNMENT_TIME).toEpochMilli(),
                endAt = assignment.endDayOffset?.let { at(it, ASSIGNMENT_TIME).toEpochMilli() },
                status = assignment.status,
                reason = assignment.reason,
                assignedBy = assignment.assignedBy,
                createdAt = nowMillis,
            )
        },
        appointments = seed.appointments.map { appointment ->
            val start = at(appointment.dayOffset, LocalTime.parse(appointment.startTime))
            AppointmentEntity(
                appointmentId = appointment.appointmentId,
                patientId = appointment.patientId,
                areaCode = appointment.area,
                professionalId = appointment.professionalId,
                startDateTime = start.toEpochMilli(),
                endDateTime = start.plus(Duration.ofMinutes(appointment.durationMinutes.toLong())).toEpochMilli(),
                modality = appointment.modality,
                location = appointment.location,
                meetingUrl = appointment.meetingUrl,
                status = appointment.status,
                administrativeNotes = appointment.administrativeNotes,
                createdBy = appointment.createdBy,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            )
        },
        encounters = seed.encounters.map { encounter ->
            val eventAt = at(encounter.dayOffset, LocalTime.parse(encounter.time))
            val recordedAt = eventAt.plus(RECORDING_DELAY).toEpochMilli()
            ClinicalEncounterEntity(
                encounterId = encounter.encounterId,
                patientId = encounter.patientId,
                areaCode = encounter.area,
                professionalId = encounter.professionalId,
                appointmentId = encounter.appointmentId,
                encounterTypeCode = encounter.type,
                status = encounter.status,
                eventAt = eventAt.toEpochMilli(),
                recordedAt = recordedAt,
                createdBy = encounter.createdBy,
                updatedAt = recordedAt,
                updatedBy = encounter.createdBy,
            )
        },
        assessments = seed.assessments.map { assessment ->
            val startedAt = at(assessment.dayOffset, LocalTime.parse(assessment.time))
            AssessmentEntity(
                assessmentId = assessment.assessmentId,
                patientId = assessment.patientId,
                encounterId = assessment.encounterId,
                professionalId = assessment.professionalId,
                instrumentCode = assessment.instrumentCode,
                instrumentVersion = assessment.instrumentVersion,
                administrationModeCode = assessment.administrationMode,
                startedAt = startedAt.toEpochMilli(),
                completedAt = completionOf(assessment, startedAt)?.toEpochMilli(),
                status = assessment.status,
            )
        },
        results = seed.assessments.mapNotNull { assessment ->
            val result = assessment.result ?: return@mapNotNull null
            val startedAt = at(assessment.dayOffset, LocalTime.parse(assessment.time))
            AssessmentResultEntity(
                assessmentResultId = result.resultId,
                assessmentId = assessment.assessmentId,
                rawScore = result.rawScore,
                normalizedScore = null,
                classificationCode = result.classificationCode,
                classificationLabel = result.classificationLabel,
                scoringVersion = result.scoringVersion,
                calculatedAt = (completionOf(assessment, startedAt) ?: startedAt).toEpochMilli(),
            )
        },
        auditEntries = listOf(
            AuditEntryEntity(
                auditId = UUID.nameUUIDFromBytes("crnl-demo-seed-v${DemoSeed.SUPPORTED_VERSION}".toByteArray()).toString(),
                actorUserId = null,
                action = SEED_AUDIT_ACTION,
                entityType = "DEMO_SEED",
                entityId = "v${DemoSeed.SUPPORTED_VERSION}",
                patientId = null,
                areaCode = null,
                occurredAt = nowMillis,
                result = "SUCCESS",
                metadata = """{"seedVersion":${DemoSeed.SUPPORTED_VERSION},"fictitious":true}""",
            ),
        ),
    )

    private fun at(dayOffset: Int, time: LocalTime): Instant =
        today.plusDays(dayOffset.toLong()).atTime(time).atZone(zone).toInstant()

    private fun completionOf(assessment: SeedAssessment, startedAt: Instant): Instant? =
        if (assessment.status == AssessmentStatus.COMPLETED.name) {
            startedAt.plus(Duration.ofMinutes(assessment.durationMinutes.toLong()))
        } else {
            null
        }

    companion object {
        const val SEED_AUDIT_ACTION = "DEMO_SEED_APPLIED"
        private val ASSIGNMENT_TIME: LocalTime = LocalTime.of(9, 0)
        private val RECORDING_DELAY: Duration = Duration.ofMinutes(30)
    }
}
