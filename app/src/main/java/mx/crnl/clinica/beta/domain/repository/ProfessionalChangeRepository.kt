package mx.crnl.clinica.beta.domain.repository

import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeContext
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeDraft
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeRequest
import mx.crnl.clinica.beta.domain.model.UserAccount

/**
 * Cambio formal del profesional de un paciente en un área. La asignación vigente nunca se edita: al aprobar se cierra
 * y se abre otra, y el historial (asignaciones, encuentros y citas pasadas) queda intacto.
 */
interface ProfessionalChangeRepository {
    /** Solicitudes que [viewer] puede ver: las propias y las que le toca revisar, la más reciente primero. */
    fun observeRequests(viewer: UserAccount): Flow<List<ProfessionalChangeRequest>>

    /** La solicitud; emite nulo si no existe o si [viewer] no puede verla. */
    fun observeRequest(requestId: String, viewer: UserAccount): Flow<ProfessionalChangeRequest?>

    /** Datos para el formulario; nulo si no hay asignación vigente o si [viewer] no puede pedir el cambio. */
    suspend fun getFormContext(patientId: String, area: ClinicalArea, viewer: UserAccount): ProfessionalChangeContext?

    suspend fun create(draft: ProfessionalChangeDraft, actorUserId: String): OperationResult<ProfessionalChangeRequest>

    /** Aprueba: cierra la asignación vigente, abre la del nuevo profesional y resuelve la solicitud, todo o nada. */
    suspend fun approve(requestId: String, actorUserId: String): OperationResult<ProfessionalChangeRequest>

    /** Rechaza una solicitud pendiente sin tocar la asignación. */
    suspend fun reject(requestId: String, resolutionReason: String?, actorUserId: String): OperationResult<ProfessionalChangeRequest>
}
