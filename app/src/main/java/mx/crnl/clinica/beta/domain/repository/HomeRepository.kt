package mx.crnl.clinica.beta.domain.repository

import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.domain.home.HomeSummary
import mx.crnl.clinica.beta.domain.model.UserAccount

interface HomeRepository {
    /** Indicadores de Inicio derivados de los datos vigentes; se recalculan cuando estos cambian. */
    fun observeSummary(user: UserAccount): Flow<HomeSummary>
}
