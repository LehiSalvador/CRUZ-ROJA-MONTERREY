package mx.crnl.clinica.beta.core.demo

import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.crnl.clinica.beta.data.local.dao.SeedCounts
import mx.crnl.clinica.beta.data.local.dao.SeedDao
import mx.crnl.clinica.beta.data.local.dao.SeedRecords
import mx.crnl.clinica.beta.domain.repository.SessionRepository

sealed interface SeedOutcome {
    data object AlreadyApplied : SeedOutcome

    data class Applied(val counts: SeedCounts) : SeedOutcome
}

fun interface LocalDataInitializer {
    suspend fun initialize(): SeedOutcome
}

/**
 * Carga los datos ficticios una sola vez por instalación. Repetirla es inofensiva: la versión aplicada
 * evita releer los JSON y, aun sin ella, las llaves determinísticas con IGNORE impiden duplicar filas.
 */
class DemoDataInitializer(
    private val seedDao: SeedDao,
    private val sessionRepository: SessionRepository,
    private val loader: DemoSeedLoader,
    private val clock: Clock,
) : LocalDataInitializer {

    override suspend fun initialize(): SeedOutcome {
        if (sessionRepository.appliedSeedVersion() >= DemoSeed.SUPPORTED_VERSION) return SeedOutcome.AlreadyApplied
        val records = withContext(Dispatchers.IO) { prepareRecords() }
        val counts = seedDao.insertAll(records)
        sessionRepository.recordAppliedSeedVersion(DemoSeed.SUPPORTED_VERSION)
        return SeedOutcome.Applied(counts)
    }

    private fun prepareRecords(): SeedRecords {
        val seed = loader.load()
        val issues = DemoSeedValidator(today = LocalDate.now(clock)).validate(seed)
        if (issues.isNotEmpty()) {
            throw SeedException("Conjunto de datos inválido:\n" + issues.joinToString("\n") { "- $it" })
        }
        return DemoSeedMapper(clock).toRecords(seed)
    }
}
