package mx.crnl.clinica.beta.feature.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.domain.home.PatientMetric
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.FakeHomeRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.domainPatient
import mx.crnl.clinica.beta.testing.emptyHomeSummary
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val user = userAccount(id = "mariana")
    private val auth = FakeAuthRepository(listOf(user to "clave"), initialUserId = "mariana")
    private val home = FakeHomeRepository()

    private fun viewModel() = HomeViewModel(auth, home, fixedClock())

    @Test
    fun `pasa de cargando al resumen del usuario autenticado`() = runTest(mainDispatcher.dispatcher) {
        home.summary.value = emptyHomeSummary().copy(patientCount = 4, upcomingAppointmentCount = 2, pendingRequestCount = 1)
        val viewModel = viewModel()
        val states = mutableListOf<UiState<HomeContent>>()
        viewModel.state.onEach(states::add).launchIn(backgroundScope)

        runCurrent()
        assertEquals(UiState.Loading, states.first())
        advanceUntilIdle()

        val content = (states.last() as UiState.Content).data
        assertEquals(user, content.user)
        assertEquals(PatientMetric.ASSIGNED_TO_USER, content.summary.patientMetric)
        assertEquals(4, content.summary.patientCount)
        assertEquals(2, content.summary.upcomingAppointmentCount)
        assertEquals(1, content.summary.pendingRequestCount)
        assertEquals(listOf(user), home.requestedFor)
    }

    @Test
    fun `los indicadores se actualizan cuando cambian los datos sin reiniciar`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(0, (viewModel.state.value as UiState.Content).data.summary.patientCount)

        home.summary.value = emptyHomeSummary().copy(patientCount = 5, recentPatients = listOf(domainPatient()))
        advanceUntilIdle()

        val summary = (viewModel.state.value as UiState.Content).data.summary
        assertEquals(5, summary.patientCount)
        assertEquals(1, summary.recentPatients.size)
    }

    @Test
    fun `sin sesion no calcula ningun resumen`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = HomeViewModel(FakeAuthRepository(), home, fixedClock())
        viewModel.state.launchIn(backgroundScope)

        advanceUntilIdle()

        assertEquals(UiState.Loading, viewModel.state.value)
        assertTrue(home.requestedFor.isEmpty())
    }

    @Test
    fun `un fallo de lectura muestra el error y reintentar lo recupera`() = runTest(mainDispatcher.dispatcher) {
        home.failing = true
        val viewModel = viewModel()
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is UiState.Error)

        home.failing = false
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Content)
    }
}
