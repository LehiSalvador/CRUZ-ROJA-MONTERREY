package mx.crnl.clinica.beta.data.repository

import java.time.Clock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mx.crnl.clinica.beta.data.local.dao.AuditDao
import mx.crnl.clinica.beta.data.local.entity.AuditEntryEntity
import mx.crnl.clinica.beta.domain.model.AuditAction

/**
 * Escribe entradas en la bitácora. Se invoca dentro de la misma transacción que la operación auditada.
 * La metadata es mínima a propósito: nunca contiene contraseñas, valores clínicos ni datos personales.
 */
class AuditRecorder(
    private val auditDao: AuditDao,
    private val clock: Clock,
    private val idGenerator: () -> String,
) {
    suspend fun record(
        action: AuditAction,
        actorUserId: String?,
        entityType: String,
        entityId: String?,
        patientId: String? = null,
        metadata: Map<String, JsonElement>? = null,
        areaCode: String? = null,
    ) {
        auditDao.insert(
            AuditEntryEntity(
                auditId = idGenerator(),
                actorUserId = actorUserId,
                action = action.name,
                entityType = entityType,
                entityId = entityId,
                patientId = patientId,
                areaCode = areaCode,
                occurredAt = clock.millis(),
                result = RESULT_SUCCESS,
                metadata = metadata?.let { JsonObject(it).toString() },
            ),
        )
    }

    companion object {
        const val RESULT_SUCCESS = "SUCCESS"

        fun text(value: String): JsonElement = JsonPrimitive(value)

        fun flag(value: Boolean): JsonElement = JsonPrimitive(value)

        fun list(values: List<String>): JsonElement = JsonArray(values.map(::JsonPrimitive))
    }
}
