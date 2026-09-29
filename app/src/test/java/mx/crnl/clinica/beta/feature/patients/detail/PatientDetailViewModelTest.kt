package mx.crnl.clinica.beta.feature.patients.detail

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.FakePatientRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.domainPatient
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.patientDetail
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PatientDetailViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val user = userAccount(id = "mariana")
    private val auth = FakeAuthRepository(listOf(user to "clave"), initialUserId = "mariana")
    private val patients = FakePatientRepository().apply {
        details.value = mapOf("p1" to patientDetail(domainPatient(id = "p1")))
    }

    private fun viewModel(patientId: String = "p1") =
        PatientDetailViewModel(SavedStateHandle(mapOf("patientId" to patientId)), auth, patients, fixedClock())

    @Test
    fun `muestra el expediente con la fecha y la hora del reloj`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()

        val content = (viewModel.state.value as UiState.Content).data
        assertEquals("p1", content.detail.patient.patientId)
        assertEquals(TestNow.toLocalDate(), content.today)
        assertEquals(TestNow.toInstant(), content.now)
    }

    @Test
    fun `un paciente inexistente se muestra como vacio y no se audita`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel(patientId = "no-existe")
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals(UiState.Empty, viewModel.state.value)
        assertTrue(patients.viewed.isEmpty())
    }

    @Test
    fun `registra la consulta del expediente una sola vez con el usuario autenticado`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals(listOf("p1" to "mariana"), patients.viewed)
    }

    @Test
    fun `recomponer, reintentar o recibir cambios no vuelve a registrar la consulta`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()

        patients.details.value = mapOf("p1" to patientDetail(domainPatient(id = "p1", firstName = "Ana Lucía")))
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(1, patients.viewed.size)
    }

    @Test
    fun `un fallo de lectura muestra el error y reintentar recupera el expediente`() = runTest(mainDispatcher.dispatcher) {
        patients.failing = true
        val viewModel = viewModel()
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is UiState.Error)

        patients.failing = false
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Content)
    }
}
