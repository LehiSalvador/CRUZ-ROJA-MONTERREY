package mx.crnl.clinica.beta.feature.auth

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.model.AccountStatus
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
class LoginViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val active = userAccount(id = "active", email = "activa@example.org")
    private val auth = FakeAuthRepository(
        listOf(
            active to PASSWORD,
            userAccount(id = "pending", email = "pendiente@example.org", status = AccountStatus.PENDING_APPROVAL) to PASSWORD,
            userAccount(id = "suspended", email = "suspendida@example.org", status = AccountStatus.SUSPENDED) to PASSWORD,
            userAccount(id = "rejected", email = "rechazada@example.org", status = AccountStatus.REJECTED) to PASSWORD,
            userAccount(id = "inactive", email = "inactiva@example.org", status = AccountStatus.INACTIVE) to PASSWORD,
        ),
    )
    private val viewModel = LoginViewModel(auth)

    private fun submit(email: String, password: String) {
        viewModel.onEmailChange(email)
        viewModel.onPasswordChange(password)
        viewModel.onSubmit()
    }

    @Test
    fun `credenciales correctas de una cuenta activa inician la sesion`() = runTest(mainDispatcher.dispatcher) {
        submit("activa@example.org", PASSWORD)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isSessionStarted)
        assertNull(state.error)
        assertEquals("la contraseña no se conserva en el estado", "", state.password)
        assertEquals(active, auth.currentUser.first())
    }

    @Test
    fun `una contrasena incorrecta se rechaza sin iniciar sesion`() = runTest(mainDispatcher.dispatcher) {
        submit("activa@example.org", "otra-contrasena")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSessionStarted)
        assertEquals(LoginError.InvalidCredentials, viewModel.state.value.error)
        assertNull(auth.currentUser.first())
    }

    @Test
    fun `un correo inexistente recibe el mismo rechazo que una contrasena incorrecta`() = runTest(mainDispatcher.dispatcher) {
        submit("nadie@example.org", PASSWORD)
        advanceUntilIdle()

        assertEquals(LoginError.InvalidCredentials, viewModel.state.value.error)
        assertNull(auth.currentUser.first())
    }

    @Test
    fun `cada estado que no puede entrar se informa con su propio rechazo`() = runTest(mainDispatcher.dispatcher) {
        mapOf(
            "pendiente@example.org" to AccountStatus.PENDING_APPROVAL,
            "suspendida@example.org" to AccountStatus.SUSPENDED,
            "rechazada@example.org" to AccountStatus.REJECTED,
            "inactiva@example.org" to AccountStatus.INACTIVE,
        ).forEach { (email, status) ->
            submit(email, PASSWORD)
            advanceUntilIdle()

            assertEquals(LoginError.NotActive(status), viewModel.state.value.error)
            assertFalse(viewModel.state.value.isSessionStarted)
            assertNull("$status no debe abrir sesión", auth.currentUser.first())
        }
    }

    @Test
    fun `sin correo o sin contrasena no se consulta al repositorio`() = runTest(mainDispatcher.dispatcher) {
        submit("", PASSWORD)
        assertEquals(LoginError.MissingFields, viewModel.state.value.error)

        submit("activa@example.org", "")
        advanceUntilIdle()

        assertEquals(LoginError.MissingFields, viewModel.state.value.error)
        assertTrue(auth.signInAttempts.isEmpty())
    }

    @Test
    fun `un fallo del almacenamiento se informa y permite reintentar`() = runTest(mainDispatcher.dispatcher) {
        auth.signInFailure = IOException("disco lleno")
        submit("activa@example.org", PASSWORD)
        advanceUntilIdle()

        assertEquals(LoginError.Unavailable, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSubmitting)

        auth.signInFailure = null
        viewModel.onSubmit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isSessionStarted)
    }

    @Test
    fun `editar un campo retira el mensaje de error`() = runTest(mainDispatcher.dispatcher) {
        submit("activa@example.org", "mal")
        advanceUntilIdle()
        assertEquals(LoginError.InvalidCredentials, viewModel.state.value.error)

        viewModel.onPasswordChange("mal2")

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `enviar dos veces seguidas consulta una sola vez`() = runTest(mainDispatcher.dispatcher) {
        viewModel.onEmailChange("activa@example.org")
        viewModel.onPasswordChange(PASSWORD)

        viewModel.onSubmit()
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, auth.signInAttempts.size)
    }

    private companion object {
        const val PASSWORD = "Secreta-123"
    }
}
