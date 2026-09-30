package mx.crnl.clinica.beta.feature.appointments

import androidx.lifecycle.SavedStateHandle
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.domain.appointment.AgendaFilter
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.FakeAppointmentRepository
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.domainAppointment
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val now: Instant = TestNow.toInstant()
    private val admin = userAccount(id = "admin", role = UserRole.CLINICAL_ADMIN, area = null)
    private val professional = userAccount(id = "user-1", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)

    private fun appointment(
        id: String,
        start: Instant,
        minutes: Long = 50,
        status: AppointmentStatus = AppointmentStatus.SCHEDULED,
        professionalId: String = "user-1",
    ) = domainAppointment(id = id, start = start, minutes = minutes, status = status, professionalId = professionalId)

    private fun viewModel(repository: FakeAppointmentRepository, user: UserAccount = admin) = AppointmentsViewModel(
        SavedStateHandle(),
        FakeAuthRepository(listOf(user to "x"), initialUserId = user.userId),
        repository,
        fixedClock(),
    )

    private fun content(viewModel: AppointmentsViewModel) = (viewModel.state.value as UiState.Content).data

    private fun ids(list: List<AppointmentSummary>) = list.map { it.appointmentId }

    @Test
    fun `por omision muestra las proximas ordenadas de la mas cercana a la mas lejana`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeAppointmentRepository(
            listOf(
                appointment("past-old", now.minus(Duration.ofDays(20))),
                appointment("future-late", now.plus(Duration.ofDays(9))),
                appointment("past-recent", now.minus(Duration.ofDays(2))),
                appointment("future-soon", now.plus(Duration.ofHours(3))),
            ),
        )
        val viewModel = viewModel(repository)
        viewModel.state.launchIn(backgroundScope)

        advanceUntilIdle()

        assertEquals(AgendaFilter.UPCOMING, content(viewModel).filter)
        assertEquals(listOf("future-soon", "future-late"), ids(content(viewModel).appointments))
        assertEquals(4, content(viewModel).total)
    }

    @Test
    fun `el filtro de historial trae lo pasado y lo terminado, lo mas reciente primero`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeAppointmentRepository(
            listOf(
                appointment("past-old", now.minus(Duration.ofDays(20))),
                appointment("cancelled-future", now.plus(Duration.ofDays(2)), status = AppointmentStatus.CANCELLED),
                appointment("past-recent", now.minus(Duration.ofDays(2))),
                appointment("future", now.plus(Duration.ofHours(3))),
            ),
        )
        val viewModel = viewModel(repository)
        viewModel.state.launchIn(backgroundScope)
        viewModel.filter.launchIn(backgroundScope)

        viewModel.selectFilter(AgendaFilter.HISTORY)
        advanceUntilIdle()

        assertEquals(listOf("cancelled-future", "past-recent", "past-old"), ids(content(viewModel).appointments))
    }

    @Test
    fun `el filtro de hoy trae las citas del dia y una cita en curso sigue siendo proxima`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeAppointmentRepository(
            listOf(
                appointment("in-progress", now.minus(Duration.ofMinutes(20))),
                appointment("just-ended", now.minus(Duration.ofMinutes(51))),
                appointment("tomorrow", now.plus(Duration.ofDays(1))),
            ),
        )
        val viewModel = viewModel(repository)
        viewModel.state.launchIn(backgroundScope)
        viewModel.filter.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals(listOf("in-progress", "tomorrow"), ids(content(viewModel).appointments))

        viewModel.selectFilter(AgendaFilter.TODAY)
        advanceUntilIdle()
        assertEquals(listOf("just-ended", "in-progress"), ids(content(viewModel).appointments))
    }

    @Test
    fun `un profesional solo ve sus propias citas`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeAppointmentRepository(
            listOf(
                appointment("mia", now.plus(Duration.ofHours(2)), professionalId = "user-1"),
                appointment("ajena", now.plus(Duration.ofHours(3)), professionalId = "otro"),
            ),
        )
        val viewModel = viewModel(repository, professional)
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals(listOf("mia"), ids(content(viewModel).appointments))
    }

    @Test
    fun `sin citas muestra el estado vacio`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel(FakeAppointmentRepository())
        viewModel.state.launchIn(backgroundScope)

        advanceUntilIdle()

        assertEquals(UiState.Empty, viewModel.state.value)
    }

    @Test
    fun `hay citas pero ninguna en el filtro y el contenido lo distingue del vacio total`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeAppointmentRepository(listOf(appointment("vieja", now.minus(Duration.ofDays(9)))))
        val viewModel = viewModel(repository)
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()

        assertTrue(content(viewModel).appointments.isEmpty())
        assertEquals(1, content(viewModel).total)
    }

    @Test
    fun `solo quien puede agendar ve el boton de nueva cita`() = runTest(mainDispatcher.dispatcher) {
        val systemAdmin = userAccount(id = "sys", role = UserRole.SYSTEM_ADMIN, area = null)
        val forAdmin = viewModel(FakeAppointmentRepository(), admin).also { it.canCreate.launchIn(backgroundScope) }
        val forSystem = viewModel(FakeAppointmentRepository(), systemAdmin).also { it.canCreate.launchIn(backgroundScope) }
        advanceUntilIdle()

        assertTrue(forAdmin.canCreate.value)
        assertFalse(forSystem.canCreate.value)
    }

    @Test
    fun `un fallo de lectura muestra el error y reintentar recupera el contenido`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeAppointmentRepository(listOf(appointment("a", now.plus(Duration.ofDays(1))))).apply {
            failing = true
        }
        val viewModel = viewModel(repository)
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Error)

        repository.failing = false
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf("a"), ids(content(viewModel).appointments))
    }
}
