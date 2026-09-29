package mx.crnl.clinica.beta.feature.patients

import androidx.lifecycle.SavedStateHandle
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.domain.model.ActiveAssignment
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.FakePatientRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.domainPatient
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.patientRecord
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PatientsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val professional = userAccount(id = "mariana")
    private val coordinator = userAccount(id = "claudia", role = UserRole.AREA_COORDINATOR)

    private val ana = patientRecord(
        domainPatient(id = "1", number = "CRNL-000001", firstName = "Ana", paternalSurname = "Cavazos"),
        phone = "+528100000101",
        assignments = listOf(ActiveAssignment(ClinicalArea.PSYCHOLOGY, "mariana")),
    )
    private val diego = patientRecord(
        domainPatient(id = "2", number = "CRNL-000002", firstName = "Diego", paternalSurname = "Treviño", sex = Sex.MALE),
        assignments = listOf(ActiveAssignment(ClinicalArea.NUTRITION, "paola")),
    )

    private fun viewModel(
        repository: FakePatientRepository = FakePatientRepository(listOf(ana, diego)),
        user: UserAccount = professional,
        handle: SavedStateHandle = SavedStateHandle(),
    ) = PatientsViewModel(handle, FakeAuthRepository(listOf(user to "clave"), initialUserId = user.userId), repository, fixedClock())

    private fun PatientsViewModel.content() = (state.value as UiState.Content).data.patients

    @Test
    fun `pasa de cargando a contenido con la edad calculada al dia de hoy`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel(FakePatientRepository(listOf(ana)))
        val states = mutableListOf<UiState<PatientListContent>>()
        viewModel.state.onEach(states::add).launchIn(backgroundScope)

        runCurrent()
        assertEquals(UiState.Loading, states.first())
        advanceUntilIdle()

        val item = (states.last() as UiState.Content).data.patients.single()
        assertEquals("Ana Cavazos Ibarra", item.displayName)
        assertEquals("CRNL-000001", item.patientNumber)
        assertEquals(28, item.ageYears)
        assertEquals(Sex.FEMALE, item.sex)
        assertEquals(PatientStatus.ACTIVE, item.status)
        assertEquals(listOf(ClinicalArea.PSYCHOLOGY), item.areas)
    }

    @Test
    fun `sin pacientes muestra el estado vacio`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel(FakePatientRepository())
        viewModel.state.launchIn(backgroundScope)

        advanceUntilIdle()

        assertEquals(UiState.Empty, viewModel.state.value)
    }

    @Test
    fun `refleja los cambios del repositorio sin reiniciar`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakePatientRepository()
        val viewModel = viewModel(repository)
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(UiState.Empty, viewModel.state.value)

        repository.records.value = listOf(ana, diego)
        advanceUntilIdle()

        assertEquals(2, viewModel.content().size)
    }

    @Test
    fun `buscar filtra el listado despues de una breve pausa al escribir`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(2, viewModel.content().size)

        viewModel.onQueryChange("trevino")
        assertEquals("el texto se muestra de inmediato", "trevino", viewModel.query.value)
        advanceTimeBy(100)
        assertEquals("todavía no se aplica", 2, viewModel.content().size)
        advanceUntilIdle()

        assertEquals(listOf("CRNL-000002"), viewModel.content().map { it.patientNumber })
    }

    @Test
    fun `una busqueda sin coincidencias se distingue de no tener pacientes`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        viewModel.state.launchIn(backgroundScope)

        viewModel.onQueryChange("zzzz")
        advanceUntilIdle()

        assertEquals(UiState.Empty, viewModel.state.value)
        assertEquals("zzzz", viewModel.query.value)
    }

    @Test
    fun `borrar la busqueda restaura el listado completo`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        viewModel.state.launchIn(backgroundScope)
        viewModel.onQueryChange("zzzz")
        advanceUntilIdle()

        viewModel.onQueryChange("")
        advanceUntilIdle()

        assertEquals(2, viewModel.content().size)
    }

    @Test
    fun `mis pacientes muestra solo las asignaciones vigentes del profesional`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        viewModel.state.launchIn(backgroundScope)
        viewModel.canFilterByAssignment.launchIn(backgroundScope)
        advanceUntilIdle()
        assertTrue(viewModel.canFilterByAssignment.value)

        viewModel.onScopeChange(PatientsScope.MINE)
        advanceUntilIdle()

        assertEquals(listOf("CRNL-000001"), viewModel.content().map { it.patientNumber })
    }

    @Test
    fun `la busqueda y el filtro se combinan`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        viewModel.state.launchIn(backgroundScope)
        viewModel.onScopeChange(PatientsScope.MINE)
        viewModel.onQueryChange("trevino")
        advanceUntilIdle()

        assertEquals(UiState.Empty, viewModel.state.value)
    }

    @Test
    fun `quien no atiende pacientes asignados no ve el filtro ni queda filtrado`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel(user = coordinator)
        viewModel.state.launchIn(backgroundScope)
        viewModel.canFilterByAssignment.launchIn(backgroundScope)

        viewModel.onScopeChange(PatientsScope.MINE)
        advanceUntilIdle()

        assertFalse(viewModel.canFilterByAssignment.value)
        assertEquals(2, viewModel.content().size)
    }

    @Test
    fun `la busqueda y el filtro sobreviven al recrear la pantalla con el mismo estado guardado`() = runTest(mainDispatcher.dispatcher) {
        val handle = SavedStateHandle()
        val first = viewModel(handle = handle)
        first.state.launchIn(backgroundScope)
        first.onQueryChange("ana")
        first.onScopeChange(PatientsScope.MINE)
        advanceUntilIdle()

        val second = viewModel(handle = handle)
        second.state.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals("ana", second.query.value)
        assertEquals(PatientsScope.MINE, second.scope.value)
        assertEquals(listOf("CRNL-000001"), second.content().map { it.patientNumber })
    }

    @Test
    fun `un fallo de lectura muestra el error y reintentar recupera el contenido`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakePatientRepository(listOf(ana)).apply { failing = true }
        val viewModel = viewModel(repository)
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Error)

        repository.failing = false
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Content)
    }

    @Test
    fun `la edad usa la fecha del reloj y no la del dispositivo`() = runTest(mainDispatcher.dispatcher) {
        val birthday = patientRecord(domainPatient(id = "3", birthDate = LocalDate.of(2000, 9, 29)))
        val viewModel = viewModel(FakePatientRepository(listOf(birthday)))
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals(26, viewModel.content().single().ageYears)
    }
}
