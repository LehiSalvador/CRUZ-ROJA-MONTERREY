package mx.crnl.clinica.beta.feature.requests

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.domain.access.AccountAction
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.RequestStatus
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.domain.request.RequestCategory
import mx.crnl.clinica.beta.feature.appointments.form.WriteState
import mx.crnl.clinica.beta.testing.FakeAccessRequestRepository
import mx.crnl.clinica.beta.testing.FakeAccountAdministrationRepository
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.FakeProfessionalChangeRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.accessRequestOf
import mx.crnl.clinica.beta.testing.changeContextOf
import mx.crnl.clinica.beta.testing.changeRequestOf
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.grantOf
import mx.crnl.clinica.beta.testing.managedAccount
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
class RequestsViewModelsTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val mariana = userAccount(id = "mariana", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)
    private val rodrigo = userAccount(id = "rodrigo", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)
    private val claudia = userAccount(id = "claudia", role = UserRole.AREA_COORDINATOR, area = ClinicalArea.PSYCHOLOGY)
    private val hector = userAccount(id = "hector", role = UserRole.CLINICAL_ADMIN, area = null)
    private val system = userAccount(id = "system", role = UserRole.SYSTEM_ADMIN, area = null)

    private fun auth(user: UserAccount) = FakeAuthRepository(listOf(user to "x"), initialUserId = user.userId)

    // ---------------------------------------------------------------- bandeja

    private fun requestsViewModel(
        user: UserAccount,
        accounts: FakeAccountAdministrationRepository = FakeAccountAdministrationRepository(),
        access: FakeAccessRequestRepository = FakeAccessRequestRepository(),
        changes: FakeProfessionalChangeRepository = FakeProfessionalChangeRepository(),
        handle: SavedStateHandle = SavedStateHandle(),
    ) = RequestsViewModel(handle, auth(user), accounts, access, changes, fixedClock())

    @Test
    fun `cada rol recibe solo las bandejas que le corresponden`() = runTest(mainDispatcher.dispatcher) {
        val expected = mapOf(
            mariana to listOf(RequestCategory.ACCESS, RequestCategory.CHANGES),
            claudia to listOf(RequestCategory.ACCOUNTS, RequestCategory.ACCESS, RequestCategory.CHANGES),
            hector to listOf(RequestCategory.ACCOUNTS, RequestCategory.ACCESS, RequestCategory.CHANGES),
            system to listOf(RequestCategory.ACCOUNTS),
        )

        expected.forEach { (user, categories) ->
            val viewModel = requestsViewModel(user)
            viewModel.state.onEach {}.launchIn(backgroundScope)
            advanceUntilIdle()
            val content = (viewModel.state.value as UiState.Content).data
            assertEquals(user.role.name, categories, content.categories)
        }
    }

    @Test
    fun `lo pendiente va primero y las cuentas solo incluyen pendientes y rechazadas`() = runTest(mainDispatcher.dispatcher) {
        val accounts = FakeAccountAdministrationRepository(
            listOf(
                managedAccount("rechazada", AccountStatus.REJECTED),
                managedAccount("activa", AccountStatus.ACTIVE),
                managedAccount("pendiente", AccountStatus.PENDING_APPROVAL),
            ),
        )
        val access = FakeAccessRequestRepository(requests = listOf(accessRequestOf("a", status = RequestStatus.APPROVED), accessRequestOf("b")))
        val viewModel = requestsViewModel(hector, accounts, access)
        viewModel.state.onEach {}.launchIn(backgroundScope)
        advanceUntilIdle()

        val content = (viewModel.state.value as UiState.Content).data

        assertEquals(listOf("pendiente", "rechazada"), content.accounts.map { it.user.userId })
        assertEquals(listOf("b", "a"), content.access.map { it.requestId })
        assertEquals(1, content.pendingCount(RequestCategory.ACCOUNTS))
        assertEquals(1, content.pendingCount(RequestCategory.ACCESS))
        assertEquals(0, content.pendingCount(RequestCategory.CHANGES))
    }

    @Test
    fun `la bandeja abierta se conserva`() = runTest(mainDispatcher.dispatcher) {
        val handle = SavedStateHandle()
        val viewModel = requestsViewModel(claudia, handle = handle)
        viewModel.selectCategory(RequestCategory.CHANGES)

        assertEquals("CHANGES", requestsViewModel(claudia, handle = handle).selectedCategory.value)
    }

    @Test
    fun `un fallo de lectura se muestra como error con reintento`() = runTest(mainDispatcher.dispatcher) {
        val failing = object : mx.crnl.clinica.beta.domain.repository.AccessRequestRepository by FakeAccessRequestRepository() {
            override fun observeRequests(viewer: UserAccount) = kotlinx.coroutines.flow.flow<List<mx.crnl.clinica.beta.domain.model.AccessRequest>> {
                throw java.io.IOException("fallo simulado")
            }
        }
        val viewModel = RequestsViewModel(SavedStateHandle(), auth(hector), FakeAccountAdministrationRepository(), failing, FakeProfessionalChangeRepository(), fixedClock())
        viewModel.state.onEach {}.launchIn(backgroundScope)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Error)
    }

    // ---------------------------------------------------------------- cuenta

    private fun accountViewModel(user: UserAccount, accounts: FakeAccountAdministrationRepository, id: String = "nueva") =
        AccountDetailViewModel(SavedStateHandle(mapOf("userId" to id)), auth(user), accounts)

    @Test
    fun `coordinacion aprueba una cuenta y el detalle lo confirma`() = runTest(mainDispatcher.dispatcher) {
        val accounts = FakeAccountAdministrationRepository(listOf(managedAccount("nueva")))
        val viewModel = accountViewModel(claudia, accounts)
        viewModel.state.onEach {}.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(setOf(AccountAction.APPROVE, AccountAction.REJECT), (viewModel.state.value as UiState.Content).data.actions)

        viewModel.onApply(AccountAction.APPROVE)
        advanceUntilIdle()

        assertEquals(ReviewState.Done(ReviewOutcome.ACCOUNT_APPROVED), viewModel.review.value)
        assertEquals(listOf(Triple("nueva", AccountAction.APPROVE, "claudia")), accounts.applied)
    }

    @Test
    fun `una accion que la politica no permite no llega al repositorio`() = runTest(mainDispatcher.dispatcher) {
        val accounts = FakeAccountAdministrationRepository(listOf(managedAccount("nueva")))
        val viewModel = accountViewModel(claudia, accounts)
        viewModel.state.onEach {}.launchIn(backgroundScope)
        advanceUntilIdle()

        viewModel.onApply(AccountAction.SUSPEND)
        advanceUntilIdle()

        assertTrue(accounts.applied.isEmpty())
        assertEquals(ReviewState.Idle, viewModel.review.value)
    }

    @Test
    fun `una cuenta que la persona no puede consultar no se muestra`() = runTest(mainDispatcher.dispatcher) {
        val accounts = FakeAccountAdministrationRepository(listOf(managedAccount("nueva", area = ClinicalArea.NUTRITION)))
        val viewModel = accountViewModel(claudia, accounts)
        viewModel.state.onEach {}.launchIn(backgroundScope)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Empty)
        viewModel.onApply(AccountAction.APPROVE)
        advanceUntilIdle()
        assertTrue(accounts.applied.isEmpty())
    }

    @Test
    fun `el administrador del sistema consulta la cuenta sin acciones`() = runTest(mainDispatcher.dispatcher) {
        val accounts = FakeAccountAdministrationRepository(listOf(managedAccount("nueva")))
        val viewModel = accountViewModel(system, accounts)
        viewModel.state.onEach {}.launchIn(backgroundScope)
        advanceUntilIdle()

        assertTrue((viewModel.state.value as UiState.Content).data.actions.isEmpty())
    }

    @Test
    fun `un rechazo de las reglas se informa sin detalles tecnicos`() = runTest(mainDispatcher.dispatcher) {
        val accounts = object : mx.crnl.clinica.beta.domain.repository.AccountAdministrationRepository by FakeAccountAdministrationRepository(listOf(managedAccount("nueva"))) {
            override suspend fun apply(targetUserId: String, action: AccountAction, expectedStatus: AccountStatus, actorUserId: String) =
                mx.crnl.clinica.beta.domain.common.OperationResult.Failure(OperationError.AccountStatusChanged(AccountStatus.ACTIVE))
        }
        val viewModel = AccountDetailViewModel(SavedStateHandle(mapOf("userId" to "nueva")), auth(claudia), accounts)
        viewModel.state.onEach {}.launchIn(backgroundScope)
        advanceUntilIdle()

        viewModel.onApply(AccountAction.APPROVE)
        advanceUntilIdle()

        assertEquals(ReviewState.Failed(OperationError.AccountStatusChanged(AccountStatus.ACTIVE)), viewModel.review.value)
    }

    // ---------------------------------------------------------------- acceso interárea

    private fun accessViewModel(user: UserAccount, access: FakeAccessRequestRepository, id: String = "r1") =
        AccessRequestDetailViewModel(SavedStateHandle(mapOf("requestId" to id)), auth(user), access, fixedClock())

    @Test
    fun `quien revisa aprueba con la vigencia elegida y la propuesta es de siete dias`() = runTest(mainDispatcher.dispatcher) {
        val access = FakeAccessRequestRepository(requests = listOf(accessRequestOf(requester = "mariana", ownerArea = ClinicalArea.NUTRITION)))
        val viewModel = accessViewModel(hector, access)
        viewModel.state.onEach {}.launchIn(backgroundScope)
        advanceUntilIdle()
        assertEquals(7, viewModel.durationDays.value)
        assertTrue((viewModel.state.value as UiState.Content).data.canReview)

        viewModel.onDurationSelected(30)
        viewModel.onDurationSelected(365) // no permitida: se ignora
        viewModel.onApprove()
        advanceUntilIdle()

        assertEquals(listOf("r1" to 30), access.approvals)
        assertEquals(ReviewState.Done(ReviewOutcome.ACCESS_APPROVED), viewModel.review.value)
    }

    @Test
    fun `quien la pidio y quien no es de su area no pueden aprobar aunque llamen a la accion`() = runTest(mainDispatcher.dispatcher) {
        val access = FakeAccessRequestRepository(requests = listOf(accessRequestOf(requester = "mariana", ownerArea = ClinicalArea.NUTRITION)))

        listOf(mariana, claudia, rodrigo).forEach { user ->
            val viewModel = accessViewModel(user, access)
            viewModel.state.onEach {}.launchIn(backgroundScope)
            advanceUntilIdle()
            assertFalse(user.userId, (viewModel.state.value as UiState.Content).data.canReview)
            viewModel.onApprove()
            viewModel.onReject()
            advanceUntilIdle()
        }

        assertTrue(access.approvals.isEmpty())
        assertTrue(access.rejections.isEmpty())
    }

    @Test
    fun `revocar solo se ofrece con una concesion vigente y a quien administra el area`() = runTest(mainDispatcher.dispatcher) {
        val approved = accessRequestOf(status = RequestStatus.APPROVED, grant = grantOf())
        val access = FakeAccessRequestRepository(requests = listOf(approved))
        val forAdmin = accessViewModel(hector, access)
        val forRequester = accessViewModel(mariana, access)
        listOf(forAdmin, forRequester).forEach { it.state.onEach {}.launchIn(backgroundScope) }
        advanceUntilIdle()

        assertTrue((forAdmin.state.value as UiState.Content).data.canRevoke)
        assertFalse((forRequester.state.value as UiState.Content).data.canRevoke)

        forRequester.onRevoke()
        forAdmin.onRevoke()
        advanceUntilIdle()
        assertEquals(listOf("g1"), access.revocations)
    }

    @Test
    fun `una solicitud que la persona no puede ver no se emite`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = accessViewModel(hector, FakeAccessRequestRepository(), id = "no-existe")
        viewModel.state.onEach {}.launchIn(backgroundScope)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Empty)
    }

    // ---------------------------------------------------------------- cambio de profesional

    private fun changeViewModel(user: UserAccount, changes: FakeProfessionalChangeRepository, id: String = "o1") =
        ChangeRequestDetailViewModel(SavedStateHandle(mapOf("requestId" to id)), auth(user), changes)

    @Test
    fun `coordinacion aprueba el cambio y quien lo pidio no`() = runTest(mainDispatcher.dispatcher) {
        val changes = FakeProfessionalChangeRepository(requests = listOf(changeRequestOf(requester = "rodrigo")))
        val coordinator = changeViewModel(claudia, changes)
        val requester = changeViewModel(rodrigo, changes)
        listOf(coordinator, requester).forEach { it.state.onEach {}.launchIn(backgroundScope) }
        advanceUntilIdle()

        requester.onApprove()
        requester.onReject()
        advanceUntilIdle()
        assertTrue(changes.approvals.isEmpty())

        coordinator.onApprove()
        advanceUntilIdle()
        assertEquals(listOf("o1"), changes.approvals)
        assertEquals(ReviewState.Done(ReviewOutcome.CHANGE_APPROVED), coordinator.review.value)
    }

    @Test
    fun `rechazar envia la razon opcional recortada`() = runTest(mainDispatcher.dispatcher) {
        val changes = FakeProfessionalChangeRepository(requests = listOf(changeRequestOf()))
        val viewModel = changeViewModel(hector, changes)
        viewModel.state.onEach {}.launchIn(backgroundScope)
        advanceUntilIdle()

        viewModel.onRejectReasonChange("x".repeat(500))
        viewModel.onReject()
        advanceUntilIdle()

        assertEquals(300, changes.rejections.single().second!!.length)
        assertEquals(ReviewState.Done(ReviewOutcome.CHANGE_REJECTED), viewModel.review.value)
    }

    @Test
    fun `un cambio ya resuelto no ofrece revision`() = runTest(mainDispatcher.dispatcher) {
        val changes = FakeProfessionalChangeRepository(requests = listOf(changeRequestOf(status = RequestStatus.APPROVED)))
        val viewModel = changeViewModel(hector, changes)
        viewModel.state.onEach {}.launchIn(backgroundScope)
        advanceUntilIdle()

        assertFalse((viewModel.state.value as UiState.Content).data.canReview)
        viewModel.onApprove()
        advanceUntilIdle()
        assertTrue(changes.approvals.isEmpty())
    }

    // ---------------------------------------------------------------- formularios

    @Test
    fun `el formulario de acceso exige un motivo valido antes de enviar`() = runTest(mainDispatcher.dispatcher) {
        val access = FakeAccessRequestRepository(
            context = mx.crnl.clinica.beta.domain.model.AccessRequestContext("p1", "Fernanda", "CRNL-000003", ClinicalArea.NUTRITION, alreadyPending = false),
            requests = listOf(accessRequestOf()),
        )
        val viewModel = NewAccessRequestViewModel(SavedStateHandle(mapOf("patientId" to "p1", "area" to "NUTRITION")), auth(mariana), access)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canSubmit)

        viewModel.onReasonChange("corto")
        viewModel.onSubmit()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.reasonInvalid)
        assertTrue(access.drafts.isEmpty())

        viewModel.onReasonChange("Necesito consultar el seguimiento nutricional")
        viewModel.onSubmit()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isSaved)
        assertEquals("NUTRITION", access.drafts.single().ownerArea.name)
    }

    @Test
    fun `el formulario de acceso se bloquea si no se puede pedir o ya hay una pendiente`() = runTest(mainDispatcher.dispatcher) {
        val denied = NewAccessRequestViewModel(SavedStateHandle(mapOf("patientId" to "p1", "area" to "NUTRITION")), auth(mariana), FakeAccessRequestRepository(context = null))
        advanceUntilIdle()
        assertEquals(AccessRequestBlock.NOT_ALLOWED, denied.state.value.block)
        assertFalse(denied.state.value.canSubmit)

        val pending = NewAccessRequestViewModel(
            SavedStateHandle(mapOf("patientId" to "p1", "area" to "NUTRITION")),
            auth(mariana),
            FakeAccessRequestRepository(context = mx.crnl.clinica.beta.domain.model.AccessRequestContext("p1", "F", "N", ClinicalArea.NUTRITION, alreadyPending = true)),
        )
        advanceUntilIdle()
        assertEquals(AccessRequestBlock.ALREADY_PENDING, pending.state.value.block)
    }

    @Test
    fun `un rechazo al enviar el acceso se muestra y no se guarda`() = runTest(mainDispatcher.dispatcher) {
        val access = FakeAccessRequestRepository(
            context = mx.crnl.clinica.beta.domain.model.AccessRequestContext("p1", "F", "N", ClinicalArea.NUTRITION, alreadyPending = false),
            requests = listOf(accessRequestOf()),
        ).apply { createError = OperationError.DuplicatePendingRequest }
        val viewModel = NewAccessRequestViewModel(SavedStateHandle(mapOf("patientId" to "p1", "area" to "NUTRITION")), auth(mariana), access)
        advanceUntilIdle()
        viewModel.onReasonChange("Necesito consultar el seguimiento nutricional")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(WriteState.Failed(OperationError.DuplicatePendingRequest), viewModel.state.value.write)
        assertFalse(viewModel.state.value.isSaved)
    }

    @Test
    fun `el formulario de cambio pide profesional y motivo y no envia sin ellos`() = runTest(mainDispatcher.dispatcher) {
        val changes = FakeProfessionalChangeRepository(context = changeContextOf(), requests = listOf(changeRequestOf()))
        val viewModel = NewProfessionalChangeViewModel(SavedStateHandle(mapOf("patientId" to "p1", "area" to "PSYCHOLOGY")), auth(rodrigo), changes)
        advanceUntilIdle()
        assertFalse("sin profesional elegido no se puede enviar", viewModel.state.value.canSubmit)

        viewModel.onSelect("mariana")
        viewModel.onSubmit()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.reasonInvalid)
        assertTrue(changes.drafts.isEmpty())

        viewModel.onReasonChange("Cambio por carga de agenda del profesional")
        viewModel.onSubmit()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isSaved)
        assertEquals("mariana", changes.drafts.single().requestedProfessionalId)
    }

    @Test
    fun `el formulario de cambio se bloquea sin permiso o con un cambio pendiente`() = runTest(mainDispatcher.dispatcher) {
        val denied = NewProfessionalChangeViewModel(SavedStateHandle(mapOf("patientId" to "p1", "area" to "PSYCHOLOGY")), auth(mariana), FakeProfessionalChangeRepository(context = null))
        advanceUntilIdle()
        assertEquals(ChangeBlock.NOT_ALLOWED, denied.state.value.block)

        val pending = NewProfessionalChangeViewModel(
            SavedStateHandle(mapOf("patientId" to "p1", "area" to "PSYCHOLOGY")),
            auth(rodrigo),
            FakeProfessionalChangeRepository(context = changeContextOf(pendingRequestId = "o9")),
        )
        advanceUntilIdle()
        assertEquals(ChangeBlock.ALREADY_PENDING, pending.state.value.block)
        assertNull(pending.state.value.selected)
    }
}
