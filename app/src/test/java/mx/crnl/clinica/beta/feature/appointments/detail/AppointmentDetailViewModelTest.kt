package mx.crnl.clinica.beta.feature.appointments.detail

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.domain.appointment.AppointmentAction
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AppointmentDetail
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.EncounterStatus
import mx.crnl.clinica.beta.domain.model.EncounterSummary
import mx.crnl.clinica.beta.domain.model.EncounterType
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.FakeAppointmentRepository
import mx.crnl.clinica.beta.testing.FakeAssignmentRepository
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.domainAppointment
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.patientAssignment
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AppointmentDetailViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val coordinator = userAccount(id = "coord", role = UserRole.AREA_COORDINATOR, area = ClinicalArea.PSYCHOLOGY)
    private val owner = userAccount(id = "pro-1", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)
    private val otherProfessional = userAccount(id = "pro-2", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)
    private val nutritionCoordinator = userAccount(id = "nut", role = UserRole.AREA_COORDINATOR, area = ClinicalArea.NUTRITION)
    private val systemAdmin = userAccount(id = "sys", role = UserRole.SYSTEM_ADMIN, area = null)

    private val appointments = FakeAppointmentRepository()
    private val assignments = FakeAssignmentRepository().apply {
        active.value = listOf(patientAssignment(ClinicalArea.PSYCHOLOGY, professionalId = "pro-1"))
    }

    private fun detail(
        status: AppointmentStatus = AppointmentStatus.SCHEDULED,
        phone: String? = "+528100000101",
        encounters: List<EncounterSummary> = emptyList(),
        professionalId: String = "pro-1",
    ) = AppointmentDetail(
        summary = domainAppointment(id = "c1", status = status, professionalId = professionalId, start = TestNow.plusDays(1).toInstant()),
        patientFirstName = "Ana Lucía",
        contactPhone = phone,
        meetingUrl = null,
        administrativeNotes = null,
        createdByName = "Claudia Benavides",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        encounters = encounters,
    )

    private fun show(detail: AppointmentDetail) {
        appointments.details.value = mapOf(detail.appointmentId to detail)
    }

    private fun viewModel(user: UserAccount = coordinator): AppointmentDetailViewModel {
        val auth = FakeAuthRepository(listOf(user to "x"), initialUserId = user.userId)
        return AppointmentDetailViewModel(SavedStateHandle(mapOf("appointmentId" to "c1")), auth, appointments, assignments, fixedClock())
    }

    private fun AppointmentDetailViewModel.content() = (state.value as UiState.Content).data

    private fun kotlinx.coroutines.test.TestScope.start(viewModel: AppointmentDetailViewModel) {
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()
    }

    @Test
    fun `una cita programada ofrece confirmar, reprogramar, realizada, no asistio y cancelar, mas editar y contactar`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        val viewModel = viewModel()
        start(viewModel)

        val content = viewModel.content()
        assertEquals(
            listOf(
                AppointmentAction.CONFIRM,
                AppointmentAction.RESCHEDULE,
                AppointmentAction.COMPLETE,
                AppointmentAction.MARK_NO_SHOW,
                AppointmentAction.CANCEL,
            ),
            content.actions,
        )
        assertTrue(content.canEdit)
        assertNotNull(content.contactLink)
        assertFalse(content.canRegisterEncounter)
    }

    @Test
    fun `una cita pendiente ofrece programar y confirmar pero no cerrarla como realizada`() = runTest(mainDispatcher.dispatcher) {
        show(detail(AppointmentStatus.PENDING))
        val viewModel = viewModel()
        start(viewModel)

        assertEquals(listOf(AppointmentAction.SCHEDULE, AppointmentAction.CONFIRM, AppointmentAction.CANCEL), viewModel.content().actions)
    }

    @Test
    fun `una cita terminal no ofrece ninguna accion, ni editar, ni contactar`() = runTest(mainDispatcher.dispatcher) {
        for (status in listOf(AppointmentStatus.NO_SHOW, AppointmentStatus.CANCELLED)) {
            show(detail(status))
            val viewModel = viewModel()
            start(viewModel)

            val content = viewModel.content()
            assertTrue(status.name, content.actions.isEmpty())
            assertFalse(status.name, content.canEdit)
            assertNull(status.name, content.contactLink)
        }
    }

    @Test
    fun `una cita realizada sin atencion ofrece registrarla y con atencion ya no`() = runTest(mainDispatcher.dispatcher) {
        show(detail(AppointmentStatus.COMPLETED))
        val viewModel = viewModel(owner)
        start(viewModel)
        assertTrue(viewModel.content().canRegisterEncounter)
        assertTrue(viewModel.content().actions.isEmpty())

        show(
            detail(
                AppointmentStatus.COMPLETED,
                encounters = listOf(EncounterSummary("e1", ClinicalArea.PSYCHOLOGY, "Mariana", EncounterType.INITIAL, EncounterStatus.COMPLETED, Instant.EPOCH, "c1")),
            ),
        )
        start(viewModel)
        assertFalse(viewModel.content().canRegisterEncounter)
    }

    @Test
    fun `no se ofrece registrar atencion si el paciente no esta asignado al profesional de la cita`() = runTest(mainDispatcher.dispatcher) {
        assignments.active.value = emptyList()
        show(detail(AppointmentStatus.COMPLETED))
        val viewModel = viewModel(coordinator)
        start(viewModel)

        assertFalse(viewModel.content().canRegisterEncounter)
    }

    @Test
    fun `un profesional que no es el titular ve la cita pero ninguna accion`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        val viewModel = viewModel(otherProfessional)
        start(viewModel)

        val content = viewModel.content()
        assertTrue(content.actions.isEmpty())
        assertFalse(content.canEdit)
        assertNull(content.contactLink)
    }

    @Test
    fun `la cita de otra area o para el administrador del sistema se muestra como no disponible`() = runTest(mainDispatcher.dispatcher) {
        // El repositorio no entrega el detalle a quien no ve el área: el ViewModel lo trata como vacío.
        appointments.details.value = emptyMap()
        for (user in listOf(nutritionCoordinator, systemAdmin)) {
            val viewModel = viewModel(user)
            start(viewModel)
            assertEquals(UiState.Empty, viewModel.state.value)
        }
    }

    @Test
    fun `sin telefono utilizable no hay enlace de contacto`() = runTest(mainDispatcher.dispatcher) {
        show(detail(phone = null))
        val viewModel = viewModel()
        start(viewModel)
        assertNull(viewModel.content().contactLink)

        show(detail(phone = "123"))
        start(viewModel)
        assertNull(viewModel.content().contactLink)
    }

    @Test
    fun `el enlace de contacto usa WhatsApp, el nombre y la fecha y no lleva area ni datos clinicos`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        val viewModel = viewModel()
        start(viewModel)

        val link = viewModel.content().contactLink!!
        assertTrue(link.startsWith("whatsapp://send?phone=528100000101&text="))
        val text = java.net.URLDecoder.decode(link.substringAfter("&text="), "UTF-8")
        assertTrue(text.startsWith("Hola Ana Lucía. Te contactamos de Cruz Roja Nuevo León"))
        assertFalse(text.lowercase().contains("psicolog"))
    }

    @Test
    fun `una accion de estado llama al repositorio con el estado que se veia y el actor`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        appointments.actionResult = OperationResult.Success(detail(AppointmentStatus.CONFIRMED))
        val viewModel = viewModel()
        start(viewModel)

        viewModel.onAction(AppointmentAction.CONFIRM)
        advanceUntilIdle()

        assertEquals(listOf(Triple("c1", AppointmentAction.CONFIRM, null)), appointments.actions)
        assertFalse(viewModel.working.value)
        assertNull(viewModel.notice.value)
    }

    @Test
    fun `cancelar envia la razon administrativa`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        appointments.actionResult = OperationResult.Success(detail(AppointmentStatus.CANCELLED))
        val viewModel = viewModel()
        start(viewModel)

        viewModel.onAction(AppointmentAction.CANCEL, "El paciente avisó")
        advanceUntilIdle()

        assertEquals("El paciente avisó", appointments.actions.single().third)
    }

    @Test
    fun `una accion que no esta entre las ofrecidas o reprogramar por esta via no se envia`() = runTest(mainDispatcher.dispatcher) {
        show(detail(AppointmentStatus.PENDING))
        val viewModel = viewModel()
        start(viewModel)

        viewModel.onAction(AppointmentAction.COMPLETE)
        viewModel.onAction(AppointmentAction.RESCHEDULE)
        advanceUntilIdle()

        assertTrue(appointments.actions.isEmpty())
    }

    @Test
    fun `un doble toque no aplica dos veces la accion`() = runTest(mainDispatcher.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        appointments.gate = gate
        show(detail())
        appointments.actionResult = OperationResult.Success(detail(AppointmentStatus.CONFIRMED))
        val viewModel = viewModel()
        start(viewModel)

        viewModel.onAction(AppointmentAction.CONFIRM)
        advanceUntilIdle()
        assertTrue(viewModel.working.value)
        viewModel.onAction(AppointmentAction.CONFIRM)
        viewModel.onAction(AppointmentAction.CANCEL)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, appointments.actions.size)
        assertFalse(viewModel.working.value)
    }

    @Test
    fun `un rechazo del repositorio se avisa sin perder la pantalla y se puede descartar`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        appointments.actionResult = OperationResult.Failure(OperationError.StatusChanged(AppointmentStatus.CANCELLED))
        val viewModel = viewModel()
        start(viewModel)

        viewModel.onAction(AppointmentAction.CONFIRM)
        advanceUntilIdle()

        assertEquals(DetailNotice.Failed(OperationError.StatusChanged(AppointmentStatus.CANCELLED)), viewModel.notice.value)
        viewModel.dismissNotice()
        assertNull(viewModel.notice.value)
    }

    @Test
    fun `un fallo inesperado al guardar se avisa y libera los botones`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        val failing = object : mx.crnl.clinica.beta.domain.repository.AppointmentRepository by appointments {
            override suspend fun applyAction(
                appointmentId: String,
                action: AppointmentAction,
                expectedStatus: AppointmentStatus,
                reason: String?,
                actorUserId: String,
            ): OperationResult<AppointmentDetail> = throw IOException("disco lleno")
        }
        val auth = FakeAuthRepository(listOf(coordinator to "x"), initialUserId = "coord")
        val viewModel = AppointmentDetailViewModel(SavedStateHandle(mapOf("appointmentId" to "c1")), auth, failing, assignments, fixedClock())
        start(viewModel)

        viewModel.onAction(AppointmentAction.CONFIRM)
        advanceUntilIdle()

        assertTrue(viewModel.notice.value is DetailNotice.Unavailable)
        assertFalse(viewModel.working.value)
    }

    @Test
    fun `abrir WhatsApp deja constancia y si no hay aplicacion se avisa sin constancia`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        val viewModel = viewModel()
        start(viewModel)

        viewModel.onContactOpened()
        advanceUntilIdle()
        assertEquals(listOf("c1" to "coord"), appointments.whatsAppOpened)

        viewModel.onContactUnavailable()
        assertEquals(DetailNotice.WhatsAppUnavailable, viewModel.notice.value)
        assertEquals(1, appointments.whatsAppOpened.size)
    }

    @Test
    fun `un fallo de lectura muestra el error y reintentar recupera`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        appointments.failing = true
        val viewModel = viewModel()
        start(viewModel)
        assertTrue(viewModel.state.value is UiState.Error)

        appointments.failing = false
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Content)
    }
}
