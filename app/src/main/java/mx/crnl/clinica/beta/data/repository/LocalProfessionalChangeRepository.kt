package mx.crnl.clinica.beta.data.repository

import androidx.room.withTransaction
import java.text.Collator
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.core.util.ClinicTime
import mx.crnl.clinica.beta.data.local.dao.AppointmentListRow
import mx.crnl.clinica.beta.data.local.dao.OverrideRequestRow
import mx.crnl.clinica.beta.data.local.entity.DemoUserEntity
import mx.crnl.clinica.beta.data.local.entity.ProfessionalAssignmentEntity
import mx.crnl.clinica.beta.data.local.entity.ProfessionalOverrideRequestEntity
import mx.crnl.clinica.beta.data.local.mapper.personName
import mx.crnl.clinica.beta.data.local.mapper.toDomain
import mx.crnl.clinica.beta.data.local.mapper.toProfessionalOption
import mx.crnl.clinica.beta.domain.access.ProfessionalChangePolicy
import mx.crnl.clinica.beta.domain.common.EntityKind
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AssignmentStatus
import mx.crnl.clinica.beta.domain.model.AuditAction
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.OPEN_APPOINTMENT_STATUSES
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeContext
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeDraft
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeRequest
import mx.crnl.clinica.beta.domain.model.RequestStatus
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.ProfessionalChangeRepository
import mx.crnl.clinica.beta.domain.text.TextNormalizer

class LocalProfessionalChangeRepository(
    private val database: ClinicalDatabase,
    private val audit: AuditRecorder,
    private val clock: Clock,
    private val idGenerator: () -> String,
) : ProfessionalChangeRepository {
    private val overrideDao = database.overrideRequestDao()
    private val assignmentDao = database.assignmentDao()
    private val appointmentDao = database.appointmentDao()
    private val patientDao = database.patientDao()
    private val userDao = database.userDao()

    /** Aborta la transacción en curso deshaciendo lo escrito y devuelve [error] como resultado. */
    private class Abort(val error: OperationError) : RuntimeException(null, null, false, false)

    override fun observeRequests(viewer: UserAccount): Flow<List<ProfessionalChangeRequest>> = overrideDao.observeRows()
        .map { rows ->
            rows.map { it.toDomain() }
                .filter { ProfessionalChangePolicy.canSee(viewer, it.area, it.requesterId) }
        }
        .flowOn(Dispatchers.Default)

    override fun observeRequest(requestId: String, viewer: UserAccount): Flow<ProfessionalChangeRequest?> =
        combine(overrideDao.observeRow(requestId), appointmentDao.observeRows()) { row, appointments ->
            row
                ?.takeIf { ProfessionalChangePolicy.canSee(viewer, ClinicalArea.valueOf(it.areaCode), it.requestedBy) }
                ?.let { it.toDomain(upcomingCount(it, appointments)) }
        }.flowOn(Dispatchers.Default)

    override suspend fun getFormContext(patientId: String, area: ClinicalArea, viewer: UserAccount): ProfessionalChangeContext? {
        val assignment = assignmentDao.getActiveRow(patientId, area.name)?.toDomain() ?: return null
        if (!ProfessionalChangePolicy.canRequest(viewer, area, assignment.professionalId)) return null
        val patient = patientDao.getById(patientId) ?: return null
        val candidates = userDao.listActiveProfessionals(area.name)
            .filter { it.userId != assignment.professionalId }
            .sortedWith(byName)
            .map { it.toProfessionalOption() }
        return ProfessionalChangeContext(
            patientId = patientId,
            patientName = personName(patient.firstName, patient.paternalSurname, patient.maternalSurname),
            patientNumber = patient.patientNumber,
            area = area,
            currentAssignment = assignment,
            candidates = candidates,
            upcomingAppointmentCount = appointmentDao.countOpenUpcoming(patientId, area.name, assignment.professionalId, clock.millis()),
            pendingRequestId = overrideDao.observePendingRow(patientId, area.name).first()?.overrideRequestId,
        )
    }

    override suspend fun create(draft: ProfessionalChangeDraft, actorUserId: String): OperationResult<ProfessionalChangeRequest> =
        database.withTransaction {
            val actor = userDao.getById(actorUserId)?.toDomain()
                ?: return@withTransaction failure(OperationError.NotAuthorized)
            val patient = patientDao.getById(draft.patientId)
                ?: return@withTransaction failure(OperationError.NotFound(EntityKind.PATIENT))
            if (patient.status != PatientStatus.ACTIVE.name) return@withTransaction failure(OperationError.PatientNotActive)

            val assignment = assignmentDao.getActiveRow(draft.patientId, draft.area.name)
                ?: return@withTransaction failure(OperationError.NoActiveAssignment)
            if (!ProfessionalChangePolicy.canRequest(actor, draft.area, assignment.professionalId)) {
                return@withTransaction failure(OperationError.NotAuthorized)
            }
            if (!ProfessionalChangePolicy.isValidReason(draft.reason)) return@withTransaction failure(OperationError.InvalidReason)
            if (draft.requestedProfessionalId == assignment.professionalId) return@withTransaction failure(OperationError.SameProfessional)

            val candidate = userDao.getById(draft.requestedProfessionalId)
                ?: return@withTransaction failure(OperationError.NotFound(EntityKind.PROFESSIONAL))
            if (!ProfessionalChangePolicy.isValidCandidate(candidate.toDomain(), draft.area, assignment.professionalId)) {
                return@withTransaction failure(OperationError.ProfessionalNotAvailable)
            }
            if (overrideDao.countPending(draft.patientId, draft.area.name) > 0) {
                return@withTransaction failure(OperationError.DuplicatePendingRequest)
            }

            val requestId = idGenerator()
            overrideDao.insert(
                ProfessionalOverrideRequestEntity(
                    overrideRequestId = requestId,
                    patientId = draft.patientId,
                    areaCode = draft.area.name,
                    currentAssignmentId = assignment.assignmentId,
                    currentProfessionalId = assignment.professionalId,
                    requestedProfessionalId = draft.requestedProfessionalId,
                    requestedBy = actor.userId,
                    reason = TextNormalizer.tidy(draft.reason),
                    requestedAt = clock.millis(),
                    status = RequestStatus.PENDING.name,
                    reviewedBy = null,
                    reviewedAt = null,
                    resolutionReason = null,
                ),
            )
            audit.record(
                AuditAction.OVERRIDE_REQUEST_CREATED,
                actorUserId = actor.userId,
                entityType = ENTITY_REQUEST,
                entityId = requestId,
                patientId = draft.patientId,
                areaCode = draft.area.name,
                metadata = mapOf(
                    "currentProfessionalId" to AuditRecorder.text(assignment.professionalId),
                    "requestedProfessionalId" to AuditRecorder.text(draft.requestedProfessionalId),
                ),
            )
            success(loadRequest(requestId))
        }

    override suspend fun approve(requestId: String, actorUserId: String): OperationResult<ProfessionalChangeRequest> = try {
        database.withTransaction {
            val actor = userDao.getById(actorUserId)?.toDomain()
                ?: return@withTransaction failure(OperationError.NotAuthorized)
            val request = overrideDao.getById(requestId)
                ?: return@withTransaction failure(OperationError.NotFound(EntityKind.CHANGE_REQUEST))
            val area = ClinicalArea.valueOf(request.areaCode)
            if (!ProfessionalChangePolicy.canReview(actor, area, request.requestedBy)) {
                return@withTransaction failure(OperationError.NotAuthorized)
            }
            val current = RequestStatus.valueOf(request.status)
            if (current != RequestStatus.PENDING) return@withTransaction failure(OperationError.RequestAlreadyResolved(current))

            val patient = patientDao.getById(request.patientId)
                ?: return@withTransaction failure(OperationError.NotFound(EntityKind.PATIENT))
            if (patient.status != PatientStatus.ACTIVE.name) return@withTransaction failure(OperationError.PatientNotActive)

            // La vigente debe seguir siendo la que se veía al pedir el cambio: si cambió, la solicitud quedó vieja.
            val active = assignmentDao.getActiveRow(request.patientId, request.areaCode)
            if (active == null || active.assignmentId != request.currentAssignmentId) {
                return@withTransaction failure(OperationError.AssignmentChanged)
            }
            val candidate = userDao.getById(request.requestedProfessionalId)
                ?: return@withTransaction failure(OperationError.NotFound(EntityKind.PROFESSIONAL))
            if (!ProfessionalChangePolicy.isValidCandidate(candidate.toDomain(), area, request.currentProfessionalId)) {
                return@withTransaction failure(OperationError.ProfessionalNotAvailable)
            }

            val now = clock.millis()
            if (overrideDao.resolve(requestId, RequestStatus.APPROVED.name, actor.userId, now, null) == 0) {
                throw Abort(OperationError.RequestAlreadyResolved(latestStatus(requestId)))
            }
            // Se cierra la asignación anterior y se abre otra: la anterior conserva a su profesional y sus fechas.
            if (assignmentDao.end(request.currentAssignmentId, now) == 0) throw Abort(OperationError.AssignmentChanged)
            val newAssignmentId = idGenerator()
            assignmentDao.insert(
                ProfessionalAssignmentEntity(
                    assignmentId = newAssignmentId,
                    patientId = request.patientId,
                    areaCode = request.areaCode,
                    professionalId = request.requestedProfessionalId,
                    startAt = now,
                    endAt = null,
                    status = AssignmentStatus.ACTIVE.name,
                    reason = (CHANGE_REASON_PREFIX + request.reason).take(MAX_ASSIGNMENT_REASON),
                    assignedBy = actor.userId,
                    createdAt = now,
                ),
            )
            val remaining = appointmentDao.countOpenUpcoming(request.patientId, request.areaCode, request.currentProfessionalId, now)
            audit.record(
                AuditAction.OVERRIDE_REQUEST_APPROVED,
                actorUserId = actor.userId,
                entityType = ENTITY_REQUEST,
                entityId = requestId,
                patientId = request.patientId,
                areaCode = request.areaCode,
                metadata = mapOf(
                    "previousAssignmentId" to AuditRecorder.text(request.currentAssignmentId),
                    "newAssignmentId" to AuditRecorder.text(newAssignmentId),
                    "previousProfessionalId" to AuditRecorder.text(request.currentProfessionalId),
                    "newProfessionalId" to AuditRecorder.text(request.requestedProfessionalId),
                    "openAppointmentsWithPrevious" to AuditRecorder.text(remaining.toString()),
                ),
            )
            success(loadRequest(requestId))
        }
    } catch (abort: Abort) {
        failure(abort.error)
    }

    override suspend fun reject(requestId: String, resolutionReason: String?, actorUserId: String): OperationResult<ProfessionalChangeRequest> =
        database.withTransaction {
            val actor = userDao.getById(actorUserId)?.toDomain()
                ?: return@withTransaction failure(OperationError.NotAuthorized)
            val request = overrideDao.getById(requestId)
                ?: return@withTransaction failure(OperationError.NotFound(EntityKind.CHANGE_REQUEST))
            val area = ClinicalArea.valueOf(request.areaCode)
            if (!ProfessionalChangePolicy.canReview(actor, area, request.requestedBy)) {
                return@withTransaction failure(OperationError.NotAuthorized)
            }
            val current = RequestStatus.valueOf(request.status)
            if (current != RequestStatus.PENDING) return@withTransaction failure(OperationError.RequestAlreadyResolved(current))

            val cleanReason = TextNormalizer.tidy(resolutionReason.orEmpty()).take(ProfessionalChangePolicy.MAX_REASON_LENGTH).ifEmpty { null }
            if (overrideDao.resolve(requestId, RequestStatus.REJECTED.name, actor.userId, clock.millis(), cleanReason) == 0) {
                return@withTransaction failure(OperationError.RequestAlreadyResolved(latestStatus(requestId)))
            }
            audit.record(
                AuditAction.OVERRIDE_REQUEST_REJECTED,
                actorUserId = actor.userId,
                entityType = ENTITY_REQUEST,
                entityId = requestId,
                patientId = request.patientId,
                areaCode = request.areaCode,
                metadata = mapOf("reasonGiven" to AuditRecorder.flag(cleanReason != null)),
            )
            success(loadRequest(requestId))
        }

    private suspend fun loadRequest(requestId: String): ProfessionalChangeRequest {
        val row = requireNotNull(overrideDao.getRow(requestId)) { "La solicitud $requestId debería existir" }
        val upcoming = if (row.status == RequestStatus.PENDING.name) {
            appointmentDao.countOpenUpcoming(row.patientId, row.areaCode, row.currentProfessionalId, clock.millis())
        } else {
            0
        }
        return row.toDomain(upcoming)
    }

    private suspend fun latestStatus(requestId: String): RequestStatus =
        overrideDao.getById(requestId)?.let { RequestStatus.valueOf(it.status) } ?: RequestStatus.PENDING

    private fun upcomingCount(row: OverrideRequestRow, appointments: List<AppointmentListRow>): Int {
        if (row.status != RequestStatus.PENDING.name) return 0
        val now = clock.instant()
        return appointments.count {
            it.patientId == row.patientId && it.areaCode == row.areaCode && it.professionalId == row.currentProfessionalId &&
                it.status in OPEN_STATUS_NAMES && !Instant.ofEpochMilli(it.endDateTime).isBefore(now)
        }
    }

    private fun <T> success(value: T): OperationResult<T> = OperationResult.Success(value)

    private fun failure(error: OperationError): OperationResult<Nothing> = OperationResult.Failure(error)

    private companion object {
        const val ENTITY_REQUEST = "OVERRIDE_REQUEST"
        const val CHANGE_REASON_PREFIX = "Cambio de profesional: "
        const val MAX_ASSIGNMENT_REASON = 200
        val OPEN_STATUS_NAMES: Set<String> = OPEN_APPOINTMENT_STATUSES.mapTo(mutableSetOf()) { it.name }

        private val collator: Collator = Collator.getInstance(ClinicTime.locale).apply { strength = Collator.PRIMARY }

        val byName: Comparator<DemoUserEntity> = Comparator { first, second ->
            compareValuesBy(
                first,
                second,
                { collator.getCollationKey(it.paternalSurname) },
                { collator.getCollationKey(it.maternalSurname.orEmpty()) },
                { collator.getCollationKey(it.firstName) },
            )
        }
    }
}
