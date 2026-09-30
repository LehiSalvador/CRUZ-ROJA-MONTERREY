package mx.crnl.clinica.beta.domain.repository

import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.domain.model.AuditFilter
import mx.crnl.clinica.beta.domain.model.AuditRecord
import mx.crnl.clinica.beta.domain.model.UserAccount

/** Consulta de la bitácora. Es solo lectura: no hay edición, borrado ni exportación. */
interface AuditRepository {
    /** Las entradas más recientes que cumplen [filter]; vacío si [viewer] no puede consultar la bitácora. */
    fun observeEntries(filter: AuditFilter, viewer: UserAccount): Flow<List<AuditRecord>>
}
