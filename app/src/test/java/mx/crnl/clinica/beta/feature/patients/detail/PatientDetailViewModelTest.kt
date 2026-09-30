package mx.crnl.clinica.beta.feature.patients.detail

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.FakePatientRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.domainPatient
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.patientAssignment
import mx.crnl.clinica.beta.testing.patientDetail
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `el area de la ruta abre esa pestana y el ViewModel conoce quien consulta`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = PatientDetailViewModel(
            SavedStateHandle(mapOf("patientId" to "p1", "area" to "NUTRITION")),
            auth,
            patients,
            fixedClock(),
        )
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()

        val content = (viewModel.state.value as UiState.Content).data
        assertEquals(ClinicalArea.NUTRITION, content.initialArea)
        assertEquals("mariana", content.viewer.userId)
    }

    @Test
    fun `un area invalida en la ruta se ignora`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = PatientDetailViewModel(SavedStateHandle(mapOf("patientId" to "p1", "area" to "NO_EXISTE")), auth, patients, fixedClock())
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()

        assertNull((viewModel.state.value as UiState.Content).data.initialArea)
    }

    @Test
    fun `las acciones de cada area salen de la politica y de la asignacion vigente`() = runTest(mainDispatcher.dispatcher) {
        patients.details.value = mapOf(
            "p1" to patientDetail(
                domainPatient(id = "p1"),
                assignments = listOf(patientAssignment(ClinicalArea.PSYCHOLOGY, professionalId = "mariana")),
                viewableAreas = setOf(ClinicalArea.PSYCHOLOGY),
            ),
        )
        val viewModel = viewModel()
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()

        val content = (viewModel.state.value as UiState.Content).data
        assertTrue(content.capabilities(ClinicalArea.PSYCHOLOGY).canSchedule)
        assertTrue(content.capabilities(ClinicalArea.PSYCHOLOGY).canRegisterEncounter)
        assertFalse(content.capabilities(ClinicalArea.PSYCHOLOGY).canAssign)
        assertFalse(content.capabilities(ClinicalArea.NUTRITION).canViewDetail)
        assertFalse(content.capabilities(ClinicalArea.NUTRITION).canAssign)
        assertTrue(content.canSchedule)
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
