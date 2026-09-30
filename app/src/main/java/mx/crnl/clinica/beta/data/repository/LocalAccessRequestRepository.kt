package mx.crnl.clinica.beta.data.repository

import androidx.room.withTransaction
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.data.local.entity.AccessGrantEntity
import mx.crnl.clinica.beta.data.local.entity.AccessRequestEntity
import mx.crnl.clinica.beta.data.local.mapper.personName
import mx.crnl.clinica.beta.data.local.mapper.toDomain
import mx.crnl.clinica.beta.domain.access.BetaClinicalAccessPolicy
import mx.crnl.clinica.beta.domain.access.InterareaAccessPolicy
import mx.crnl.clinica.beta.domain.common.EntityKind
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AccessGrantStatus
import mx.crnl.clinica.beta.domain.model.AccessRequest
import mx.crnl.clinica.beta.domain.model.AccessRequestContext
import mx.crnl.clinica.beta.domain.model.AccessRequestDraft
import mx.crnl.clinica.beta.domain.model.AccessScope
import mx.crnl.clinica.beta.domain.model.AuditAction
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.RequestStatus
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AccessRequestRepository
import mx.crnl.clinica.beta.domain.text.TextNormalizer

class LocalAccessRequestRepository(
    private val database: ClinicalDatabase,
    private val audit: AuditRecorder,
    private val clock: Clock,
    private val idGenerator: () -> String,
) : AccessRequestRepository {
    private val requestDao = database.accessRequestDao()
    private val grantDao = database.accessGrantDao()
    private val patientDao = database.patientDao()
    private val userDao = database.userDao()
    private val assignmentDao = database.assignmentDao()
    private val encounterDao = database.encounterDao()

    override fun observeRequests(viewer: UserAccount): Flow<List<AccessRequest>> = requestDao.observeRows()
        .map { rows ->
            rows.map { it.toDomain() }
                .filter { InterareaAccessPolicy.canSeeRequest(viewer, it.ownerArea, it.requesterId) }
        }
        .flowOn(Dispatchers.Default)

    override fun observeRequest(requestId: String, viewer: UserAccount): Flow<AccessRequest?> = requestDao.observeRow(requestId)
        .map { row ->
            row?.toDomain()?.takeIf { InterareaAccessPolicy.canSeeRequest(viewer, it.ownerArea, it.requesterId) }
        }
        .flowOn(Dispatchers.Default)

    override suspend fun getRequestContext(patientId: String, area: ClinicalArea, viewer: UserAccount): AccessRequestContext? {
        if (!InterareaAccessPolicy.canRequest(viewer, area)) return null
        val patient = patientDao.getById(patientId) ?: return null
        return AccessRequestContext(
            patientId = patientId,
            patientName = personName(patient.firstName, patient.paternalSurname, patient.maternalSurname),
            patientNumber = patient.patientNumber,
            ownerArea = area,
            alreadyPending = requestDao.countPending(patientId, viewer.userId, area.name) > 0,
        )
    }

    override suspend fun create(draft: AccessRequestDraft, actorUserId: String): OperationResult<AccessRequest> =
        database.withTransaction {
            val actor = userDao.getById(actorUserId)?.toDomain()
                ?: return@withTransaction failure(OperationError.NotAuthorized)
            if (draft.ownerArea in BetaClinicalAccessPolicy.viewableAreas(actor)) {
                return@withTransaction failure(OperationError.AreaAlreadyReadable)
            }
            val requesterArea = actor.area
            if (requesterArea == null || !InterareaAccessPolicy.canRequest(actor, draft.ownerArea)) {
                return@withTransaction failure(OperationError.NotAuthorized)
            }
            patientDao.getById(draft.patientId) ?: return@withTransaction failure(OperationError.NotFound(EntityKind.PATIENT))
            if (!InterareaAccessPolicy.isValidReason(draft.reason)) return@withTransaction failure(OperationError.InvalidReason)

            val hasActivity = assignmentDao.countForArea(draft.patientId, draft.ownerArea.name) > 0 ||
                encounterDao.countForArea(draft.patientId, draft.ownerArea.name) > 0
            if (!hasActivity) return@withTransaction failure(OperationError.NoAreaActivity)
            if (requestDao.countPending(draft.patientId, actor.userId, draft.ownerArea.name) > 0) {
                return@withTransaction failure(OperationError.DuplicatePendingRequest)
            }

            val requestId = idGenerator()
            requestDao.insert(
                AccessRequestEntity(
                    accessRequestId = requestId,
                    patientId = draft.patientId,
                    requesterUserId = actor.userId,
                    requesterAreaCode = requesterArea.name,
                    ownerAreaCode = draft.ownerArea.name,
                    reason = TextNormalizer.tidy(draft.reason),
                    requestedScope = AccessScope.READ.name,
                    requestedAt = clock.millis(),
                    status = RequestStatus.PENDING.name,
                    reviewedBy = null,
                    reviewedAt = null,
                    expiresAt = null,
                ),
            )
            // La bitácora registra el hecho, no el motivo: el texto del motivo puede ser sensible.
            audit.record(
                AuditAction.INTERAREA_REQUEST_CREATED,
                actorUserId = actor.userId,
                entityType = ENTITY_REQUEST,
                entityId = requestId,
                patientId = draft.patientId,
                areaCode = draft.ownerArea.name,
                metadata = mapOf("scope" to AuditRecorder.text(AccessScope.READ.name)),
            )
            success(loadRequest(requestId))
        }

    override suspend fun approve(requestId: String, durationDays: Int, actorUserId: String): OperationResult<AccessRequest> =
        database.withTransaction {
            val actor = userDao.getById(actorUserId)?.toDomain()
                ?: return@withTransaction failure(OperationError.NotAuthorized)
            val request = requestDao.getById(requestId)
                ?: return@withTransaction failure(OperationError.NotFound(EntityKind.ACCESS_REQUEST))
            val ownerArea = ClinicalArea.valueOf(request.ownerAreaCode)
            if (!InterareaAccessPolicy.canReview(actor, ownerArea, request.requesterUserId)) {
                return@withTransaction failure(OperationError.NotAuthorized)
            }
            if (!InterareaAccessPolicy.isValidDuration(durationDays)) return@withTransaction failure(OperationError.InvalidGrantDuration)
            val current = RequestStatus.valueOf(request.status)
            if (current != RequestStatus.PENDING) return@withTransaction failure(OperationError.RequestAlreadyResolved(current))

            val now = clock.instant()
            val expiresAt = InterareaAccessPolicy.expiresAt(now, durationDays)
            val changed = requestDao.resolve(requestId, RequestStatus.APPROVED.name, actor.userId, now.toEpochMilli(), expiresAt.toEpochMilli())
            if (changed == 0) return@withTransaction failure(OperationError.RequestAlreadyResolved(latestStatus(requestId)))

            grantDao.insert(
                AccessGrantEntity(
                    accessGrantId = idGenerator(),
                    accessRequestId = requestId,
                    patientId = request.patientId,
                    granteeUserId = request.requesterUserId,
                    ownerAreaCode = request.ownerAreaCode,
                    scopeCode = AccessScope.READ.name,
                    validFrom = now.toEpochMilli(),
                    expiresAt = expiresAt.toEpochMilli(),
                    status = AccessGrantStatus.ACTIVE.name,
                    grantedBy = actor.userId,
                    createdAt = now.toEpochMilli(),
                    revokedBy = null,
                    revokedAt = null,
                ),
            )
            audit.record(
                AuditAction.INTERAREA_REQUEST_APPROVED,
                actorUserId = actor.userId,
                entityType = ENTITY_REQUEST,
                entityId = requestId,
                patientId = request.patientId,
                areaCode = request.ownerAreaCode,
                metadata = mapOf(
                    "scope" to AuditRecorder.text(AccessScope.READ.name),
                    "days" to AuditRecorder.text(durationDays.toString()),
                ),
            )
            success(loadRequest(requestId))
        }

    override suspend fun reject(requestId: String, actorUserId: String): OperationResult<AccessRequest> = database.withTransaction {
        val actor = userDao.getById(actorUserId)?.toDomain()
            ?: return@withTransaction failure(OperationError.NotAuthorized)
        val request = requestDao.getById(requestId)
            ?: return@withTransaction failure(OperationError.NotFound(EntityKind.ACCESS_REQUEST))
        val ownerArea = ClinicalArea.valueOf(request.ownerAreaCode)
        if (!InterareaAccessPolicy.canReview(actor, ownerArea, request.requesterUserId)) {
            return@withTransaction failure(OperationError.NotAuthorized)
        }
        val current = RequestStatus.valueOf(request.status)
        if (current != RequestStatus.PENDING) return@withTransaction failure(OperationError.RequestAlreadyResolved(current))

        val changed = requestDao.resolve(requestId, RequestStatus.REJECTED.name, actor.userId, clock.millis(), null)
        if (changed == 0) return@withTransaction failure(OperationError.RequestAlreadyResolved(latestStatus(requestId)))
        audit.record(
            AuditAction.INTERAREA_REQUEST_REJECTED,
            actorUserId = actor.userId,
            entityType = ENTITY_REQUEST,
            entityId = requestId,
            patientId = request.patientId,
            areaCode = request.ownerAreaCode,
        )
        success(loadRequest(requestId))
    }

    override suspend fun revokeGrant(grantId: String, actorUserId: String): OperationResult<AccessRequest> = database.withTransaction {
        val actor = userDao.getById(actorUserId)?.toDomain()
            ?: return@withTransaction failure(OperationError.NotAuthorized)
        val grant = grantDao.getById(grantId)
            ?: return@withTransaction failure(OperationError.NotFound(EntityKind.ACCESS_GRANT))
        val domain = grant.toDomain()
        if (!InterareaAccessPolicy.canRevoke(actor, domain.ownerArea)) return@withTransaction failure(OperationError.NotAuthorized)
        if (domain.statusAt(clock.instant()) != AccessGrantStatus.ACTIVE) return@withTransaction failure(OperationError.GrantNotActive)

        val changed = grantDao.revoke(grantId, actor.userId, clock.millis())
        if (changed == 0) return@withTransaction failure(OperationError.GrantNotActive)
        audit.record(
            AuditAction.ACCESS_GRANT_REVOKED,
            actorUserId = actor.userId,
            entityType = ENTITY_GRANT,
            entityId = grantId,
            patientId = grant.patientId,
            areaCode = grant.ownerAreaCode,
        )
        success(loadRequest(grant.accessRequestId))
    }

    private suspend fun loadRequest(requestId: String): AccessRequest =
        requireNotNull(requestDao.getRow(requestId)) { "La solicitud $requestId debería existir" }.toDomain()

    private suspend fun latestStatus(requestId: String): RequestStatus =
        requestDao.getById(requestId)?.let { RequestStatus.valueOf(it.status) } ?: RequestStatus.PENDING

    private fun <T> success(value: T): OperationResult<T> = OperationResult.Success(value)

    private fun failure(error: OperationError): OperationResult<Nothing> = OperationResult.Failure(error)

    private companion object {
        const val ENTITY_REQUEST = "ACCESS_REQUEST"
        const val ENTITY_GRANT = "ACCESS_GRANT"
    }
}
