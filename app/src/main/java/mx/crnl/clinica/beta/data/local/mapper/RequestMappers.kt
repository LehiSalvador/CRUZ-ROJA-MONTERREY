package mx.crnl.clinica.beta.data.local.mapper

import java.time.Instant
import mx.crnl.clinica.beta.data.local.dao.AccessRequestRow
import mx.crnl.clinica.beta.data.local.dao.AssessmentRow
import mx.crnl.clinica.beta.data.local.dao.AuditRow
import mx.crnl.clinica.beta.data.local.dao.OverrideRequestRow
import mx.crnl.clinica.beta.data.local.entity.AccessGrantEntity
import mx.crnl.clinica.beta.data.local.entity.DemoUserEntity
import mx.crnl.clinica.beta.domain.model.AccessGrant
import mx.crnl.clinica.beta.domain.model.AccessGrantStatus
import mx.crnl.clinica.beta.domain.model.AccessRequest
import mx.crnl.clinica.beta.domain.model.AccessScope
import mx.crnl.clinica.beta.domain.model.AdministrationMode
import mx.crnl.clinica.beta.domain.model.AssessmentDetail
import mx.crnl.clinica.beta.domain.model.AssessmentInstrument
import mx.crnl.clinica.beta.domain.model.AssessmentStatus
import mx.crnl.clinica.beta.domain.model.AssessmentSummary
import mx.crnl.clinica.beta.domain.model.AuditRecord
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ManagedAccount
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeRequest
import mx.crnl.clinica.beta.domain.model.RequestStatus

fun AccessGrantEntity.toDomain(): AccessGrant = AccessGrant(
    grantId = accessGrantId,
    requestId = accessRequestId,
    patientId = patientId,
    granteeUserId = granteeUserId,
    ownerArea = ClinicalArea.valueOf(ownerAreaCode),
    scope = AccessScope.valueOf(scopeCode),
    validFrom = Instant.ofEpochMilli(validFrom),
    expiresAt = Instant.ofEpochMilli(expiresAt),
    storedStatus = AccessGrantStatus.valueOf(status),
    revokedAt = revokedAt?.let(Instant::ofEpochMilli),
)

fun AccessRequestRow.toDomain(): AccessRequest = AccessRequest(
    requestId = accessRequestId,
    patientId = patientId,
    patientName = personName(patientFirstName, patientPaternalSurname, patientMaternalSurname),
    patientNumber = patientNumber,
    requesterId = requesterUserId,
    requesterName = personName(requesterFirstName, requesterPaternalSurname, requesterMaternalSurname),
    requesterArea = ClinicalArea.entries.firstOrNull { it.name == requesterAreaCode },
    ownerArea = ClinicalArea.valueOf(ownerAreaCode),
    reason = reason,
    scope = AccessScope.valueOf(requestedScope),
    requestedAt = Instant.ofEpochMilli(requestedAt),
    status = RequestStatus.valueOf(status),
    reviewerName = reviewerFirstName?.let { personName(it, requireNotNull(reviewerPaternalSurname), reviewerMaternalSurname) },
    reviewedAt = reviewedAt?.let(Instant::ofEpochMilli),
    grant = grantId?.let { id ->
        AccessGrant(
            grantId = id,
            requestId = accessRequestId,
            patientId = patientId,
            granteeUserId = requesterUserId,
            ownerArea = ClinicalArea.valueOf(ownerAreaCode),
            scope = AccessScope.valueOf(requestedScope),
            validFrom = Instant.ofEpochMilli(requireNotNull(grantValidFrom)),
            expiresAt = Instant.ofEpochMilli(requireNotNull(grantExpiresAt)),
            storedStatus = AccessGrantStatus.valueOf(requireNotNull(grantStatus)),
            revokedAt = grantRevokedAt?.let(Instant::ofEpochMilli),
        )
    },
)

fun OverrideRequestRow.toDomain(upcomingAppointmentCount: Int = 0): ProfessionalChangeRequest = ProfessionalChangeRequest(
    requestId = overrideRequestId,
    patientId = patientId,
    patientName = personName(patientFirstName, patientPaternalSurname, patientMaternalSurname),
    patientNumber = patientNumber,
    area = ClinicalArea.valueOf(areaCode),
    currentAssignmentId = currentAssignmentId,
    currentProfessionalId = currentProfessionalId,
    currentProfessionalName = personName(currentFirstName, currentPaternalSurname, null),
    requestedProfessionalId = requestedProfessionalId,
    requestedProfessionalName = personName(requestedFirstName, requestedPaternalSurname, null),
    requesterId = requestedBy,
    requesterName = personName(requesterFirstName, requesterPaternalSurname, requesterMaternalSurname),
    reason = reason,
    requestedAt = Instant.ofEpochMilli(requestedAt),
    status = RequestStatus.valueOf(status),
    reviewerName = reviewerFirstName?.let { personName(it, requireNotNull(reviewerPaternalSurname), reviewerMaternalSurname) },
    reviewedAt = reviewedAt?.let(Instant::ofEpochMilli),
    resolutionReason = resolutionReason,
    upcomingAppointmentCount = upcomingAppointmentCount,
)

fun DemoUserEntity.toManaged(): ManagedAccount = ManagedAccount(user = toDomain(), requestedAt = Instant.ofEpochMilli(createdAt))

fun AssessmentRow.toSummary(): AssessmentSummary = AssessmentSummary(
    assessmentId = assessmentId,
    area = areaCode?.let(ClinicalArea::valueOf),
    professionalName = personName(professionalFirstName, professionalPaternalSurname, null),
    status = AssessmentStatus.valueOf(status),
    startedAt = Instant.ofEpochMilli(startedAt),
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    instrument = AssessmentInstrument.fromCode(instrumentCode),
    instrumentVersion = instrumentVersion,
    mode = AdministrationMode.valueOf(administrationModeCode),
    hasResult = resultId != null,
    rawScore = rawScore,
)

fun AssessmentRow.toDetail(canOpenSupervisedPreview: Boolean): AssessmentDetail = AssessmentDetail(
    summary = toSummary(),
    patientId = patientId,
    patientName = personName(patientFirstName, patientPaternalSurname, patientMaternalSurname),
    patientNumber = patientNumber,
    encounterId = encounterId,
    scoringVersion = scoringVersion,
    canOpenSupervisedPreview = canOpenSupervisedPreview,
)

fun AuditRow.toDomain(): AuditRecord = AuditRecord(
    auditId = auditId,
    occurredAt = Instant.ofEpochMilli(occurredAt),
    actorName = actorFirstName?.let { personName(it, requireNotNull(actorPaternalSurname), actorMaternalSurname) },
    actionCode = actionCode,
    entityType = entityType,
    area = areaCode?.let { code -> ClinicalArea.entries.firstOrNull { it.name == code } },
    success = result == "SUCCESS",
)
