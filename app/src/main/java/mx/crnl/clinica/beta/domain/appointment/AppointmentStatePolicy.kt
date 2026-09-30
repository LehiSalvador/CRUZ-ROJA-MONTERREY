package mx.crnl.clinica.beta.domain.appointment

import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AppointmentStatus.CANCELLED
import mx.crnl.clinica.beta.domain.model.AppointmentStatus.COMPLETED
import mx.crnl.clinica.beta.domain.model.AppointmentStatus.CONFIRMED
import mx.crnl.clinica.beta.domain.model.AppointmentStatus.NO_SHOW
import mx.crnl.clinica.beta.domain.model.AppointmentStatus.PENDING
import mx.crnl.clinica.beta.domain.model.AppointmentStatus.RESCHEDULED
import mx.crnl.clinica.beta.domain.model.AppointmentStatus.SCHEDULED

/** Operaciones explícitas que una persona puede pedir sobre una cita; cada una lleva a un único estado destino. */
enum class AppointmentAction { SCHEDULE, CONFIRM, RESCHEDULE, COMPLETE, MARK_NO_SHOW, CANCEL }

/**
 * Máquina de estados de la cita en la Beta. Es una regla operativa y reversible, no la matriz institucional final:
 * cambiarla no debe tocar ninguna pantalla. Los estados realizada, no asistió y cancelada son terminales.
 *
 * Desviación de la matriz recomendada: una cita ya reprogramada puede reprogramarse de nuevo; sin ello, la primera
 * reprogramación dejaría la cita sin manera de volver a moverse.
 */
object AppointmentStatePolicy {
    private val transitions: Map<AppointmentStatus, Set<AppointmentStatus>> = mapOf(
        PENDING to setOf(SCHEDULED, CONFIRMED, CANCELLED),
        SCHEDULED to setOf(CONFIRMED, RESCHEDULED, COMPLETED, NO_SHOW, CANCELLED),
        CONFIRMED to setOf(RESCHEDULED, COMPLETED, NO_SHOW, CANCELLED),
        RESCHEDULED to setOf(CONFIRMED, RESCHEDULED, COMPLETED, NO_SHOW, CANCELLED),
        COMPLETED to emptySet(),
        NO_SHOW to emptySet(),
        CANCELLED to emptySet(),
    )

    // El orden es el de uso: primero avanzar la cita, al final lo definitivo.
    private val actionOrder = listOf(
        AppointmentAction.SCHEDULE,
        AppointmentAction.CONFIRM,
        AppointmentAction.RESCHEDULE,
        AppointmentAction.COMPLETE,
        AppointmentAction.MARK_NO_SHOW,
        AppointmentAction.CANCEL,
    )

    fun allowedTargets(from: AppointmentStatus): Set<AppointmentStatus> = transitions.getValue(from)

    fun canTransition(from: AppointmentStatus, to: AppointmentStatus): Boolean = to in allowedTargets(from)

    fun isTerminal(status: AppointmentStatus): Boolean = allowedTargets(status).isEmpty()

    fun targetOf(action: AppointmentAction): AppointmentStatus = when (action) {
        AppointmentAction.SCHEDULE -> SCHEDULED
        AppointmentAction.CONFIRM -> CONFIRMED
        AppointmentAction.RESCHEDULE -> RESCHEDULED
        AppointmentAction.COMPLETE -> COMPLETED
        AppointmentAction.MARK_NO_SHOW -> NO_SHOW
        AppointmentAction.CANCEL -> CANCELLED
    }

    fun canApply(action: AppointmentAction, from: AppointmentStatus): Boolean = canTransition(from, targetOf(action))

    fun actionsFor(status: AppointmentStatus): List<AppointmentAction> = actionOrder.filter { canApply(it, status) }

    /** Una cita terminal ya no cambia: ni su estado ni sus datos administrativos. */
    fun canEditAdministrativeData(status: AppointmentStatus): Boolean = !isTerminal(status)
}
