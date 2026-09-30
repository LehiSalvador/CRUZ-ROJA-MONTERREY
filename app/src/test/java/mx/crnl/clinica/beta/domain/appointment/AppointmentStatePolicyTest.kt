package mx.crnl.clinica.beta.domain.appointment

import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AppointmentStatus.CANCELLED
import mx.crnl.clinica.beta.domain.model.AppointmentStatus.COMPLETED
import mx.crnl.clinica.beta.domain.model.AppointmentStatus.CONFIRMED
import mx.crnl.clinica.beta.domain.model.AppointmentStatus.NO_SHOW
import mx.crnl.clinica.beta.domain.model.AppointmentStatus.PENDING
import mx.crnl.clinica.beta.domain.model.AppointmentStatus.RESCHEDULED
import mx.crnl.clinica.beta.domain.model.AppointmentStatus.SCHEDULED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppointmentStatePolicyTest {
    // Matriz de la Beta: cada estado con los únicos destinos válidos. Es la especificación, no un espejo del código.
    private val expected: Map<AppointmentStatus, Set<AppointmentStatus>> = mapOf(
        PENDING to setOf(SCHEDULED, CONFIRMED, CANCELLED),
        SCHEDULED to setOf(CONFIRMED, RESCHEDULED, COMPLETED, NO_SHOW, CANCELLED),
        CONFIRMED to setOf(RESCHEDULED, COMPLETED, NO_SHOW, CANCELLED),
        // Reprogramar una cita ya reprogramada es la única desviación de la matriz recomendada.
        RESCHEDULED to setOf(CONFIRMED, RESCHEDULED, COMPLETED, NO_SHOW, CANCELLED),
        COMPLETED to emptySet(),
        NO_SHOW to emptySet(),
        CANCELLED to emptySet(),
    )

    @Test
    fun `cada par de estados permite exactamente las transiciones de la matriz`() {
        for (from in AppointmentStatus.entries) {
            for (to in AppointmentStatus.entries) {
                assertEquals(
                    "$from -> $to",
                    to in expected.getValue(from),
                    AppointmentStatePolicy.canTransition(from, to),
                )
            }
        }
    }

    @Test
    fun `los destinos permitidos coinciden con la matriz`() {
        for (status in AppointmentStatus.entries) {
            assertEquals(status.name, expected.getValue(status), AppointmentStatePolicy.allowedTargets(status))
        }
    }

    @Test
    fun `realizada, no asistio y cancelada son terminales y no permiten ningun cambio`() {
        for (terminal in listOf(COMPLETED, NO_SHOW, CANCELLED)) {
            assertTrue(terminal.name, AppointmentStatePolicy.isTerminal(terminal))
            assertTrue(terminal.name, AppointmentStatePolicy.allowedTargets(terminal).isEmpty())
            assertTrue(terminal.name, AppointmentStatePolicy.actionsFor(terminal).isEmpty())
            assertFalse(terminal.name, AppointmentStatePolicy.canEditAdministrativeData(terminal))
        }
    }

    @Test
    fun `los estados abiertos no son terminales y admiten editar datos administrativos`() {
        for (open in listOf(PENDING, SCHEDULED, CONFIRMED, RESCHEDULED)) {
            assertFalse(open.name, AppointmentStatePolicy.isTerminal(open))
            assertTrue(open.name, AppointmentStatePolicy.canEditAdministrativeData(open))
        }
    }

    @Test
    fun `las acciones de un estado son las que llevan a un destino valido, en orden de uso`() {
        assertEquals(
            listOf(AppointmentAction.SCHEDULE, AppointmentAction.CONFIRM, AppointmentAction.CANCEL),
            AppointmentStatePolicy.actionsFor(PENDING),
        )
        assertEquals(
            listOf(
                AppointmentAction.CONFIRM,
                AppointmentAction.RESCHEDULE,
                AppointmentAction.COMPLETE,
                AppointmentAction.MARK_NO_SHOW,
                AppointmentAction.CANCEL,
            ),
            AppointmentStatePolicy.actionsFor(SCHEDULED),
        )
        assertEquals(
            listOf(
                AppointmentAction.RESCHEDULE,
                AppointmentAction.COMPLETE,
                AppointmentAction.MARK_NO_SHOW,
                AppointmentAction.CANCEL,
            ),
            AppointmentStatePolicy.actionsFor(CONFIRMED),
        )
        assertEquals(
            listOf(
                AppointmentAction.CONFIRM,
                AppointmentAction.RESCHEDULE,
                AppointmentAction.COMPLETE,
                AppointmentAction.MARK_NO_SHOW,
                AppointmentAction.CANCEL,
            ),
            AppointmentStatePolicy.actionsFor(RESCHEDULED),
        )
    }

    @Test
    fun `toda accion ofrecida lleva a una transicion valida y ninguna otra se ofrece`() {
        for (status in AppointmentStatus.entries) {
            val offered = AppointmentStatePolicy.actionsFor(status)
            for (action in AppointmentAction.entries) {
                assertEquals(
                    "$status / $action",
                    action in offered,
                    AppointmentStatePolicy.canApply(action, status),
                )
            }
        }
    }

    @Test
    fun `cada accion tiene un estado destino propio`() {
        assertEquals(SCHEDULED, AppointmentStatePolicy.targetOf(AppointmentAction.SCHEDULE))
        assertEquals(CONFIRMED, AppointmentStatePolicy.targetOf(AppointmentAction.CONFIRM))
        assertEquals(RESCHEDULED, AppointmentStatePolicy.targetOf(AppointmentAction.RESCHEDULE))
        assertEquals(COMPLETED, AppointmentStatePolicy.targetOf(AppointmentAction.COMPLETE))
        assertEquals(NO_SHOW, AppointmentStatePolicy.targetOf(AppointmentAction.MARK_NO_SHOW))
        assertEquals(CANCELLED, AppointmentStatePolicy.targetOf(AppointmentAction.CANCEL))
    }
}
