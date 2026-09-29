package mx.crnl.clinica.beta.feature

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.feature.auth.LoginUiState
import mx.crnl.clinica.beta.feature.auth.LoginViewModel
import mx.crnl.clinica.beta.feature.profile.ProfileUiState
import mx.crnl.clinica.beta.feature.profile.ProfileViewModel
import mx.crnl.clinica.beta.testing.FakeSessionRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelsTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `continuar en el acceso inicia la sesion`() = runTest(mainDispatcher.dispatcher) {
        val session = FakeSessionRepository()
        val viewModel = LoginViewModel(session)

        viewModel.onContinue()
        advanceUntilIdle()

        assertEquals(LoginUiState(isSessionStarted = true), viewModel.state.value)
        assertTrue(session.isSessionActive.first())
    }

    @Test
    fun `un fallo al iniciar sesion se informa sin cerrar la app`() = runTest(mainDispatcher.dispatcher) {
        val session = FakeSessionRepository().apply { writeFailure = IOException("disco lleno") }
        val viewModel = LoginViewModel(session)

        viewModel.onContinue()
        advanceUntilIdle()

        assertEquals(LoginUiState(hasError = true), viewModel.state.value)
        assertFalse(session.isSessionActive.first())

        session.writeFailure = null
        viewModel.onContinue()
        advanceUntilIdle()

        assertEquals(LoginUiState(isSessionStarted = true), viewModel.state.value)
    }

    @Test
    fun `cerrar sesion la termina`() = runTest(mainDispatcher.dispatcher) {
        val session = FakeSessionRepository(active = true)
        val viewModel = ProfileViewModel(session)

        viewModel.onSignOut()
        advanceUntilIdle()

        assertEquals(ProfileUiState(isSignedOut = true), viewModel.state.value)
        assertFalse(session.isSessionActive.first())
    }

    @Test
    fun `un fallo al cerrar sesion se informa y la sesion sigue activa`() = runTest(mainDispatcher.dispatcher) {
        val session = FakeSessionRepository(active = true).apply { writeFailure = IOException("disco lleno") }
        val viewModel = ProfileViewModel(session)

        viewModel.onSignOut()
        advanceUntilIdle()

        assertEquals(ProfileUiState(hasError = true), viewModel.state.value)
        assertTrue(session.isSessionActive.first())
    }
}
