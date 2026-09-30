package mx.crnl.clinica.beta.domain.model

import java.time.Instant

/** Agrupa las acciones auditadas para filtrarlas; cualquier acción que la versión no conozca cae en [SYSTEM]. */
enum class AuditCategory(private val actions: Set<AuditAction>) {
    SESSIONS(setOf(AuditAction.LOGIN, AuditAction.LOGOUT)),
    PATIENTS(setOf(AuditAction.PATIENT_CREATED, AuditAction.PATIENT_VIEWED, AuditAction.PATIENT_UPDATED)),
    CARE(
        setOf(
            AuditAction.ASSIGNMENT_CREATED,
            AuditAction.APPOINTMENT_CREATED,
            AuditAction.APPOINTMENT_UPDATED,
            AuditAction.APPOINTMENT_SCHEDULED,
            AuditAction.APPOINTMENT_CONFIRMED,
            AuditAction.APPOINTMENT_RESCHEDULED,
            AuditAction.APPOINTMENT_COMPLETED,
            AuditAction.APPOINTMENT_NO_SHOW,
            AuditAction.APPOINTMENT_CANCELLED,
            AuditAction.APPOINTMENT_WHATSAPP_OPENED,
            AuditAction.ENCOUNTER_CREATED,
            AuditAction.SUPERVISED_PREVIEW_OPENED,
        ),
    ),
    REQUESTS(
        setOf(
            AuditAction.USER_REQUESTED,
            AuditAction.USER_APPROVED,
            AuditAction.USER_REJECTED,
            AuditAction.USER_SUSPENDED,
            AuditAction.USER_REACTIVATED,
            AuditAction.INTERAREA_REQUEST_CREATED,
            AuditAction.INTERAREA_REQUEST_APPROVED,
            AuditAction.INTERAREA_REQUEST_REJECTED,
            AuditAction.ACCESS_GRANT_REVOKED,
            AuditAction.OVERRIDE_REQUEST_CREATED,
            AuditAction.OVERRIDE_REQUEST_APPROVED,
            AuditAction.OVERRIDE_REQUEST_REJECTED,
        ),
    ),
    SYSTEM(setOf(AuditAction.BETA_DATA_RESET)),
    ;

    fun accepts(actionCode: String): Boolean {
        val known = AuditAction.entries.firstOrNull { it.name == actionCode }
        return if (known == null) this == SYSTEM else known in actions
    }
}

enum class AuditPeriod { ALL, TODAY, LAST_7_DAYS }

/** Filtro de la bitácora; sin categoría muestra todas las acciones. */
data class AuditFilter(val category: AuditCategory? = null, val period: AuditPeriod = AuditPeriod.ALL)

/**
 * Una entrada de la bitácora tal como se consulta: quién, qué acción, sobre qué tipo de entidad y con qué resultado.
 * No lleva identificadores de entidad ni la metadata guardada, para que consultarla no revele datos de pacientes.
 */
data class AuditRecord(
    val auditId: String,
    val occurredAt: Instant,
    val actorName: String?,
    val actionCode: String,
    val entityType: String,
    val area: ClinicalArea?,
    val success: Boolean,
) {
    val action: AuditAction? get() = AuditAction.entries.firstOrNull { it.name == actionCode }
}
