package mx.crnl.clinica.beta.feature.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.account.AccountRequestField
import mx.crnl.clinica.beta.domain.account.AccountRequestIssue
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RequestAccountViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val existing = userAccount(id = "existente", email = "existente@example.org")
    private val auth = FakeAuthRepository(listOf(existing to "clave-existente"))
    private val viewModel = RequestAccountViewModel(auth)

    private fun fillValidForm(email: String = "nueva.persona@example.org") {
        viewModel.onTextChange(AccountRequestField.FIRST_NAME, "Nueva")
        viewModel.onTextChange(AccountRequestField.PATERNAL_SURNAME, "Persona")
        viewModel.onTextChange(AccountRequestField.EMAIL, email)
        viewModel.onTextChange(AccountRequestField.PASSWORD, "clave-segura-1")
        viewModel.onTextChange(AccountRequestField.PASSWORD_CONFIRMATION, "clave-segura-1")
        viewModel.onAreaChange(ClinicalArea.NUTRITION)
        viewModel.onRoleChange(UserRole.PROFESSIONAL)
        viewModel.onTextChange(AccountRequestField.LICENSE, "12345678")
    }

    @Test
    fun `una solicitud completa queda pendiente de aprobacion y no inicia sesion`() = runTest(mainDispatcher.dispatcher) {
        fillValidForm()

        viewModel.onSubmit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isSubmitted)
        val created = auth.users.value.single { it.email == "nueva.persona@example.org" }
        assertEquals(AccountStatus.PENDING_APPROVAL, created.status)
        assertEquals(UserRole.PROFESSIONAL, created.role)
        assertNull("no debe haber sesión", auth.currentUser.first())
    }

    @Test
    fun `al enviar se descartan las contrasenas capturadas`() = runTest(mainDispatcher.dispatcher) {
        fillValidForm()

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.form.password)
        assertEquals("", viewModel.state.value.form.passwordConfirmation)
    }

    @Test
    fun `un formulario vacio muestra los campos obligatorios sin consultar al repositorio`() = runTest(mainDispatcher.dispatcher) {
        viewModel.onSubmit()
        advanceUntilIdle()

        val issues = viewModel.state.value.issues
        assertEquals(AccountRequestIssue.REQUIRED, issues[AccountRequestField.FIRST_NAME])
        assertEquals(AccountRequestIssue.REQUIRED, issues[AccountRequestField.EMAIL])
        assertEquals(AccountRequestIssue.REQUIRED, issues[AccountRequestField.PASSWORD])
        assertEquals(AccountRequestIssue.REQUIRED, issues[AccountRequestField.AREA])
        assertEquals(AccountRequestIssue.REQUIRED, issues[AccountRequestField.ROLE])
        assertTrue(auth.submittedRequests.isEmpty())
    }

    @Test
    fun `una confirmacion distinta se marca junto al campo`() = runTest(mainDispatcher.dispatcher) {
        fillValidForm()
        viewModel.onTextChange(AccountRequestField.PASSWORD_CONFIRMATION, "otra-clave")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(AccountRequestIssue.MISMATCH, viewModel.state.value.issues[AccountRequestField.PASSWORD_CONFIRMATION])
        assertFalse(viewModel.state.value.isSubmitted)
    }

    @Test
    fun `un correo ya registrado se rechaza sin sobrescribir la cuenta existente`() = runTest(mainDispatcher.dispatcher) {
        fillValidForm(email = "EXISTENTE@example.org")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.emailTaken)
        assertFalse(viewModel.state.value.isSubmitted)
        assertEquals(existing, auth.users.value.single())
    }

    @Test
    fun `editar el correo retira el aviso de correo repetido`() = runTest(mainDispatcher.dispatcher) {
        fillValidForm(email = "existente@example.org")
        viewModel.onSubmit()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.emailTaken)

        viewModel.onTextChange(AccountRequestField.EMAIL, "otro@example.org")

        assertFalse(viewModel.state.value.emailTaken)
    }

    @Test
    fun `un profesional necesita cedula y un coordinador no`() = runTest(mainDispatcher.dispatcher) {
        fillValidForm()
        viewModel.onTextChange(AccountRequestField.LICENSE, "")
        viewModel.onSubmit()
        assertEquals(AccountRequestIssue.REQUIRED, viewModel.state.value.issues[AccountRequestField.LICENSE])

        viewModel.onRoleChange(UserRole.AREA_COORDINATOR)
        viewModel.onSubmit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isSubmitted)
    }

    @Test
    fun `no se puede solicitar un rol administrativo`() = runTest(mainDispatcher.dispatcher) {
        fillValidForm()
        viewModel.onRoleChange(UserRole.SYSTEM_ADMIN)

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(AccountRequestIssue.INVALID, viewModel.state.value.issues[AccountRequestField.ROLE])
        assertTrue(auth.submittedRequests.isEmpty())
    }

    @Test
    fun `enviar dos veces seguidas crea una sola solicitud`() = runTest(mainDispatcher.dispatcher) {
        fillValidForm()

        viewModel.onSubmit()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, auth.submittedRequests.size)
    }
}
