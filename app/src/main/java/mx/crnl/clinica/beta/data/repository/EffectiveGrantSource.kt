package mx.crnl.clinica.beta.data.repository

import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import mx.crnl.clinica.beta.data.local.dao.AccessGrantDao
import mx.crnl.clinica.beta.data.local.mapper.toDomain
import mx.crnl.clinica.beta.domain.access.InterareaAccessPolicy
import mx.crnl.clinica.beta.domain.model.AccessGrant
import mx.crnl.clinica.beta.domain.model.AccessGrantStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserAccount

/**
 * Concesiones de lectura vigentes de una persona, reevaluadas cuando cambian en la base (se aprueban, revocan) y en
 * cada instante en que una empieza a regir o vence, de modo que quien lee reacciona sin un programador aparte. Solo
 * dice qué áreas de qué pacientes se pueden leer; nunca amplía una acción de escritura.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EffectiveGrantSource(private val grantDao: AccessGrantDao, private val clock: Clock) {
    fun observe(viewer: UserAccount): Flow<List<AccessGrant>> = grantDao.observeByGrantee(viewer.userId)
        .flatMapLatest { rows -> evaluating(rows.map { it.toDomain() }, viewer) }
        .distinctUntilChanged()

    suspend fun current(viewer: UserAccount): List<AccessGrant> {
        val now = clock.instant()
        return grantDao.listByGrantee(viewer.userId).map { it.toDomain() }.filter { InterareaAccessPolicy.isEffective(it, viewer, now) }
    }

    /** Las áreas de [patientId] legibles ahora por [viewer] gracias a una concesión. */
    suspend fun readableAreas(viewer: UserAccount, patientId: String): Set<ClinicalArea> =
        InterareaAccessPolicy.readableAreas(viewer, current(viewer), patientId, clock.instant()).keys

    private fun evaluating(grants: List<AccessGrant>, viewer: UserAccount): Flow<List<AccessGrant>> = flow {
        while (true) {
            val now = clock.instant()
            emit(grants.filter { InterareaAccessPolicy.isEffective(it, viewer, now) })
            val next = grants
                .filter { it.storedStatus == AccessGrantStatus.ACTIVE }
                .flatMap { listOf(it.validFrom, it.expiresAt) }
                .filter { it.isAfter(now) }
                .minOrNull() ?: return@flow
            delay(Duration.between(now, next).toMillis().coerceAtLeast(1))
            // Si el reloj no avanzó no hay nada nuevo que evaluar.
            if (!clock.instant().isAfter(now)) return@flow
        }
    }
}
