package mx.crnl.clinica.beta.feature.appointments.form

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
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
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.ScheduleConflict
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.FakeAppointmentRepository
import mx.crnl.clinica.beta.testing.FakeAssignmentRepository
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.FakePatientRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.domainAppointment
import mx.crnl.clinica.beta.testing.domainPatient
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.patientAssignment
import mx.crnl.clinica.beta.testing.patientRecord
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
class NewAppointmentViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val coordinator = userAccount(id = "coord", role = UserRole.AREA_COORDINATOR, area = ClinicalArea.PSYCHOLOGY)
    private val professional = userAccount(id = "pro-1", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)
    private val admin = userAccount(id = "admin", role = UserRole.CLINICAL_ADMIN, area = null)
    private val systemAdmin = userAccount(id = "sys", role = UserRole.SYSTEM_ADMIN, area = null)

    private val ana = domainPatient(id = "ana", number = "CRNL-000001", firstName = "Ana", paternalSurname = "Cavazos")
    private val inactive = domainPatient(id = "baja", number = "CRNL-000009", firstName = "Ana Baja", paternalSurname = "Inactiva", status = PatientStatus.INACTIVE)
    private val patients = FakePatientRepository(listOf(patientRecord(ana), patientRecord(inactive)))
    private val assignments = FakeAssignmentRepository().apply { active.value = listOf(patientAssignment(ClinicalArea.PSYCHOLOGY, professionalId = "pro-1")) }
    private val appointments = FakeAppointmentRepository()

    private fun viewModel(user: UserAccount = coordinator, route: Map<String, Any?> = mapOf("patientId" to "ana")): NewAppointmentViewModel {
        val auth = FakeAuthRepository(listOf(user to "x"), initialUserId = user.userId)
        return NewAppointmentViewModel(SavedStateHandle(route), auth, patients, assignments, appointments, fixedClock())
    }

    private fun NewAppointmentViewModel.fillValid() {
        onDateChange("30092026")
        onTimeChange("1100")
        onLocationChange("Consultorio 3")
    }

    private fun createdDetail(id: String = "nueva") = AppointmentDetail(
        summary = domainAppointment(id = id),
        patientFirstName = "Ana",
        contactPhone = null,
        meetingUrl = null,
        administrativeNotes = null,
        createdByName = "Claudia",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        encounters = emptyList(),
    )

    // ---------------------------------------------------------------- carga y selección

    @Test
    fun `desde el expediente abre con el paciente fijo, el area unica elegida y el profesional asignado`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("ana", state.patient?.patientId)
        assertTrue(state.patientLocked)
        assertEquals(listOf(ClinicalArea.PSYCHOLOGY), state.areaOptions)
        assertEquals(ClinicalArea.PSYCHOLOGY, state.area)
        val lookup = state.lookup as AssignmentLookup.Assigned
        assertEquals("pro-1", lookup.assignment.professionalId)
    }

    @Test
    fun `un area de la ruta que la persona no puede gestionar se ignora`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel(route = mapOf("patientId" to "ana", "area" to "NUTRITION"))
        advanceUntilIdle()

        assertEquals(ClinicalArea.PSYCHOLOGY, viewModel.state.value.area)
    }

    @Test
    fun `administracion clinica elige entre las tres areas y el area de la ruta viene preseleccionada`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel(admin, mapOf("patientId" to "ana", "area" to "PSYCHOLOGY"))
        advanceUntilIdle()

        assertEquals(ClinicalArea.entries, viewModel.state.value.areaOptions)
        assertEquals(ClinicalArea.PSYCHOLOGY, viewModel.state.value.area)
    }

    @Test
    fun `el administrador del sistema no tiene areas y no puede agendar`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel(systemAdmin)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.areaOptions.isEmpty())
        viewModel.fillValid()
        viewModel.onSave()
        advanceUntilIdle()
        assertTrue(appointments.created.isEmpty())
    }

    @Test
    fun `sin paciente en la ruta se busca por nombre o folio, solo activos, y se selecciona`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel(route = emptyMap())
        advanceUntilIdle()
        assertNull(viewModel.state.value.patient)

        viewModel.onQueryChange("ana")
        advanceUntilIdle()
        assertEquals(listOf("ana"), viewModel.state.value.results.map { it.patientId })

        viewModel.onPatientSelected(viewModel.state.value.results.single())
        advanceUntilIdle()
        assertEquals("ana", viewModel.state.value.patient?.patientId)
        assertFalse(viewModel.state.value.patientLocked)
        assertTrue(viewModel.state.value.lookup is AssignmentLookup.Assigned)

        viewModel.onPatientCleared()
        assertNull(viewModel.state.value.patient)
        assertTrue(viewModel.state.value.lookup is AssignmentLookup.NotChosen)
    }

    @Test
    fun `un paciente fijado desde el expediente no se puede cambiar`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onPatientCleared()

        assertEquals("ana", viewModel.state.value.patient?.patientId)
    }

    @Test
    fun `sin profesional asignado se informa, no se crea asignacion y coordinacion puede asignar`() = runTest(mainDispatcher.dispatcher) {
        assignments.active.value = emptyList()
        val viewModel = viewModel()
        advanceUntilIdle()

        val lookup = viewModel.state.value.lookup as AssignmentLookup.Missing
        assertTrue(lookup.canAssign)
        viewModel.fillValid()
        viewModel.onSave()
        advanceUntilIdle()
        assertTrue(appointments.created.isEmpty())
        assertTrue(assignments.created.isEmpty())
    }

    @Test
    fun `un profesional sin asignacion no puede asignar y se le indica pedirlo a coordinacion`() = runTest(mainDispatcher.dispatcher) {
        assignments.active.value = emptyList()
        val viewModel = viewModel(professional)
        advanceUntilIdle()

        assertFalse((viewModel.state.value.lookup as AssignmentLookup.Missing).canAssign)
    }

    // ---------------------------------------------------------------- guardar

    @Test
    fun `guardar con el formulario vacio senala cada campo y no llama al repositorio`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel(route = emptyMap())
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(AppointmentIssue.REQUIRED, state.selectionIssues[AppointmentField.PATIENT])
        assertEquals(AppointmentIssue.REQUIRED, state.schedule.issues[AppointmentField.DATE])
        assertEquals(AppointmentIssue.REQUIRED, state.schedule.issues[AppointmentField.TIME])
        assertEquals(AppointmentIssue.REQUIRED, state.logistics.issues[AppointmentField.LOCATION])
        assertTrue(appointments.created.isEmpty())
    }

    @Test
    fun `una fecha en el pasado se rechaza antes de llamar al repositorio`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onDateChange("01092026")
        viewModel.onTimeChange("0900")
        viewModel.onLocationChange("Consultorio 3")

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(AppointmentIssue.IN_THE_PAST, viewModel.state.value.schedule.issues[AppointmentField.DATE])
        assertTrue(appointments.created.isEmpty())
    }

    @Test
    fun `guardar envia el borrador con el profesional asignado, el horario de Monterrey y el actor`() = runTest(mainDispatcher.dispatcher) {
        appointments.createResult = { OperationResult.Success(createdDetail("cita-1")) }
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.fillValid()
        viewModel.onDurationChange(45)
        viewModel.onNotesChange("Traer identificación")

        viewModel.onSave()
        advanceUntilIdle()

        val (draft, actor) = appointments.created.single()
        assertEquals("coord", actor)
        assertEquals("ana", draft.patientId)
        assertEquals(ClinicalArea.PSYCHOLOGY, draft.area)
        assertEquals("pro-1", draft.professionalId)
        assertEquals(testInstant(1, 11), draft.start)
        assertEquals(45, draft.durationMinutes)
        assertEquals(AppointmentModality.IN_PERSON, draft.modality)
        assertEquals("Consultorio 3", draft.location)
        assertEquals("Traer identificación", draft.administrativeNotes)
        assertEquals("cita-1", viewModel.state.value.createdAppointmentId)
    }

    @Test
    fun `un doble toque mientras guarda no crea dos citas`() = runTest(mainDispatcher.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        appointments.gate = gate
        appointments.createResult = { OperationResult.Success(createdDetail()) }
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.fillValid()

        viewModel.onSave()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isSaving)
        viewModel.onSave()
        viewModel.onSave()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, appointments.created.size)
        // Una vez creada, tampoco se vuelve a guardar por recomposición o toque tardío.
        viewModel.onSave()
        advanceUntilIdle()
        assertEquals(1, appointments.created.size)
    }

    @Test
    fun `un conflicto de horario se muestra con la cita en conflicto y no navega`() = runTest(mainDispatcher.dispatcher) {
        val conflict = ScheduleConflict(ConflictKind.PROFESSIONAL, testInstant(1, 10), testInstant(1, 11), "otra", "Diego Treviño", "Mariana")
        appointments.createResult = { OperationResult.Failure(OperationError.ScheduleConflicts(listOf(conflict))) }
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.fillValid()

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(listOf(conflict), viewModel.state.value.conflicts)
        assertNull(viewModel.state.value.createdAppointmentId)
        assertFalse(viewModel.state.value.isSaving)
        // Mover el horario limpia el aviso y permite reintentar; lo capturado se conserva.
        viewModel.onTimeChange("1200")
        assertTrue(viewModel.state.value.conflicts.isEmpty())
        assertEquals("Consultorio 3", viewModel.state.value.logistics.location)
    }

    @Test
    fun `los problemas que devuelve el repositorio se pintan junto a su campo`() = runTest(mainDispatcher.dispatcher) {
        appointments.createResult = {
            OperationResult.Failure(
                OperationError.InvalidAppointment(mapOf(AppointmentField.DURATION to AppointmentIssue.TOO_LONG, AppointmentField.LOCATION to AppointmentIssue.TOO_LONG)),
            )
        }
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.fillValid()

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(AppointmentIssue.TOO_LONG, viewModel.state.value.schedule.issues[AppointmentField.DURATION])
        assertEquals(AppointmentIssue.TOO_LONG, viewModel.state.value.logistics.issues[AppointmentField.LOCATION])
    }

    @Test
    fun `una asignacion que desaparecio mientras se capturaba se reporta como sin profesional`() = runTest(mainDispatcher.dispatcher) {
        appointments.createResult = { OperationResult.Failure(OperationError.NoActiveAssignment) }
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.fillValid()

        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.lookup is AssignmentLookup.Missing)
    }

    @Test
    fun `un rechazo por permisos o un fallo inesperado deja el formulario intacto y permite reintentar`() = runTest(mainDispatcher.dispatcher) {
        appointments.createResult = { OperationResult.Failure(OperationError.NotAuthorized) }
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.fillValid()

        viewModel.onSave()
        advanceUntilIdle()
        assertEquals(WriteState.Failed(OperationError.NotAuthorized), viewModel.state.value.write)
        assertEquals("Consultorio 3", viewModel.state.value.logistics.location)

        appointments.createResult = { throw IOException("disco lleno") }
        viewModel.onSave()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.write is WriteState.Unavailable)

        appointments.createResult = { OperationResult.Success(createdDetail()) }
        viewModel.onSave()
        advanceUntilIdle()
        assertEquals("nueva", viewModel.state.value.createdAppointmentId)
    }

    @Test
    fun `cambiar a en linea conserva el resto y no pide ubicacion`() = runTest(mainDispatcher.dispatcher) {
        appointments.createResult = { OperationResult.Success(createdDetail()) }
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onDateChange("30092026")
        viewModel.onTimeChange("1100")
        viewModel.onModalityChange(AppointmentModality.ONLINE)
        viewModel.onMeetingUrlChange("https://meet.example.org/abc")

        viewModel.onSave()
        advanceUntilIdle()

        val draft = appointments.created.single().first
        assertEquals(AppointmentModality.ONLINE, draft.modality)
        assertEquals("https://meet.example.org/abc", draft.meetingUrl)
    }

    @Test
    fun `hasChanges avisa solo cuando hay algo capturado que se perderia`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.hasChanges)

        viewModel.onNotesChange("algo")
        assertTrue(viewModel.state.value.hasChanges)
    }
}
