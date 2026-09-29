package mx.crnl.clinica.beta.feature.patients.create

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.model.DuplicateReason
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex
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

@OptIn(ExperimentalCoroutinesApi::class)
class NewPatientViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val user = userAccount(id = "mariana")
    private val auth = FakeAuthRepository(listOf(user to "clave"), initialUserId = "mariana")
    private val existing = patientRecord(
        domainPatient(id = "existente", number = "CRNL-000008", firstName = "Beatriz", paternalSurname = "Lozano", maternalSurname = "Garza", birthDate = LocalDate.of(1995, 3, 8)),
        email = "beatriz.lozano@example.org",
    )
    private val patients = FakePatientRepository(listOf(existing))
    private val viewModel = NewPatientViewModel(auth, patients, PatientFormValidator(fixedClock()))

    private fun fillIdentity(firstName: String = "Camila", paternal: String = "Ríos", birthDate: String = "12071990") {
        viewModel.onTextChange(PatientField.FIRST_NAME, firstName)
        viewModel.onTextChange(PatientField.PATERNAL_SURNAME, paternal)
        viewModel.onTextChange(PatientField.MATERNAL_SURNAME, "Soto")
        viewModel.onTextChange(PatientField.BIRTH_DATE, birthDate)
        viewModel.onSexChange(Sex.FEMALE)
    }

    private fun fillContact(phone: String = "81 2345 6789", email: String = "camila.rios@example.org") {
        viewModel.onTextChange(PatientField.PHONE, phone)
        viewModel.onTextChange(PatientField.EMAIL, email)
        viewModel.onTextChange(PatientField.MUNICIPALITY, "Monterrey")
    }

    private fun goToConsent() {
        fillIdentity()
        viewModel.onNext()
        fillContact()
        viewModel.onNext()
        viewModel.onPopulationTypeChange(PopulationType.STUDENT)
        viewModel.onNext()
    }

    private val state get() = viewModel.state.value

    @Test
    fun `cada paso valida sus campos antes de avanzar`() = runTest(mainDispatcher.dispatcher) {
        viewModel.onNext()

        assertEquals(NewPatientStep.IDENTITY, state.step)
        assertEquals(FieldIssue.REQUIRED, state.form.issues[PatientField.FIRST_NAME])
        assertEquals(FieldIssue.REQUIRED, state.form.issues[PatientField.PATERNAL_SURNAME])
        assertEquals(FieldIssue.REQUIRED, state.form.issues[PatientField.BIRTH_DATE])
        assertEquals(FieldIssue.REQUIRED, state.form.issues[PatientField.SEX])
        assertNull("el paso siguiente aún no se valida", state.form.issues[PatientField.POPULATION_TYPE])

        fillIdentity()
        viewModel.onNext()
        assertEquals(NewPatientStep.CONTACT, state.step)

        fillContact(phone = "123", email = "no-es-correo")
        viewModel.onNext()
        assertEquals(NewPatientStep.CONTACT, state.step)
        assertEquals(FieldIssue.INVALID, state.form.issues[PatientField.PHONE])
        assertEquals(FieldIssue.INVALID, state.form.issues[PatientField.EMAIL])

        fillContact()
        viewModel.onNext()
        assertEquals(NewPatientStep.POPULATION, state.step)

        viewModel.onNext()
        assertEquals(FieldIssue.REQUIRED, state.form.issues[PatientField.POPULATION_TYPE])
        viewModel.onPopulationTypeChange(PopulationType.STUDENT)
        viewModel.onNext()
        assertEquals(NewPatientStep.CONSENT, state.step)
    }

    @Test
    fun `editar un campo retira su error`() = runTest(mainDispatcher.dispatcher) {
        viewModel.onNext()
        assertNotNull(state.form.issues[PatientField.FIRST_NAME])

        viewModel.onTextChange(PatientField.FIRST_NAME, "Camila")

        assertNull(state.form.issues[PatientField.FIRST_NAME])
        assertNotNull(state.form.issues[PatientField.PATERNAL_SURNAME])
    }

    @Test
    fun `una fecha futura o inexistente no permite continuar`() = runTest(mainDispatcher.dispatcher) {
        fillIdentity(birthDate = "01012031")
        viewModel.onNext()
        assertEquals(FieldIssue.FUTURE_DATE, state.form.issues[PatientField.BIRTH_DATE])

        viewModel.onTextChange(PatientField.BIRTH_DATE, "31022000")
        viewModel.onNext()
        assertEquals(FieldIssue.INVALID, state.form.issues[PatientField.BIRTH_DATE])
        assertEquals(NewPatientStep.IDENTITY, state.step)
    }

    @Test
    fun `el paso de consentimientos no registra ninguna aceptacion y deja continuar`() = runTest(mainDispatcher.dispatcher) {
        goToConsent()
        assertEquals(NewPatientStep.CONSENT, state.step)

        viewModel.onNext()
        advanceUntilIdle()

        assertEquals(NewPatientStep.DUPLICATES, state.step)
        assertTrue(patients.created.isEmpty())
    }

    @Test
    fun `sin coincidencias llega a la confirmacion y guarda con el usuario autenticado`() = runTest(mainDispatcher.dispatcher) {
        goToConsent()
        viewModel.onNext()
        advanceUntilIdle()
        assertEquals(DuplicateCheck.Clear, state.duplicates)

        viewModel.onNext()
        assertEquals(NewPatientStep.CONFIRMATION, state.step)
        assertTrue("todavía no se guarda nada", patients.created.isEmpty())

        viewModel.onNext()
        advanceUntilIdle()

        val (draft, actor) = patients.created.single()
        assertEquals("mariana", actor)
        assertEquals("Camila", draft.firstName)
        assertEquals("Ríos", draft.paternalSurname)
        assertEquals("Soto", draft.maternalSurname)
        assertEquals(LocalDate.of(1990, 7, 12), draft.birthDate)
        assertEquals("81 2345 6789", draft.phone)
        assertEquals("camila.rios@example.org", draft.email)
        assertEquals(PopulationType.STUDENT, draft.populationType)
        assertEquals(CreatedPatient("created-1", "CRNL-000009"), state.created)
    }

    @Test
    fun `con coincidencias no avanza solo y crear de todos modos exige la accion explicita`() = runTest(mainDispatcher.dispatcher) {
        fillIdentity(firstName = "Beatriz", paternal = "Lozano", birthDate = "08031995")
        viewModel.onTextChange(PatientField.MATERNAL_SURNAME, "Garza")
        viewModel.onNext()
        fillContact(email = "otro.correo@example.org")
        viewModel.onNext()
        viewModel.onPopulationTypeChange(PopulationType.GENERAL_PUBLIC)
        viewModel.onNext()
        viewModel.onNext()
        advanceUntilIdle()

        val found = state.duplicates as DuplicateCheck.Found
        assertEquals("existente", found.candidates.single().patient.patientId)
        assertEquals(setOf(DuplicateReason.SAME_NAME_AND_BIRTH_DATE), found.candidates.single().reasons)

        viewModel.onNext()
        assertEquals("continuar no basta con coincidencias", NewPatientStep.DUPLICATES, state.step)
        assertTrue(patients.created.isEmpty())

        viewModel.onCreateAnyway()
        assertEquals(NewPatientStep.CONFIRMATION, state.step)
        assertTrue("confirmar el aviso tampoco guarda", patients.created.isEmpty())

        viewModel.onNext()
        advanceUntilIdle()
        assertEquals(1, patients.created.size)
        assertNotNull(state.created)
    }

    @Test
    fun `una coincidencia por correo se advierte aunque el nombre sea distinto`() = runTest(mainDispatcher.dispatcher) {
        fillIdentity()
        viewModel.onNext()
        fillContact(email = "BEATRIZ.LOZANO@example.org")
        viewModel.onNext()
        viewModel.onPopulationTypeChange(PopulationType.OTHER)
        viewModel.onNext()
        viewModel.onNext()
        advanceUntilIdle()

        val found = state.duplicates as DuplicateCheck.Found
        assertEquals(setOf(DuplicateReason.SAME_EMAIL), found.candidates.single().reasons)
    }

    @Test
    fun `volver a editar y retroceder conservan lo capturado y la revision se repite`() = runTest(mainDispatcher.dispatcher) {
        fillIdentity(firstName = "Beatriz", paternal = "Lozano", birthDate = "08031995")
        viewModel.onNext()
        fillContact(email = "beatriz.lozano@example.org")
        viewModel.onNext()
        viewModel.onPopulationTypeChange(PopulationType.OTHER)
        viewModel.onNext()
        viewModel.onNext()
        advanceUntilIdle()
        assertTrue(state.duplicates is DuplicateCheck.Found)

        viewModel.onEditData()
        assertEquals(NewPatientStep.IDENTITY, state.step)
        assertEquals("Beatriz", state.form.input.firstName)
        assertEquals("beatriz.lozano@example.org", state.form.input.email)

        viewModel.onTextChange(PatientField.FIRST_NAME, "Camila")
        viewModel.onTextChange(PatientField.PATERNAL_SURNAME, "Ríos")
        viewModel.onNext()
        viewModel.onTextChange(PatientField.EMAIL, "camila.rios@example.org")
        viewModel.onNext()
        viewModel.onNext()
        viewModel.onNext()
        advanceUntilIdle()

        assertEquals(DuplicateCheck.Clear, state.duplicates)
    }

    @Test
    fun `retroceder desde un paso conserva los datos de los pasos anteriores`() = runTest(mainDispatcher.dispatcher) {
        fillIdentity()
        viewModel.onNext()
        fillContact()
        viewModel.onNext()
        assertEquals(NewPatientStep.POPULATION, state.step)

        viewModel.onBack()
        assertEquals(NewPatientStep.CONTACT, state.step)
        assertEquals("81 2345 6789", state.form.input.phone)
        viewModel.onBack()
        assertEquals(NewPatientStep.IDENTITY, state.step)
        assertEquals("Camila", state.form.input.firstName)
        viewModel.onBack()
        assertEquals("no hay paso anterior al primero", NewPatientStep.IDENTITY, state.step)
    }

    @Test
    fun `enviar dos veces en la confirmacion crea un solo paciente`() = runTest(mainDispatcher.dispatcher) {
        goToConsent()
        viewModel.onNext()
        advanceUntilIdle()
        viewModel.onNext()

        viewModel.onNext()
        viewModel.onNext()
        advanceUntilIdle()
        viewModel.onNext()
        advanceUntilIdle()

        assertEquals(1, patients.created.size)
    }

    @Test
    fun `un fallo al guardar se informa y reintentar no duplica el alta`() = runTest(mainDispatcher.dispatcher) {
        goToConsent()
        viewModel.onNext()
        advanceUntilIdle()
        viewModel.onNext()
        patients.failingWrites = true

        viewModel.onNext()
        advanceUntilIdle()

        assertTrue(state.saveFailed)
        assertFalse(state.isSaving)
        assertNull(state.created)
        assertTrue(patients.created.isEmpty())

        patients.failingWrites = false
        viewModel.onNext()
        advanceUntilIdle()

        assertEquals(1, patients.created.size)
        assertNotNull(state.created)
        assertFalse(state.saveFailed)
    }

    @Test
    fun `un fallo al revisar coincidencias permite reintentar`() = runTest(mainDispatcher.dispatcher) {
        goToConsent()
        patients.duplicateLookupFailure = IOException("sin lectura")
        viewModel.onNext()
        advanceUntilIdle()
        assertEquals(DuplicateCheck.Failed, state.duplicates)

        patients.duplicateLookupFailure = null
        viewModel.onRetryDuplicateCheck()
        advanceUntilIdle()

        assertEquals(DuplicateCheck.Clear, state.duplicates)
    }

    @Test
    fun `el alta se marca con avance solo cuando hay algo capturado`() = runTest(mainDispatcher.dispatcher) {
        assertFalse(state.hasProgress)

        viewModel.onTextChange(PatientField.FIRST_NAME, "Camila")

        assertTrue(state.hasProgress)
    }

    @Test
    fun `una vez creado el paciente los controles ya no lo modifican`() = runTest(mainDispatcher.dispatcher) {
        goToConsent()
        viewModel.onNext()
        advanceUntilIdle()
        viewModel.onNext()
        viewModel.onNext()
        advanceUntilIdle()
        val created = state.created

        viewModel.onBack()
        viewModel.onNext()
        advanceUntilIdle()

        assertEquals(created, state.created)
        assertEquals(NewPatientStep.CONFIRMATION, state.step)
        assertEquals(1, patients.created.size)
    }
}
