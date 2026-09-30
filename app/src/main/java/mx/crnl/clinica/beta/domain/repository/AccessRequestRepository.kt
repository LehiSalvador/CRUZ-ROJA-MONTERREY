package mx.crnl.clinica.beta.domain.repository

import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AccessRequest
import mx.crnl.clinica.beta.domain.model.AccessRequestContext
import mx.crnl.clinica.beta.domain.model.AccessRequestDraft
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserAccount

/**
 * Solicitudes de lectura interárea y las concesiones que producen. Aprobar crea una concesión de solo lectura con
 * vigencia; ni la solicitud ni la concesión cambian asignaciones ni habilitan ninguna escritura.
 */
interface AccessRequestRepository {
    /** Solicitudes que [viewer] puede ver: las propias y las que le toca revisar, la más reciente primero. */
    fun observeRequests(viewer: UserAccount): Flow<List<AccessRequest>>

    /** La solicitud; emite nulo si no existe o si [viewer] no puede verla. */
    fun observeRequest(requestId: String, viewer: UserAccount): Flow<AccessRequest?>

    /** Datos para el formulario; nulo si el paciente no existe o si [viewer] no puede pedir acceso a esa área. */
    suspend fun getRequestContext(patientId: String, area: ClinicalArea, viewer: UserAccount): AccessRequestContext?

    /** Registra la solicitud como pendiente. No la resuelve ni concede nada. */
    suspend fun create(draft: AccessRequestDraft, actorUserId: String): OperationResult<AccessRequest>

    /** Aprueba una solicitud pendiente y crea la concesión de lectura por [durationDays] días, en una sola transacción. */
    suspend fun approve(requestId: String, durationDays: Int, actorUserId: String): OperationResult<AccessRequest>

    /** Rechaza una solicitud pendiente; no crea concesión. */
    suspend fun reject(requestId: String, actorUserId: String): OperationResult<AccessRequest>

    /** Revoca una concesión activa; no se borra. Devuelve la solicitud ya actualizada. */
    suspend fun revokeGrant(grantId: String, actorUserId: String): OperationResult<AccessRequest>
}
