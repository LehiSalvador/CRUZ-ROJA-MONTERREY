package mx.crnl.clinica.beta.feature.encounters

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.clinical.EncounterField
import mx.crnl.clinica.beta.domain.clinical.EncounterIssue
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.EncounterDetail
import mx.crnl.clinica.beta.domain.model.EncounterFormContext
import mx.crnl.clinica.beta.domain.model.EncounterStatus
import mx.crnl.clinica.beta.domain.model.EncounterSummary
import mx.crnl.clinica.beta.domain.model.EncounterType
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.feature.appointments.form.WriteState
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.FakeEncounterRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.domainAppointment
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.patientAssignment
import mx.crnl.clinica.beta.testing.testInstant
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
class NewEncounterViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val professional = userAccount(id = "pro-1", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)
    private val encounters = FakeEncounterRepository()

    private val pastAppointment = domainAppointment(id = "c-pasada", start = testInstant(-2, 10), status = AppointmentStatus.COMPLETED, professionalId = "pro-1")
    private val laterTodayAppointment = domainAppointment(id = "c-hoy", start = testInstant(0, 16), status = AppointmentStatus.COMPLETED, professionalId = "pro-1")

    private fun context(canCreate: Boolean = true, assigned: Boolean = true) = EncounterFormContext(
        patientId = "ana",
        patientName = "Ana Lucía Cavazos Ibarra",
        patientNumber = "CRNL-000001",
        area = ClinicalArea.PSYCHOLOGY,
        assignment = if (assigned) patientAssignment(ClinicalArea.PSYCHOLOGY, professionalId = "pro-1") else null,
        linkableAppointments = listOf(laterTodayAppointment, pastAppointment),
        canCreate = canCreate,
    )

    private fun detail() = EncounterDetail(
        summary = EncounterSummary("nuevo", ClinicalArea.PSYCHOLOGY, "Mariana", EncounterType.FOLLOW_UP, EncounterStatus.COMPLETED, Instant.EPOCH),
        patientId = "ana",
        patientName = "Ana",
        patientNumber = "CRNL-000001",
        professionalId = "pro-1",
        appointmentStart = null,
        recordedAt = Instant.EPOCH,
        createdByName = "Mariana",
    )

    private fun viewModel(appointmentId: String? = null, area: String = "PSYCHOLOGY"): NewEncounterViewModel {
        val auth = FakeAuthRepository(listOf(professional to "x"), initialUserId = "pro-1")
        val route = buildMap<String, Any?> {
            put("patientId", "ana")
            put("area", area)
            if (appointmentId != null) put("appointmentId", appointmentId)
        }
        return NewEncounterViewModel(SavedStateHandle(route), auth, encounters, fixedClock())
    }

    @Test
    fun `sin cita abre con la fecha y la hora actuales de Monterrey y ningun tipo elegido`() = runTest(mainDispatcher.dispatcher) {
        encounters.context = context()
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.canFill)
        assertNull(state.type)
        assertNull(state.appointmentId)
        assertEquals("29092026", state.moment.dateDigits)
        assertEquals("1000", state.moment.timeDigits)
    }

    @Test
    fun `desde una cita pasada abre vinculado y con el momento de esa cita`() = runTest(mainDispatcher.dispatcher) {
        encounters.context = context()
        val viewModel = viewModel(appointmentId = "c-pasada")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("c-pasada", state.appointmentId)
        assertEquals("27092026", state.moment.dateDigits)
        assertEquals("1000", state.moment.timeDigits)
    }

    @Test
    fun `desde una cita que empieza mas tarde hoy la atencion se sugiere ahora y no en el futuro`() = runTest(mainDispatcher.dispatcher) {
        encounters.context = context()
        val viewModel = viewModel(appointmentId = "c-hoy")
        advanceUntilIdle()

        assertEquals("c-hoy", viewModel.state.value.appointmentId)
        assertEquals("1000", viewModel.state.value.moment.timeDigits)
    }

    @Test
    fun `una cita que no es vinculable en la ruta se ignora`() = runTest(mainDispatcher.dispatcher) {
        encounters.context = context()
        val viewModel = viewModel(appointmentId = "otra")
        advanceUntilIdle()

        assertNull(viewModel.state.value.appointmentId)
    }

    @Test
    fun `vincular y desvincular una cita cambia el momento sugerido solo al vincular`() = runTest(mainDispatcher.dispatcher) {
        encounters.context = context()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAppointmentChange("c-pasada")
        assertEquals("27092026", viewModel.state.value.moment.dateDigits)
        viewModel.onAppointmentChange(null)
        assertNull(viewModel.state.value.appointmentId)
        assertEquals("27092026", viewModel.state.value.moment.dateDigits)
    }

    @Test
    fun `sin permiso o sin profesional asignado no se puede llenar el formulario`() = runTest(mainDispatcher.dispatcher) {
        encounters.context = context(canCreate = false, assigned = false)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canFill)
        viewModel.onTypeChange(EncounterType.INITIAL)
        viewModel.onSave()
        advanceUntilIdle()
        assertTrue(encounters.created.isEmpty())
    }

    @Test
    fun `guardar sin tipo pide elegirlo y no llama al repositorio`() = runTest(mainDispatcher.dispatcher) {
        encounters.context = context()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(EncounterIssue.REQUIRED, viewModel.state.value.issues[EncounterField.TYPE])
        assertTrue(encounters.created.isEmpty())
    }

    @Test
    fun `una fecha futura o ilegible se rechaza antes de guardar`() = runTest(mainDispatcher.dispatcher) {
        encounters.context = context()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTypeChange(EncounterType.FOLLOW_UP)

        viewModel.onDateChange("15122026")
        viewModel.onSave()
        advanceUntilIdle()
        assertEquals(EncounterIssue.IN_THE_FUTURE, viewModel.state.value.issues[EncounterField.DATE])

        viewModel.onDateChange("")
        viewModel.onTimeChange("9999")
        viewModel.onSave()
        advanceUntilIdle()
        assertEquals(EncounterIssue.REQUIRED, viewModel.state.value.issues[EncounterField.DATE])
        assertEquals(EncounterIssue.INVALID, viewModel.state.value.issues[EncounterField.TIME])
        assertTrue(encounters.created.isEmpty())
    }

    @Test
    fun `guardar envia el borrador con el profesional asignado, el tipo, el momento y el actor`() = runTest(mainDispatcher.dispatcher) {
        encounters.context = context()
        encounters.createResult = OperationResult.Success(detail())
        val viewModel = viewModel(appointmentId = "c-pasada")
        advanceUntilIdle()
        viewModel.onTypeChange(EncounterType.INTERVENTION)

        viewModel.onSave()
        advanceUntilIdle()

        val (draft, actor) = encounters.created.single()
        assertEquals("pro-1", actor)
        assertEquals("ana", draft.patientId)
        assertEquals(ClinicalArea.PSYCHOLOGY, draft.area)
        assertEquals("pro-1", draft.professionalId)
        assertEquals("c-pasada", draft.appointmentId)
        assertEquals(EncounterType.INTERVENTION, draft.type)
        assertEquals(testInstant(-2, 10), draft.eventAt)
        assertEquals("ana", viewModel.state.value.savedPatientId)
        assertEquals(ClinicalArea.PSYCHOLOGY, viewModel.state.value.savedArea)
    }

    @Test
    fun `un doble toque no registra dos encuentros`() = runTest(mainDispatcher.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        encounters.gate = gate
        encounters.context = context()
        encounters.createResult = OperationResult.Success(detail())
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTypeChange(EncounterType.FOLLOW_UP)

        viewModel.onSave()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isSaving)
        viewModel.onSave()
        viewModel.onSave()
        gate.complete(Unit)
        advanceUntilIdle()
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(1, encounters.created.size)
    }

    @Test
    fun `un rechazo del repositorio se muestra y deja reintentar sin perder lo capturado`() = runTest(mainDispatcher.dispatcher) {
        encounters.context = context()
        encounters.createResult = OperationResult.Failure(OperationError.DuplicateEncounter)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTypeChange(EncounterType.CLOSURE)

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(WriteState.Failed(OperationError.DuplicateEncounter), viewModel.state.value.write)
        assertEquals(EncounterType.CLOSURE, viewModel.state.value.type)
        assertNull(viewModel.state.value.savedPatientId)
    }

    @Test
    fun `un area invalida o un fallo de lectura permiten reintentar`() = runTest(mainDispatcher.dispatcher) {
        val invalid = viewModel(area = "NO_EXISTE")
        advanceUntilIdle()
        assertTrue(invalid.state.value.loadFailed)

        encounters.context = context()
        encounters.failing = true
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loadFailed)

        encounters.failing = false
        viewModel.onRetryLoad()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canFill)
    }
}
