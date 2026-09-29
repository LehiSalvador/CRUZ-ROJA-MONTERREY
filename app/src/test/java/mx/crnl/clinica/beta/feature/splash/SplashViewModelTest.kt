package mx.crnl.clinica.beta.feature.splash

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.core.demo.LocalDataInitializer
import mx.crnl.clinica.beta.core.demo.SeedException
import mx.crnl.clinica.beta.core.demo.SeedOutcome
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private var initializations = 0

    private fun viewModel(
        auth: FakeAuthRepository = FakeAuthRepository(),
        initializer: LocalDataInitializer = LocalDataInitializer {
            initializations++
            SeedOutcome.AlreadyApplied
        },
        minimumMillis: Long = 500,
    ) = SplashViewModel(initializer, auth, minimumMillis)

    @Test
    fun `sin sesion activa termina en el acceso`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()

        advanceUntilIdle()

        assertEquals(SplashUiState.Ready(SplashDestination.LOGIN), viewModel.state.value)
    }

    @Test
    fun `con sesion activa termina en la shell principal`() = runTest(mainDispatcher.dispatcher) {
        val user = userAccount(id = "mariana")
        val viewModel = viewModel(auth = FakeAuthRepository(listOf(user to "clave"), initialUserId = "mariana"))

        advanceUntilIdle()

        assertEquals(SplashUiState.Ready(SplashDestination.MAIN), viewModel.state.value)
    }

    @Test
    fun `una sesion guardada de una cuenta que ya no esta activa se descarta y va al acceso`() = runTest(mainDispatcher.dispatcher) {
        val suspended = userAccount(id = "suspendida", status = AccountStatus.SUSPENDED)
        val auth = FakeAuthRepository(listOf(suspended to "clave"), initialUserId = "suspendida")
        val viewModel = viewModel(auth = auth)

        advanceUntilIdle()

        assertEquals(SplashUiState.Ready(SplashDestination.LOGIN), viewModel.state.value)
        assertNull(auth.currentUser.first())
    }

    @Test
    fun `una sesion guardada de un usuario inexistente se descarta y va al acceso`() = runTest(mainDispatcher.dispatcher) {
        val auth = FakeAuthRepository(emptyList(), initialUserId = "fantasma")
        val viewModel = viewModel(auth = auth)

        advanceUntilIdle()

        assertEquals(SplashUiState.Ready(SplashDestination.LOGIN), viewModel.state.value)
    }

    @Test
    fun `la sesion se resuelve una sola vez sin ciclos`() = runTest(mainDispatcher.dispatcher) {
        val auth = FakeAuthRepository()
        viewModel(auth = auth)

        advanceUntilIdle()

        assertEquals(1, auth.restoreCalls)
    }

    @Test
    fun `respeta el tiempo minimo aunque la inicializacion sea inmediata`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel(minimumMillis = 500)

        runCurrent()
        assertEquals(SplashUiState.Loading, viewModel.state.value)
        assertEquals(1, initializations)

        advanceTimeBy(499)
        assertEquals(SplashUiState.Loading, viewModel.state.value)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(SplashUiState.Ready(SplashDestination.LOGIN), viewModel.state.value)
    }

    @Test
    fun `si la inicializacion falla informa el error y permite reintentar`() = runTest(mainDispatcher.dispatcher) {
        var attempts = 0
        val viewModel = viewModel(
            initializer = LocalDataInitializer {
                attempts++
                if (attempts == 1) throw SeedException("datos inválidos")
                SeedOutcome.AlreadyApplied
            },
        )

        advanceUntilIdle()
        val failed = viewModel.state.value
        assertTrue("Se esperaba Failed pero fue $failed", failed is SplashUiState.Failed)
        assertEquals("datos inválidos", (failed as SplashUiState.Failed).cause.message)

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, attempts)
        assertEquals(SplashUiState.Ready(SplashDestination.LOGIN), viewModel.state.value)
    }

    @Test
    fun `reintentar fuera del estado de error no relanza la inicializacion`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(1, initializations)
    }
}
