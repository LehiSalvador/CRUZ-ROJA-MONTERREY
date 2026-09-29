package mx.crnl.clinica.beta.feature.patients.edit

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.model.DuplicateReason
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.patient.FieldIssue
import mx.crnl.clinica.beta.domain.patient.PatientField
import mx.crnl.clinica.beta.domain.patient.PatientFormValidator
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.FakePatientRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.domainPatient
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.patientRecord
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class EditPatientViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val auth = FakeAuthRepository(listOf(userAccount(id = "mariana") to "clave"), initialUserId = "mariana")
    private val ana = patientRecord(
        domainPatient(id = "ana", number = "CRNL-000001", firstName = "Ana", paternalSurname = "Cavazos", maternalSurname = "Ibarra"),
        phone = "+528100000101",
        email = "ana.cavazos@example.org",
    )
    private val diego = patientRecord(
        domainPatient(id = "diego", number = "CRNL-000002", firstName = "Diego", paternalSurname = "Treviño", maternalSurname = "Salinas", birthDate = LocalDate.of(2003, 11, 2)),
        phone = "+528100000102",
        email = "diego.trevino@example.org",
    )
    private val patients = FakePatientRepository(listOf(ana, diego))

    private fun viewModel(patientId: String = "ana") = EditPatientViewModel(
        SavedStateHandle(mapOf("patientId" to patientId)),
        auth,
        patients,
        PatientFormValidator(fixedClock()),
    )

    private val EditPatientViewModel.current get() = state.value

    @Test
    fun `carga los datos editables del paciente`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val input = viewModel.current.form.input
        assertFalse(viewModel.current.isLoading)
        assertEquals("Ana", input.firstName)
        assertEquals("Cavazos", input.paternalSurname)
        assertEquals("Ibarra", input.maternalSurname)
        assertEquals("14051998", input.birthDate)
        assertEquals("+528100000101", input.phone)
        assertEquals("ana.cavazos@example.org", input.email)
        assertFalse(viewModel.current.hasChanges)
    }

    @Test
    fun `un paciente inexistente se informa y un fallo de lectura permite reintentar`() = runTest(mainDispatcher.dispatcher) {
        val missing = viewModel("no-existe")
        advanceUntilIdle()
        assertTrue(missing.current.notFound)

        patients.failing = true
        val failing = viewModel()
        advanceUntilIdle()
        assertTrue(failing.current.loadFailed)

        patients.failing = false
        failing.onRetryLoad()
        advanceUntilIdle()

        assertFalse(failing.current.loadFailed)
        assertEquals("Ana", failing.current.form.input.firstName)
    }

    @Test
    fun `guardar sin cambios termina sin escribir nada`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(viewModel.current.isSaved)
        assertTrue(patients.updated.isEmpty())
    }

    @Test
    fun `un cambio que no toca la identidad guarda sin buscar duplicados`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onTextChange(PatientField.MUNICIPALITY, "Guadalupe")
        assertTrue(viewModel.current.hasChanges)
        viewModel.onSave()
        advanceUntilIdle()

        val (id, draft, actor) = patients.updated.single()
        assertEquals("ana", id)
        assertEquals("Guadalupe", draft.municipality)
        assertEquals("mariana", actor)
        assertTrue(viewModel.current.isSaved)
        assertTrue(patients.duplicateLookups.isEmpty())
    }

    @Test
    fun `el propio paciente nunca cuenta como duplicado al cambiar su nombre`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onTextChange(PatientField.FIRST_NAME, "Ana Lucía")
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(listOf("ana"), patients.duplicateLookups.map { it.second })
        assertNull(viewModel.current.duplicateWarning)
        assertTrue(viewModel.current.isSaved)
        assertEquals("Ana Lucía", patients.updated.single().second.firstName)
    }

    @Test
    fun `un cambio que coincide con otro paciente advierte y no guarda hasta confirmar`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onTextChange(PatientField.EMAIL, "DIEGO.TREVINO@example.org")
        viewModel.onSave()
        advanceUntilIdle()

        val warning = viewModel.current.duplicateWarning!!
        assertEquals("diego", warning.single().patient.patientId)
        assertEquals(setOf(DuplicateReason.SAME_EMAIL), warning.single().reasons)
        assertTrue("todavía no se escribe nada", patients.updated.isEmpty())
        assertFalse(viewModel.current.isSaving)

        viewModel.onConfirmDuplicates()
        advanceUntilIdle()

        assertEquals("diego.trevino@example.org", patients.updated.single().second.email)
        assertNull(viewModel.current.duplicateWarning)
        assertTrue(viewModel.current.isSaved)
    }

    @Test
    fun `descartar la advertencia conserva la edicion sin guardar`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTextChange(PatientField.PHONE, "+528100000102")
        viewModel.onSave()
        advanceUntilIdle()
        assertNotNull(viewModel.current.duplicateWarning)

        viewModel.onDismissDuplicates()

        assertNull(viewModel.current.duplicateWarning)
        assertEquals("+528100000102", viewModel.current.form.input.phone)
        assertTrue(patients.updated.isEmpty())
        assertFalse(viewModel.current.isSaved)
    }

    @Test
    fun `no se guarda con datos invalidos`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onTextChange(PatientField.FIRST_NAME, "   ")
        viewModel.onTextChange(PatientField.EMAIL, "correo-roto")
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(FieldIssue.REQUIRED, viewModel.current.form.issues[PatientField.FIRST_NAME])
        assertEquals(FieldIssue.INVALID, viewModel.current.form.issues[PatientField.EMAIL])
        assertTrue(patients.updated.isEmpty())
        assertFalse(viewModel.current.isSaved)
    }

    @Test
    fun `guardar dos veces seguidas escribe una sola vez`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTextChange(PatientField.MUNICIPALITY, "Apodaca")

        viewModel.onSave()
        viewModel.onSave()
        advanceUntilIdle()
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(1, patients.updated.size)
    }

    @Test
    fun `un fallo al guardar se informa y reintentar termina la edicion`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTextChange(PatientField.MUNICIPALITY, "Apodaca")
        patients.failingWrites = true

        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(viewModel.current.saveFailed)
        assertFalse(viewModel.current.isSaved)
        assertFalse(viewModel.current.isSaving)

        patients.failingWrites = false
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(viewModel.current.isSaved)
        assertEquals(1, patients.updated.size)
    }

    @Test
    fun `la poblacion y el sexo tambien se pueden modificar`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onPopulationTypeChange(PopulationType.EMPLOYEE)
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(PopulationType.EMPLOYEE, patients.updated.single().second.populationType)
    }
}
