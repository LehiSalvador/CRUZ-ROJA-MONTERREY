package mx.crnl.clinica.beta.data.repository

import androidx.room.withTransaction
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.data.local.mapper.toDomain
import mx.crnl.clinica.beta.data.local.mapper.toManaged
import mx.crnl.clinica.beta.domain.access.AccountAction
import mx.crnl.clinica.beta.domain.access.BetaUserAdministrationPolicy
import mx.crnl.clinica.beta.domain.common.EntityKind
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.AuditAction
import mx.crnl.clinica.beta.domain.model.ManagedAccount
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AccountAdministrationRepository

class LocalAccountAdministrationRepository(
    private val database: ClinicalDatabase,
    private val audit: AuditRecorder,
    private val clock: Clock,
) : AccountAdministrationRepository {
    private val userDao = database.userDao()

    override fun observeAccounts(viewer: UserAccount): Flow<List<ManagedAccount>> = userDao.observeAll()
        .map { users ->
            users.map { it.toManaged() }
                .filter { BetaUserAdministrationPolicy.canSee(viewer, it.user) }
                .sortedWith(
                    compareBy<ManagedAccount> { it.user.status != AccountStatus.PENDING_APPROVAL }
                        .thenByDescending { it.requestedAt }
                        .thenBy { it.user.userId },
                )
        }
        .flowOn(Dispatchers.Default)

    override fun observeAccount(userId: String, viewer: UserAccount): Flow<ManagedAccount?> = userDao.observeById(userId)
        .map { entity -> entity?.toManaged()?.takeIf { BetaUserAdministrationPolicy.canSee(viewer, it.user) } }
        .flowOn(Dispatchers.Default)

    override suspend fun apply(
        targetUserId: String,
        action: AccountAction,
        expectedStatus: AccountStatus,
        actorUserId: String,
    ): OperationResult<ManagedAccount> = database.withTransaction {
        val actor = userDao.getById(actorUserId)?.toDomain()
            ?: return@withTransaction failure(OperationError.NotAuthorized)
        val target = userDao.getById(targetUserId)
            ?: return@withTransaction failure(OperationError.NotFound(EntityKind.USER))

        // Primero se autoriza contra lo que la persona veía; luego se detecta que otra persona ya cambió el estado.
        val seen = target.toDomain().copy(status = expectedStatus)
        if (expectedStatus != action.requiredStatus || !BetaUserAdministrationPolicy.canApply(actor, seen, action)) {
            return@withTransaction failure(OperationError.NotAuthorized)
        }
        val current = AccountStatus.valueOf(target.status)
        if (current != expectedStatus) return@withTransaction failure(OperationError.AccountStatusChanged(current))

        val now = clock.millis()
        val changed = userDao.changeStatus(targetUserId, action.requiredStatus.name, action.resultingStatus.name, now)
        if (changed == 0) {
            val latest = userDao.getById(targetUserId)?.let { AccountStatus.valueOf(it.status) } ?: current
            return@withTransaction failure(OperationError.AccountStatusChanged(latest))
        }
        audit.record(
            action.auditAction(),
            actorUserId = actor.userId,
            entityType = ENTITY_USER,
            entityId = targetUserId,
            areaCode = target.areaCode,
            metadata = mapOf("role" to AuditRecorder.text(target.roleCode)),
        )
        OperationResult.Success(requireNotNull(userDao.getById(targetUserId)) { "La cuenta $targetUserId debería existir" }.toManaged())
    }

    private fun AccountAction.auditAction(): AuditAction = when (this) {
        AccountAction.APPROVE -> AuditAction.USER_APPROVED
        AccountAction.REJECT -> AuditAction.USER_REJECTED
        AccountAction.SUSPEND -> AuditAction.USER_SUSPENDED
        AccountAction.REACTIVATE -> AuditAction.USER_REACTIVATED
    }

    private fun failure(error: OperationError): OperationResult<Nothing> = OperationResult.Failure(error)

    private companion object {
        const val ENTITY_USER = "USER"
    }
}
