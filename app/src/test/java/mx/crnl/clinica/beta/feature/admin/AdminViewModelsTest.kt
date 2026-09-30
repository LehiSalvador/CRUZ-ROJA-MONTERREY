package mx.crnl.clinica.beta.feature.admin

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.AuditCategory
import mx.crnl.clinica.beta.domain.model.AuditRecord
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.FakeAccountAdministrationRepository
import mx.crnl.clinica.beta.testing.FakeAuditRepository
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.FakeMaintenanceRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.managedAccount
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AdminViewModelsTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val professional = userAccount(id = "pro", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)
    private val coordinator = userAccount(id = "coord", role = UserRole.AREA_COORDINATOR, area = ClinicalArea.PSYCHOLOGY)
    private val admin = userAccount(id = "admin", role = UserRole.CLINICAL_ADMIN, area = null)
    private val system = userAccount(id = "system", role = UserRole.SYSTEM_ADMIN, area = null)

    private fun auth(user: UserAccount) = FakeAuthRepository(listOf(user to "x"), initialUserId = user.userId)

    // ---------------------------------------------------------------- usuarios

    private val directory = listOf(
        managedAccount("pendiente", AccountStatus.PENDING_APPROVAL),
        managedAccount("activa-a", AccountStatus.ACTIVE),
        managedAccount("activa-b", AccountStatus.ACTIVE),
        managedAccount("suspendida", AccountStatus.SUSPENDED),
        managedAccount("rechazada", AccountStatus.REJECTED),
        managedAccount("inactiva", AccountStatus.INACTIVE),
    )

    private fun directoryViewModel(user: UserAccount, accounts: FakeAccountAdministrationRepository = FakeAccountAdministrationRepository(directory)) =
        UserDirectoryViewModel(SavedStateHandle(), auth(user), accounts)

    @Test
    fun `administracion clinica ve el directorio agrupado con conteos y puede gestionar`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = directoryViewModel(admin)
        viewModel.state.onEach {}.launchIn(backgroundScope)
        advanceUntilIdle()

        val content = (viewModel.state.value as UiState.Content).data
        assertTrue(content.allowed)
        assertTrue(content.canManage)
        assertEquals(UserFilter.PENDING, content.filter)
        assertEquals(listOf("pendiente"), content.users.map { it.user.userId })
        assertEquals(mapOf(UserFilter.PENDING to 1, UserFilter.ACTIVE to 2, UserFilter.SUSPENDED_OR_REJECTED to 2, UserFilter.INACTIVE to 1), content.counts)
    }

    @Test
    fun `cambiar de grupo filtra la lista y se conserva`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = directoryViewModel(admin)
        viewModel.state.onEach {}.launchIn(backgroundScope)
        advanceUntilIdle()

        viewModel.selectFilter(UserFilter.SUSPENDED_OR_REJECTED)
        advanceUntilIdle()

        val content = (viewModel.state.value as UiState.Content).data
        assertEquals(setOf("suspendida", "rechazada"), content.users.map { it.user.userId }.toSet())
    }

    @Test
    fun `el administrador del sistema consulta el directorio sin gestionarlo`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = directoryViewModel(system)
        viewModel.state.onEach {}.launchIn(backgroundScope)
        advanceUntilIdle()

        val content = (viewModel.state.value as UiState.Content).data
        assertTrue(content.allowed)
        assertFalse(content.canManage)
    }

    @Test
    fun `sin permiso el directorio no consulta ni muestra ninguna cuenta`() = runTest(mainDispatcher.dispatcher) {
        listOf(professional, coordinator).forEach { user ->
            val viewModel = directoryViewModel(user)
            viewModel.state.onEach {}.launchIn(backgroundScope)
            advanceUntilIdle()

            val content = (viewModel.state.value as UiState.Content).data
            assertFalse(user.userId, content.allowed)
            assertTrue(content.users.isEmpty())
            assertTrue(content.counts.isEmpty())
        }
    }

    // ---------------------------------------------------------------- auditoria

    private val entries = listOf(
        AuditRecord("a1", Instant.EPOCH, "Mariana", "LOGIN", "USER", null, true),
        AuditRecord("a2", Instant.EPOCH, "Héctor", "USER_APPROVED", "USER", ClinicalArea.NUTRITION, true),
        AuditRecord("a3", Instant.EPOCH, null, "DEMO_SEED_APPLIED", "DEMO_SEED", null, true),
    )

    private fun auditViewModel(user: UserAccount) = AuditViewModel(SavedStateHandle(), auth(user), FakeAuditRepository(entries))

    @Test
    fun `administracion clinica y del sistema consultan la bitacora y pueden filtrar`() = runTest(mainDispatcher.dispatcher) {
        listOf(admin, system).forEach { user ->
            val viewModel = auditViewModel(user)
            viewModel.state.onEach {}.launchIn(backgroundScope)
            advanceUntilIdle()
            assertEquals(3, (viewModel.state.value as UiState.Content).data.entries.size)

            viewModel.selectCategory(AuditCategory.REQUESTS)
            advanceUntilIdle()
            assertEquals(listOf("a2"), (viewModel.state.value as UiState.Content).data.entries.map { it.auditId })
        }
    }

    @Test
    fun `sin permiso la bitacora no muestra ninguna entrada`() = runTest(mainDispatcher.dispatcher) {
        listOf(professional, coordinator).forEach { user ->
            val viewModel = auditViewModel(user)
            viewModel.state.onEach {}.launchIn(backgroundScope)
            advanceUntilIdle()

            val content = (viewModel.state.value as UiState.Content).data
            assertFalse(content.allowed)
            assertTrue(content.entries.isEmpty())
        }
    }

    // ---------------------------------------------------------------- herramientas de Beta

    @Test
    fun `restablecer exige las dos confirmaciones en orden`() = runTest(mainDispatcher.dispatcher) {
        val maintenance = FakeMaintenanceRepository()
        val viewModel = BetaToolsViewModel(auth(admin), maintenance)
        advanceUntilIdle()
        assertEquals(true, viewModel.state.value.allowed)

        viewModel.onConfirm() // sin haber confirmado nada
        viewModel.onContinue() // sin haber empezado
        advanceUntilIdle()
        assertEquals(ResetStep.IDLE, viewModel.state.value.step)
        assertEquals(0, maintenance.resets)

        viewModel.onStart()
        assertEquals(ResetStep.FIRST_CONFIRMATION, viewModel.state.value.step)
        viewModel.onConfirm() // todavía falta la segunda
        advanceUntilIdle()
        assertEquals(0, maintenance.resets)

        viewModel.onContinue()
        assertEquals(ResetStep.FINAL_CONFIRMATION, viewModel.state.value.step)
        viewModel.onConfirm()
        advanceUntilIdle()

        assertEquals(1, maintenance.resets)
        assertEquals(8, viewModel.state.value.summary?.patients)
    }

    @Test
    fun `cancelar en cualquier paso vuelve al inicio sin restablecer`() = runTest(mainDispatcher.dispatcher) {
        val maintenance = FakeMaintenanceRepository()
        val viewModel = BetaToolsViewModel(auth(admin), maintenance)
        advanceUntilIdle()

        viewModel.onStart()
        viewModel.onContinue()
        viewModel.onCancel()

        assertEquals(ResetStep.IDLE, viewModel.state.value.step)
        assertEquals(0, maintenance.resets)
    }

    @Test
    fun `quien no es administracion clinica no puede iniciar el restablecimiento aunque llegue a la pantalla`() = runTest(mainDispatcher.dispatcher) {
        listOf(professional, coordinator, system).forEach { user ->
            val maintenance = FakeMaintenanceRepository()
            val viewModel = BetaToolsViewModel(auth(user), maintenance)
            advanceUntilIdle()

            assertEquals(user.userId, false, viewModel.state.value.allowed)
            viewModel.onStart()
            viewModel.onContinue()
            viewModel.onConfirm()
            advanceUntilIdle()
            assertEquals(ResetStep.IDLE, viewModel.state.value.step)
            assertEquals(0, maintenance.resets)
        }
    }

    @Test
    fun `un rechazo del repositorio deja el paso en fallo y permite reintentar`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = BetaToolsViewModel(auth(admin), FakeMaintenanceRepository(allow = false))
        advanceUntilIdle()

        viewModel.onStart()
        viewModel.onContinue()
        viewModel.onConfirm()
        advanceUntilIdle()

        assertEquals(ResetStep.FAILED, viewModel.state.value.step)
        viewModel.onStart()
        assertEquals(ResetStep.FIRST_CONFIRMATION, viewModel.state.value.step)
    }
}
