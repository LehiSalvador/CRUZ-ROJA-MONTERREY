package mx.crnl.clinica.beta.feature.assignments

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ProfessionalOption
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.FakeAssignmentRepository
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.FakePatientRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.domainPatient
import mx.crnl.clinica.beta.testing.patientAssignment
import mx.crnl.clinica.beta.testing.patientRecord
import mx.crnl.clinica.beta.testing.userAccount
import mx.crnl.clinica.beta.feature.appointments.form.WriteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AssignProfessionalViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val coordinator = userAccount(id = "coord", role = UserRole.AREA_COORDINATOR, area = ClinicalArea.PSYCHOLOGY)
    private val professional = userAccount(id = "pro-1", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)
    private val admin = userAccount(id = "admin", role = UserRole.CLINICAL_ADMIN, area = null)

    private val patients = FakePatientRepository(listOf(patientRecord(domainPatient(id = "ana", firstName = "Ana", paternalSurname = "Cavazos"))))
    private val assignments = FakeAssignmentRepository().apply {
        professionals = listOf(
            ProfessionalOption("pro-1", "Mariana Elizondo Cantú", "00000103", ClinicalArea.PSYCHOLOGY),
            ProfessionalOption("pro-2", "Rodrigo Villarreal Saldaña", "00000104", ClinicalArea.PSYCHOLOGY),
            ProfessionalOption("pro-3", "Paola Garza Leal", "00000105", ClinicalArea.NUTRITION),
        )
    }

    private fun viewModel(user: UserAccount = coordinator, patientId: String = "ana", area: String = "PSYCHOLOGY"): AssignProfessionalViewModel {
        val auth = FakeAuthRepository(listOf(user to "x"), initialUserId = user.userId)
        return AssignProfessionalViewModel(SavedStateHandle(mapOf("patientId" to patientId, "area" to area)), auth, patients, assignments)
    }

    @Test
    fun `coordinacion ve solo los profesionales de su area, sin nada seleccionado`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.block)
        assertEquals(listOf("pro-1", "pro-2"), state.professionals.map { it.userId })
        assertNull(state.selected)
        assertFalse(state.canSubmit)
        assertEquals("Ana Cavazos Ibarra", state.patient?.fullName)
    }

    @Test
    fun `un profesional no puede asignar aunque abra la pantalla`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel(professional)
        advanceUntilIdle()

        assertEquals(AssignBlock.NOT_AUTHORIZED, viewModel.state.value.block)
        viewModel.onSelect("pro-1")
        viewModel.onConfirm()
        advanceUntilIdle()
        assertTrue(assignments.created.isEmpty())
    }

    @Test
    fun `coordinacion no asigna en otra area pero administracion clinica si`() = runTest(mainDispatcher.dispatcher) {
        val other = viewModel(coordinator, area = "NUTRITION")
        advanceUntilIdle()
        assertEquals(AssignBlock.NOT_AUTHORIZED, other.state.value.block)

        val forAdmin = viewModel(admin, area = "NUTRITION")
        advanceUntilIdle()
        assertNull(forAdmin.state.value.block)
        assertEquals(listOf("pro-3"), forAdmin.state.value.professionals.map { it.userId })
    }

    @Test
    fun `si ya hay profesional vigente se informa quien es y no se puede asignar otro`() = runTest(mainDispatcher.dispatcher) {
        assignments.active.value = listOf(patientAssignment(ClinicalArea.PSYCHOLOGY, professionalId = "pro-2", professionalName = "Rodrigo Villarreal"))
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(AssignBlock.ALREADY_ASSIGNED, state.block)
        assertEquals("Rodrigo Villarreal", state.current?.professionalName)
    }

    @Test
    fun `un paciente inexistente se informa`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel(patientId = "no-existe")
        advanceUntilIdle()

        assertEquals(AssignBlock.PATIENT_NOT_FOUND, viewModel.state.value.block)
    }

    @Test
    fun `seleccionar habilita confirmar con el nombre y la razon se limita`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSelect("pro-2")
        viewModel.onReasonChange("x".repeat(500))

        val state = viewModel.state.value
        assertEquals("Rodrigo Villarreal Saldaña", state.selected?.fullName)
        assertTrue(state.canSubmit)
        assertEquals(200, state.reason.length)
    }

    @Test
    fun `confirmar crea la asignacion inicial con el actor y la razon y termina`() = runTest(mainDispatcher.dispatcher) {
        assignments.createResult = OperationResult.Success(patientAssignment(ClinicalArea.PSYCHOLOGY, professionalId = "pro-2"))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onSelect("pro-2")
        viewModel.onReasonChange("Primera asignación")

        viewModel.onConfirm()
        advanceUntilIdle()

        assertEquals(listOf<Any?>("ana", ClinicalArea.PSYCHOLOGY, "pro-2", "Primera asignación", "coord"), assignments.created.single())
        assertTrue(viewModel.state.value.isAssigned)
    }

    @Test
    fun `un doble toque no asigna dos veces`() = runTest(mainDispatcher.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        assignments.gate = gate
        assignments.createResult = OperationResult.Success(patientAssignment())
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onSelect("pro-1")

        viewModel.onConfirm()
        advanceUntilIdle()
        viewModel.onConfirm()
        viewModel.onSelect("pro-2")
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, assignments.created.size)
        assertEquals("pro-1", assignments.created.single()[2])
    }

    @Test
    fun `una asignacion que apareció mientras tanto o un permiso retirado se reportan sin crear nada`() = runTest(mainDispatcher.dispatcher) {
        assignments.createResult = OperationResult.Failure(OperationError.AssignmentAlreadyActive)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onSelect("pro-1")

        viewModel.onConfirm()
        advanceUntilIdle()

        assertEquals(AssignBlock.ALREADY_ASSIGNED, viewModel.state.value.block)
        assertFalse(viewModel.state.value.isAssigned)
    }

    @Test
    fun `un rechazo por profesional no disponible deja elegir otro`() = runTest(mainDispatcher.dispatcher) {
        assignments.createResult = OperationResult.Failure(OperationError.ProfessionalNotAvailable)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onSelect("pro-1")

        viewModel.onConfirm()
        advanceUntilIdle()

        assertEquals(WriteState.Failed(OperationError.ProfessionalNotAvailable), viewModel.state.value.write)
        assertNull(viewModel.state.value.block)
        viewModel.onSelect("pro-2")
        assertEquals(WriteState.Idle, viewModel.state.value.write)
    }

    @Test
    fun `un fallo de lectura permite reintentar`() = runTest(mainDispatcher.dispatcher) {
        assignments.failing = true
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loadFailed)

        assignments.failing = false
        viewModel.onRetryLoad()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.loadFailed)
        assertEquals(2, viewModel.state.value.professionals.size)
    }
}
