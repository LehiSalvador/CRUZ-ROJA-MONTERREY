package mx.crnl.clinica.beta.feature.patients

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.testing.FakePatientRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.fixedClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PatientsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun patient(id: String, birthDate: LocalDate = LocalDate.of(1998, 5, 14)) = Patient(
        patientId = id,
        patientNumber = "CRNL-00000$id",
        firstName = "Ana",
        paternalSurname = "Cavazos",
        maternalSurname = "Ibarra",
        birthDate = birthDate,
        birthPlace = null,
        sex = Sex.FEMALE,
        municipality = "Monterrey",
        populationType = PopulationType.STUDENT,
        status = PatientStatus.ACTIVE,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `pasa de cargando a contenido con la edad calculada al dia de hoy`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakePatientRepository(listOf(patient("1")))
        val viewModel = PatientsViewModel(repository, fixedClock())
        val states = mutableListOf<UiState<List<PatientListItem>>>()
        viewModel.state.onEach(states::add).launchIn(backgroundScope)

        runCurrent()
        assertEquals(UiState.Loading, states.first())
        advanceUntilIdle()

        val content = states.last() as UiState.Content
        val item = content.data.single()
        assertEquals("Ana Cavazos Ibarra", item.displayName)
        assertEquals("CRNL-000001", item.patientNumber)
        assertEquals(28, item.ageYears)
        assertEquals("Monterrey", item.municipality)
        assertEquals(PatientStatus.ACTIVE, item.status)
    }

    @Test
    fun `sin pacientes muestra el estado vacio`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = PatientsViewModel(FakePatientRepository(), fixedClock())
        viewModel.state.launchIn(backgroundScope)

        advanceUntilIdle()

        assertEquals(UiState.Empty, viewModel.state.value)
    }

    @Test
    fun `refleja los cambios del repositorio sin reiniciar`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = PatientsViewModel(repository, fixedClock())
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(UiState.Empty, viewModel.state.value)

        repository.patients.value = listOf(patient("1"), patient("2"))
        advanceUntilIdle()

        assertEquals(2, (viewModel.state.value as UiState.Content).data.size)
    }

    @Test
    fun `un fallo de lectura muestra el error y reintentar recupera el contenido`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakePatientRepository(listOf(patient("1"))).apply { failing = true }
        val viewModel = PatientsViewModel(repository, fixedClock())
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Error)

        repository.failing = false
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Content)
    }
}
