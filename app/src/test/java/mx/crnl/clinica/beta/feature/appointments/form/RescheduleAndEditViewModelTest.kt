package mx.crnl.clinica.beta.feature.appointments.form

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.appointment.AppointmentField
import mx.crnl.clinica.beta.domain.appointment.AppointmentIssue
import mx.crnl.clinica.beta.domain.appointment.ConflictKind
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AppointmentDetail
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ScheduleConflict
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.FakeAppointmentRepository
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.domainAppointment
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.testInstant
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RescheduleAndEditViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val coordinator = userAccount(id = "coord", role = UserRole.AREA_COORDINATOR, area = ClinicalArea.PSYCHOLOGY)
    private val nutritionCoordinator = userAccount(id = "nut", role = UserRole.AREA_COORDINATOR, area = ClinicalArea.NUTRITION)
    private val appointments = FakeAppointmentRepository()

    private fun detail(status: AppointmentStatus = AppointmentStatus.SCHEDULED, modality: AppointmentModality = AppointmentModality.IN_PERSON) = AppointmentDetail(
        summary = domainAppointment(id = "c1", status = status, start = testInstant(2, 15), minutes = 45, professionalId = "pro-1")
            .copy(modality = modality, location = if (modality == AppointmentModality.IN_PERSON) "Consultorio 2" else null),
        patientFirstName = "Ana",
        contactPhone = null,
        meetingUrl = if (modality == AppointmentModality.ONLINE) "https://meet.example.org/x" else null,
        administrativeNotes = "Nota",
        createdByName = "Claudia",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        encounters = emptyList(),
    )

    private fun show(detail: AppointmentDetail) {
        appointments.details.value = mapOf(detail.appointmentId to detail)
    }

    private fun auth(user: UserAccount) = FakeAuthRepository(listOf(user to "x"), initialUserId = user.userId)

    private fun reschedule(user: UserAccount = coordinator) =
        RescheduleAppointmentViewModel(SavedStateHandle(mapOf("appointmentId" to "c1")), auth(user), appointments, fixedClock())

    private fun edit(user: UserAccount = coordinator) =
        EditAppointmentViewModel(SavedStateHandle(mapOf("appointmentId" to "c1")), auth(user), appointments)

    // ---------------------------------------------------------------- reprogramar

    @Test
    fun `reprogramar abre con el horario y la duracion actuales`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        val viewModel = reschedule()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.canEdit)
        assertEquals("01102026", state.schedule.dateDigits)
        assertEquals("1500", state.schedule.timeDigits)
        assertEquals(45, state.schedule.durationMinutes)
        assertFalse(state.hasChanges)
    }

    @Test
    fun `reprogramar envia el nuevo horario con el estado que se veia y termina`() = runTest(mainDispatcher.dispatcher) {
        show(detail(AppointmentStatus.CONFIRMED))
        appointments.rescheduleResult = OperationResult.Success(detail(AppointmentStatus.RESCHEDULED))
        val viewModel = reschedule()
        advanceUntilIdle()
        viewModel.onDateChange("02102026")
        viewModel.onTimeChange("0930")
        viewModel.onDurationChange(60)

        viewModel.onSave()
        advanceUntilIdle()

        val (id, schedule, expected) = appointments.rescheduled.single()
        assertEquals("c1", id)
        assertEquals(testInstant(3, 9, 30), schedule.start)
        assertEquals(60, schedule.durationMinutes)
        assertEquals(AppointmentStatus.CONFIRMED, expected)
        assertTrue(viewModel.state.value.isSaved)
    }

    @Test
    fun `reprogramar valida el horario antes de llamar al repositorio`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        val viewModel = reschedule()
        advanceUntilIdle()
        viewModel.onDateChange("")
        viewModel.onTimeChange("2561")

        viewModel.onSave()
        advanceUntilIdle()

        val issues = viewModel.state.value.schedule.issues
        assertEquals(AppointmentIssue.REQUIRED, issues[AppointmentField.DATE])
        assertEquals(AppointmentIssue.INVALID, issues[AppointmentField.TIME])
        assertTrue(appointments.rescheduled.isEmpty())
    }

    @Test
    fun `reprogramar a un horario ocupado muestra el conflicto y conserva lo capturado`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        val conflict = ScheduleConflict(ConflictKind.PATIENT, testInstant(3, 9), testInstant(3, 10), null, null, null)
        appointments.rescheduleResult = OperationResult.Failure(OperationError.ScheduleConflicts(listOf(conflict)))
        val viewModel = reschedule()
        advanceUntilIdle()
        viewModel.onDateChange("02102026")
        viewModel.onTimeChange("0930")

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(listOf(conflict), viewModel.state.value.conflicts)
        assertFalse(viewModel.state.value.isSaved)
        assertEquals("0930", viewModel.state.value.schedule.timeDigits)
    }

    @Test
    fun `si la cita cambio de estado mientras se editaba se informa y no se guarda`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        appointments.rescheduleResult = OperationResult.Failure(OperationError.StatusChanged(AppointmentStatus.CANCELLED))
        val viewModel = reschedule()
        advanceUntilIdle()
        viewModel.onTimeChange("1600")

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(WriteState.Failed(OperationError.StatusChanged(AppointmentStatus.CANCELLED)), viewModel.state.value.write)
        assertFalse(viewModel.state.value.isSaved)
    }

    @Test
    fun `no se reprograma una cita pendiente, terminal, de otra area o inexistente`() = runTest(mainDispatcher.dispatcher) {
        for (status in listOf(AppointmentStatus.PENDING, AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED)) {
            show(detail(status))
            val viewModel = reschedule()
            advanceUntilIdle()
            assertTrue(status.name, viewModel.state.value.notAllowed)
            assertFalse(status.name, viewModel.state.value.canEdit)
        }
        show(detail())
        val otherArea = reschedule(nutritionCoordinator)
        advanceUntilIdle()
        // El repositorio real no entrega el detalle a otra área; aquí el fake sí, y la política lo rechaza igualmente.
        assertTrue(otherArea.state.value.notAllowed)

        appointments.details.value = emptyMap()
        val missing = reschedule()
        advanceUntilIdle()
        assertTrue(missing.state.value.notFound)
    }

    @Test
    fun `un doble toque al reprogramar solo guarda una vez`() = runTest(mainDispatcher.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        appointments.gate = gate
        show(detail())
        appointments.rescheduleResult = OperationResult.Success(detail(AppointmentStatus.RESCHEDULED))
        val viewModel = reschedule()
        advanceUntilIdle()
        viewModel.onTimeChange("1600")

        viewModel.onSave()
        advanceUntilIdle()
        viewModel.onSave()
        viewModel.onSave()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, appointments.rescheduled.size)
    }

    // ---------------------------------------------------------------- editar

    @Test
    fun `editar abre con los datos administrativos actuales y sin cambios`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        val viewModel = edit()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.canEdit)
        assertEquals(AppointmentModality.IN_PERSON, state.logistics.modality)
        assertEquals("Consultorio 2", state.logistics.location)
        assertEquals("Nota", state.logistics.notes)
        assertFalse(state.hasChanges)
    }

    @Test
    fun `editar y guardar envia solo datos administrativos`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        appointments.updateResult = OperationResult.Success(detail())
        val viewModel = edit()
        advanceUntilIdle()
        viewModel.onModalityChange(AppointmentModality.ONLINE)
        viewModel.onMeetingUrlChange("https://meet.example.org/nuevo")
        viewModel.onNotesChange("Otra nota")
        assertTrue(viewModel.state.value.hasChanges)

        viewModel.onSave()
        advanceUntilIdle()

        val (id, update, actor) = appointments.updated.single()
        assertEquals("c1", id)
        assertEquals("coord", actor)
        assertEquals(AppointmentModality.ONLINE, update.modality)
        assertEquals("https://meet.example.org/nuevo", update.meetingUrl)
        assertEquals("Otra nota", update.administrativeNotes)
        assertTrue(viewModel.state.value.isSaved)
    }

    @Test
    fun `editar valida la ubicacion y el enlace antes de guardar`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        val viewModel = edit()
        advanceUntilIdle()
        viewModel.onLocationChange("   ")

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(AppointmentIssue.REQUIRED, viewModel.state.value.logistics.issues[AppointmentField.LOCATION])
        assertTrue(appointments.updated.isEmpty())

        show(detail(modality = AppointmentModality.ONLINE))
        val online = edit()
        advanceUntilIdle()
        online.onMeetingUrlChange("no es un enlace")
        online.onSave()
        advanceUntilIdle()
        assertEquals(AppointmentIssue.INVALID, online.state.value.logistics.issues[AppointmentField.MEETING_URL])
    }

    @Test
    fun `una cita terminada o de otra area no se edita`() = runTest(mainDispatcher.dispatcher) {
        show(detail(AppointmentStatus.COMPLETED))
        val done = edit()
        advanceUntilIdle()
        assertTrue(done.state.value.notEditable)

        show(detail())
        val otherArea = edit(nutritionCoordinator)
        advanceUntilIdle()
        assertTrue(otherArea.state.value.notEditable)
    }

    @Test
    fun `un fallo al guardar la edicion deja el formulario y permite reintentar`() = runTest(mainDispatcher.dispatcher) {
        show(detail())
        appointments.updateResult = OperationResult.Failure(OperationError.NotEditable(AppointmentStatus.COMPLETED))
        val viewModel = edit()
        advanceUntilIdle()
        viewModel.onNotesChange("Cambio")

        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.write is WriteState.Failed)
        assertEquals("Cambio", viewModel.state.value.logistics.notes)
        assertFalse(viewModel.state.value.isSaved)
    }
}
