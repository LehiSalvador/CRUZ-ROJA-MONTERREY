package mx.crnl.clinica.beta.feature.appointments

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.testing.FakeAppointmentRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.fixedClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val now: Instant = TestNow.toInstant()

    private fun appointment(id: String, start: Instant, minutes: Long = 50) = AppointmentSummary(
        appointmentId = id,
        patientId = "patient-$id",
        patientName = "Paciente $id",
        patientNumber = "CRNL-000001",
        professionalId = "user-1",
        professionalName = "Profesional",
        area = ClinicalArea.PSYCHOLOGY,
        start = start,
        end = start.plus(Duration.ofMinutes(minutes)),
        modality = AppointmentModality.IN_PERSON,
        location = "Consultorio 1",
        status = AppointmentStatus.SCHEDULED,
    )

    private fun content(viewModel: AppointmentsViewModel) = (viewModel.state.value as UiState.Content).data

    @Test
    fun `separa proximas e historial y ordena cada grupo`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeAppointmentRepository(
            listOf(
                appointment("past-old", now.minus(Duration.ofDays(20))),
                appointment("future-late", now.plus(Duration.ofDays(9))),
                appointment("past-recent", now.minus(Duration.ofDays(2))),
                appointment("future-soon", now.plus(Duration.ofHours(3))),
            ),
        )
        val viewModel = AppointmentsViewModel(repository, fixedClock())
        viewModel.state.launchIn(backgroundScope)

        advanceUntilIdle()

        assertEquals(listOf("future-soon", "future-late"), content(viewModel).upcoming.map { it.appointmentId })
        assertEquals(listOf("past-recent", "past-old"), content(viewModel).history.map { it.appointmentId })
    }

    @Test
    fun `una cita en curso todavia cuenta como proxima`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeAppointmentRepository(
            listOf(
                appointment("in-progress", now.minus(Duration.ofMinutes(20)), minutes = 50),
                appointment("just-ended", now.minus(Duration.ofMinutes(51)), minutes = 50),
            ),
        )
        val viewModel = AppointmentsViewModel(repository, fixedClock())
        viewModel.state.launchIn(backgroundScope)

        advanceUntilIdle()

        assertEquals(listOf("in-progress"), content(viewModel).upcoming.map { it.appointmentId })
        assertEquals(listOf("just-ended"), content(viewModel).history.map { it.appointmentId })
    }

    @Test
    fun `sin citas muestra el estado vacio`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = AppointmentsViewModel(FakeAppointmentRepository(), fixedClock())
        viewModel.state.launchIn(backgroundScope)

        advanceUntilIdle()

        assertEquals(UiState.Empty, viewModel.state.value)
    }

    @Test
    fun `un fallo de lectura muestra el error y reintentar recupera el contenido`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeAppointmentRepository(listOf(appointment("a", now.plus(Duration.ofDays(1))))).apply {
            failing = true
        }
        val viewModel = AppointmentsViewModel(repository, fixedClock())
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Error)

        repository.failing = false
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf("a"), content(viewModel).upcoming.map { it.appointmentId })
    }
}
