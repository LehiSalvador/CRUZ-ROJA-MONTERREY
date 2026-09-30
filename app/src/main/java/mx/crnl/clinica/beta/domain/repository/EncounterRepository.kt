package mx.crnl.clinica.beta.domain.repository

import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.EncounterDetail
import mx.crnl.clinica.beta.domain.model.EncounterDraft
import mx.crnl.clinica.beta.domain.model.EncounterFormContext
import mx.crnl.clinica.beta.domain.model.EncounterSummary
import mx.crnl.clinica.beta.domain.model.UserAccount

/**
 * Encuentros clínicos base. Solo registran que hubo atención (quién, cuándo, de qué tipo y, si aplica, de qué cita);
 * no hay contenido clínico ni edición posterior. Un encuentro nunca se borra.
 */
interface EncounterRepository {
    /** Encuentros del paciente en el área, del más reciente al más antiguo; vacío si [viewer] no ve el área. */
    fun observeAreaEncounters(patientId: String, area: ClinicalArea, viewer: UserAccount): Flow<List<EncounterSummary>>

    /** El encuentro; emite nulo si no existe o si [viewer] no ve su área. */
    fun observeEncounter(encounterId: String, viewer: UserAccount): Flow<EncounterDetail?>

    /** Datos para el formulario; nulo si el paciente no existe o [viewer] no ve el área. */
    suspend fun getFormContext(patientId: String, area: ClinicalArea, viewer: UserAccount): EncounterFormContext?

    /** Guarda el encuentro con el momento de captura actual. Rechaza duplicados idénticos (doble toque). */
    suspend fun createEncounter(draft: EncounterDraft, actorUserId: String): OperationResult<EncounterDetail>
}
