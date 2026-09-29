package mx.crnl.clinica.beta.feature.profile

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
class ProfileViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val user = userAccount(id = "mariana")
    private val auth = FakeAuthRepository(listOf(user to "clave"), initialUserId = "mariana")

    @Test
    fun `muestra los datos de la cuenta autenticada`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = ProfileViewModel(auth)
        viewModel.state.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals(user, viewModel.state.value.user)
    }

    @Test
    fun `cerrar sesion la termina y el usuario deja de estar disponible`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = ProfileViewModel(auth)
        viewModel.state.launchIn(backgroundScope)

        viewModel.onSignOut()
        advanceUntilIdle()

        assertNull(auth.currentUser.first())
        assertNull(viewModel.state.value.user)
        assertFalse(viewModel.state.value.hasError)
        assertFalse(viewModel.state.value.isSigningOut)
    }

    @Test
    fun `un fallo al cerrar sesion se informa y la sesion sigue activa`() = runTest(mainDispatcher.dispatcher) {
        auth.signOutFailure = IOException("disco lleno")
        val viewModel = ProfileViewModel(auth)
        viewModel.state.launchIn(backgroundScope)

        viewModel.onSignOut()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.hasError)
        assertEquals(user, auth.currentUser.first())
    }
}
