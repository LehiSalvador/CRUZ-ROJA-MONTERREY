package mx.crnl.clinica.beta.data.repository

import androidx.room.withTransaction
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.core.demo.LocalDataInitializer
import mx.crnl.clinica.beta.data.local.mapper.toDomain
import mx.crnl.clinica.beta.domain.access.BetaAdminAccessPolicy
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AuditAction
import mx.crnl.clinica.beta.domain.repository.BetaMaintenanceRepository
import mx.crnl.clinica.beta.domain.repository.BetaResetSummary
import mx.crnl.clinica.beta.domain.repository.SessionRepository

/**
 * Restablece la base local de la Beta: en una sola transacción vacía las tablas de la propia base y vuelve a cargar el
 * conjunto ficticio, de modo que nadie observa la base a medias; después termina la sesión. Si la recarga fallara, la
 * transacción se deshace y los datos quedan como estaban.
 */
class LocalBetaMaintenanceRepository(
    private val database: ClinicalDatabase,
    private val sessionRepository: SessionRepository,
    private val initializer: LocalDataInitializer,
    private val audit: AuditRecorder,
) : BetaMaintenanceRepository {
    private val userDao = database.userDao()
    private val maintenanceDao = database.maintenanceDao()

    override suspend fun resetBetaData(actorUserId: String): OperationResult<BetaResetSummary> {
        val actor = userDao.getById(actorUserId)?.toDomain()
        if (actor == null || !BetaAdminAccessPolicy.canResetBetaData(actor)) {
            return OperationResult.Failure(OperationError.NotAuthorized)
        }

        // El cargador solo aplica el conjunto si la versión aplicada es anterior: se olvida la versión mientras dura la recarga.
        val previousVersion = sessionRepository.appliedSeedVersion()
        sessionRepository.recordAppliedSeedVersion(0)
        try {
            database.withTransaction {
                maintenanceDao.deleteAllRows()
                initializer.initialize()
            }
        } catch (error: Throwable) {
            sessionRepository.recordAppliedSeedVersion(previousVersion)
            throw error
        }
        sessionRepository.endSession()

        // La cuenta que restableció existe de nuevo solo si pertenece al conjunto inicial; si no, no se atribuye a nadie.
        audit.record(
            AuditAction.BETA_DATA_RESET,
            actorUserId = actor.userId.takeIf { userDao.getById(it) != null },
            entityType = ENTITY_BETA_DATA,
            entityId = null,
        )
        return OperationResult.Success(
            BetaResetSummary(
                users = count("demo_users"),
                patients = count("patients"),
                appointments = count("appointments"),
                assessments = count("assessments"),
            ),
        )
    }

    private fun count(table: String): Int = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

    private companion object {
        const val ENTITY_BETA_DATA = "BETA_DATA"
    }
}
