package mx.crnl.clinica.beta.data.repository

import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.core.util.ClinicTime
import mx.crnl.clinica.beta.data.local.mapper.toDomain
import mx.crnl.clinica.beta.domain.access.BetaAdminAccessPolicy
import mx.crnl.clinica.beta.domain.model.AuditFilter
import mx.crnl.clinica.beta.domain.model.AuditPeriod
import mx.crnl.clinica.beta.domain.model.AuditRecord
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AuditRepository

class LocalAuditRepository(database: ClinicalDatabase, private val clock: Clock) : AuditRepository {
    private val auditDao = database.auditDao()

    override fun observeEntries(filter: AuditFilter, viewer: UserAccount): Flow<List<AuditRecord>> {
        if (!BetaAdminAccessPolicy.canViewAudit(viewer)) return flowOf(emptyList())
        return auditDao.observeRows(since(filter.period), MAX_ENTRIES)
            .map { rows ->
                rows.map { it.toDomain() }
                    .filter { record -> filter.category?.accepts(record.actionCode) ?: true }
            }
            .flowOn(Dispatchers.Default)
    }

    private fun since(period: AuditPeriod): Long = when (period) {
        AuditPeriod.ALL -> 0L
        AuditPeriod.TODAY -> clock.instant().atZone(ClinicTime.zone).toLocalDate().atStartOfDay(ClinicTime.zone).toInstant().toEpochMilli()
        AuditPeriod.LAST_7_DAYS -> clock.instant().minus(Duration.ofDays(7)).toEpochMilli()
    }

    companion object {
        /** La consulta muestra lo más reciente; la bitácora completa sigue guardada. */
        const val MAX_ENTRIES = 300
    }
}
