package mx.crnl.clinica.beta.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.access.AccountAction
import mx.crnl.clinica.beta.domain.account.AccountRequest
import mx.crnl.clinica.beta.domain.common.EntityKind
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.domain.repository.AccountRequestResult
import mx.crnl.clinica.beta.domain.repository.SignInResult
import mx.crnl.clinica.beta.testing.BetaAccounts
import mx.crnl.clinica.beta.testing.RepositoryTest
import mx.crnl.clinica.beta.testing.SeedIds
import mx.crnl.clinica.beta.testing.failure
import mx.crnl.clinica.beta.testing.observeAround
import mx.crnl.clinica.beta.testing.success
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAccountAdministrationRepositoryTest : RepositoryTest() {
    private val password = "clave-de-prueba-1"

    /** Solicita una cuenta como lo haría la persona y devuelve su identificador (queda pendiente de aprobación). */
    private suspend fun requestAccount(
        email: String,
        area: ClinicalArea = ClinicalArea.PSYCHOLOGY,
        role: UserRole = UserRole.PROFESSIONAL,
    ): String {
        val result = auth.requestAccount(
            AccountRequest(
                firstName = "Nueva",
                paternalSurname = "Persona",
                email = email,
                password = password,
                passwordConfirmation = password,
                area = area,
                role = role,
                professionalLicense = "76543210",
            ),
        )
        assertEquals(AccountRequestResult.Submitted, result)
        return requireNotNull(text("SELECT userId FROM demo_users WHERE email = '$email'"))
    }

    private fun statusOf(userId: String) = text("SELECT status FROM demo_users WHERE userId = '$userId'")

    // ---------------------------------------------------------------- revisar solicitudes

    @Test
    fun `coordinacion aprueba a un profesional pendiente de su area y la cuenta pasa a activa`() = runTest {
        val id = requestAccount("psicologa.nueva@example.org")

        val approved = accounts.apply(id, AccountAction.APPROVE, AccountStatus.PENDING_APPROVAL, SeedIds.CLAUDIA).success()

        assertEquals(AccountStatus.ACTIVE, approved.user.status)
        assertEquals("ACTIVE", statusOf(id))
    }

    @Test
    fun `la cuenta aprobada inicia sesion con la contrasena que eligio y no se crea otra`() = runTest {
        val id = requestAccount("psicologa.nueva@example.org")
        val credentialBefore = text("SELECT passwordHash FROM demo_credentials WHERE userId = '$id'")
        assertTrue(auth.signIn("psicologa.nueva@example.org", password) is SignInResult.AccountNotActive)

        accounts.apply(id, AccountAction.APPROVE, AccountStatus.PENDING_APPROVAL, SeedIds.CLAUDIA).success()

        assertTrue(auth.signIn("psicologa.nueva@example.org", password) is SignInResult.Success)
        assertEquals(credentialBefore, text("SELECT passwordHash FROM demo_credentials WHERE userId = '$id'"))
        assertEquals(1, scalar("SELECT COUNT(*) FROM demo_credentials WHERE userId = '$id'"))
    }

    @Test
    fun `aprobar deja constancia en la bitacora sin datos personales ni contrasenas`() = runTest {
        val id = requestAccount("psicologa.nueva@example.org")

        accounts.apply(id, AccountAction.APPROVE, AccountStatus.PENDING_APPROVAL, SeedIds.CLAUDIA).success()

        val entry = auditRows("USER_APPROVED").single()
        assertEquals(SeedIds.CLAUDIA, entry.actor)
        assertEquals("USER", entry.entityType)
        assertEquals(id, entry.entityId)
        assertEquals("PSYCHOLOGY", entry.areaCode)
        assertFalse(entry.metadata.orEmpty().contains("example.org"))
        assertFalse(entry.metadata.orEmpty().contains(password))
    }

    @Test
    fun `coordinacion no aprueba cuentas de otra area y no cambia nada`() = runTest {
        val actionsBefore = auditedActions().size

        val error = accounts.apply(SeedIds.VALERIA, AccountAction.APPROVE, AccountStatus.PENDING_APPROVAL, SeedIds.CLAUDIA).failure<OperationError.NotAuthorized>()

        assertEquals(OperationError.NotAuthorized, error)
        assertEquals("PENDING_APPROVAL", statusOf(SeedIds.VALERIA))
        assertEquals(actionsBefore, auditedActions().size)
    }

    @Test
    fun `coordinacion no aprueba a otra coordinacion`() = runTest {
        val id = requestAccount("coordinadora.nueva@example.org", role = UserRole.AREA_COORDINATOR)

        accounts.apply(id, AccountAction.APPROVE, AccountStatus.PENDING_APPROVAL, SeedIds.CLAUDIA).failure<OperationError.NotAuthorized>()

        assertEquals("PENDING_APPROVAL", statusOf(id))
    }

    @Test
    fun `administracion clinica aprueba coordinacion y profesionales de cualquier area`() = runTest {
        val coordinator = requestAccount("coordinadora.nueva@example.org", area = ClinicalArea.NUTRITION, role = UserRole.AREA_COORDINATOR)

        accounts.apply(coordinator, AccountAction.APPROVE, AccountStatus.PENDING_APPROVAL, SeedIds.HECTOR).success()
        accounts.apply(SeedIds.VALERIA, AccountAction.APPROVE, AccountStatus.PENDING_APPROVAL, SeedIds.HECTOR).success()

        assertEquals("ACTIVE", statusOf(coordinator))
        assertEquals("ACTIVE", statusOf(SeedIds.VALERIA))
    }

    @Test
    fun `rechazar deja la cuenta rechazada sin borrar nada y no puede iniciar sesion`() = runTest {
        val id = requestAccount("psicologa.nueva@example.org")

        accounts.apply(id, AccountAction.REJECT, AccountStatus.PENDING_APPROVAL, SeedIds.CLAUDIA).success()

        assertEquals("REJECTED", statusOf(id))
        assertEquals(1, scalar("SELECT COUNT(*) FROM demo_users WHERE userId = '$id'"))
        assertEquals(1, scalar("SELECT COUNT(*) FROM demo_credentials WHERE userId = '$id'"))
        val signIn = auth.signIn("psicologa.nueva@example.org", password)
        assertEquals(SignInResult.AccountNotActive(AccountStatus.REJECTED), signIn)
        assertEquals(1, auditRows("USER_REJECTED").size)
    }

    @Test
    fun `una cuenta suspendida no inicia sesion y reactivarla la deja entrar`() = runTest {
        accounts.apply(SeedIds.PAOLA, AccountAction.SUSPEND, AccountStatus.ACTIVE, SeedIds.HECTOR).success()

        assertEquals(SignInResult.AccountNotActive(AccountStatus.SUSPENDED), auth.signIn(BetaAccounts.NUTRITIONIST_EMAIL, BetaAccounts.PASSWORD))
        assertEquals(1, auditRows("USER_SUSPENDED").size)

        accounts.apply(SeedIds.PAOLA, AccountAction.REACTIVATE, AccountStatus.SUSPENDED, SeedIds.HECTOR).success()

        assertTrue(auth.signIn(BetaAccounts.NUTRITIONIST_EMAIL, BetaAccounts.PASSWORD) is SignInResult.Success)
        assertEquals(1, auditRows("USER_REACTIVATED").size)
    }

    @Test
    fun `suspender a quien tiene la sesion abierta la cierra`() = runTest {
        auth.signIn(BetaAccounts.NUTRITIONIST_EMAIL, BetaAccounts.PASSWORD)
        assertEquals(SeedIds.PAOLA, auth.currentUser.first()?.userId)

        accounts.apply(SeedIds.PAOLA, AccountAction.SUSPEND, AccountStatus.ACTIVE, SeedIds.HECTOR).success()

        assertNull(auth.currentUser.first())
    }

    // ---------------------------------------------------------------- concurrencia y limites

    @Test
    fun `una segunda accion sobre la misma cuenta no duplica el cambio ni la bitacora`() = runTest {
        val id = requestAccount("psicologa.nueva@example.org")
        accounts.apply(id, AccountAction.APPROVE, AccountStatus.PENDING_APPROVAL, SeedIds.CLAUDIA).success()

        val error = accounts.apply(id, AccountAction.APPROVE, AccountStatus.PENDING_APPROVAL, SeedIds.HECTOR).failure<OperationError.AccountStatusChanged>()

        assertEquals(AccountStatus.ACTIVE, error.current)
        assertEquals(1, auditRows("USER_APPROVED").size)
    }

    @Test
    fun `un estado que la persona ya no veia se rechaza y no se aplica`() = runTest {
        val id = requestAccount("psicologa.nueva@example.org")
        setUserStatus(id, "REJECTED")

        val error = accounts.apply(id, AccountAction.APPROVE, AccountStatus.PENDING_APPROVAL, SeedIds.CLAUDIA).failure<OperationError.AccountStatusChanged>()

        assertEquals(AccountStatus.REJECTED, error.current)
        assertEquals("REJECTED", statusOf(id))
        assertTrue(auditRows("USER_APPROVED").isEmpty())
    }

    @Test
    fun `una accion que no corresponde al estado observado no es autorizada`() = runTest {
        // Aprobar algo que se veía activo no tiene sentido: la acción exige el estado pendiente.
        accounts.apply(SeedIds.PAOLA, AccountAction.APPROVE, AccountStatus.ACTIVE, SeedIds.HECTOR).failure<OperationError.NotAuthorized>()

        assertEquals("ACTIVE", statusOf(SeedIds.PAOLA))
    }

    @Test
    fun `nadie actua sobre su propia cuenta`() = runTest {
        accounts.apply(SeedIds.HECTOR, AccountAction.SUSPEND, AccountStatus.ACTIVE, SeedIds.HECTOR).failure<OperationError.NotAuthorized>()

        assertEquals("ACTIVE", statusOf(SeedIds.HECTOR))
    }

    @Test
    fun `un profesional o el administrador del sistema no aprueban aunque abran la pantalla`() = runTest {
        val system = insertSystemAdmin()

        accounts.apply(SeedIds.VALERIA, AccountAction.APPROVE, AccountStatus.PENDING_APPROVAL, SeedIds.MARIANA).failure<OperationError.NotAuthorized>()
        accounts.apply(SeedIds.VALERIA, AccountAction.APPROVE, AccountStatus.PENDING_APPROVAL, system.userId).failure<OperationError.NotAuthorized>()

        assertEquals("PENDING_APPROVAL", statusOf(SeedIds.VALERIA))
    }

    @Test
    fun `una cuenta que ya no esta activa deja de poder aprobar`() = runTest {
        setUserStatus(SeedIds.HECTOR, "SUSPENDED")

        accounts.apply(SeedIds.VALERIA, AccountAction.APPROVE, AccountStatus.PENDING_APPROVAL, SeedIds.HECTOR).failure<OperationError.NotAuthorized>()
    }

    @Test
    fun `una cuenta inexistente o un actor inexistente se rechazan`() = runTest {
        val notFound = accounts.apply("no-existe", AccountAction.APPROVE, AccountStatus.PENDING_APPROVAL, SeedIds.HECTOR).failure<OperationError.NotFound>()
        assertEquals(EntityKind.USER, notFound.entity)

        accounts.apply(SeedIds.VALERIA, AccountAction.APPROVE, AccountStatus.PENDING_APPROVAL, "fantasma").failure<OperationError.NotAuthorized>()
    }

    @Test
    fun `no se puede reactivar una cuenta rechazada ni suspender una pendiente`() = runTest {
        accounts.apply(SeedIds.IVAN, AccountAction.REACTIVATE, AccountStatus.REJECTED, SeedIds.HECTOR).failure<OperationError.NotAuthorized>()
        accounts.apply(SeedIds.VALERIA, AccountAction.SUSPEND, AccountStatus.PENDING_APPROVAL, SeedIds.HECTOR).failure<OperationError.NotAuthorized>()

        assertEquals("REJECTED", statusOf(SeedIds.IVAN))
        assertEquals("PENDING_APPROVAL", statusOf(SeedIds.VALERIA))
    }

    // ---------------------------------------------------------------- lo que cada persona ve

    @Test
    fun `coordinacion solo ve profesionales pendientes o rechazados de su area`() = runTest {
        val id = requestAccount("psicologa.nueva@example.org")

        val visible = accounts.observeAccounts(account(SeedIds.CLAUDIA)).first().map { it.user.userId }

        assertTrue(id in visible)
        assertTrue(SeedIds.IVAN in visible) // rechazado de Psicología
        assertFalse(SeedIds.VALERIA in visible) // pendiente de Nutrición
        assertFalse(SeedIds.MARIANA in visible) // activa
    }

    @Test
    fun `administracion clinica y del sistema ven todas las cuentas y un profesional ninguna`() = runTest {
        val system = insertSystemAdmin()

        assertEquals(11, accounts.observeAccounts(account(SeedIds.HECTOR)).first().size)
        assertEquals(11, accounts.observeAccounts(system).first().size)
        assertTrue(accounts.observeAccounts(account(SeedIds.MARIANA)).first().isEmpty())
    }

    @Test
    fun `las pendientes van primero y se ve la fecha de solicitud`() = runTest {
        val list = accounts.observeAccounts(account(SeedIds.HECTOR)).first()

        assertEquals(AccountStatus.PENDING_APPROVAL, list.first().user.status)
        assertTrue(list.dropWhile { it.user.status == AccountStatus.PENDING_APPROVAL }.none { it.user.status == AccountStatus.PENDING_APPROVAL })
    }

    @Test
    fun `una cuenta que no se puede consultar no se emite`() = runTest {
        assertNull(accounts.observeAccount(SeedIds.VALERIA, account(SeedIds.CLAUDIA)).first())
        assertEquals(SeedIds.VALERIA, accounts.observeAccount(SeedIds.VALERIA, account(SeedIds.HECTOR)).first()?.user?.userId)
    }

    @Test
    fun `aprobar se refleja en el flujo de cuentas`() = runTest {
        val flow = accounts.observeAccount(SeedIds.VALERIA, account(SeedIds.HECTOR))

        val seen = flow.observeAround(
            change = { accounts.apply(SeedIds.VALERIA, AccountAction.APPROVE, AccountStatus.PENDING_APPROVAL, SeedIds.HECTOR).success() },
            until = { it?.user?.status == AccountStatus.ACTIVE },
        )

        assertEquals(AccountStatus.PENDING_APPROVAL, seen.first()?.user?.status)
        assertEquals(AccountStatus.ACTIVE, seen.last()?.user?.status)
    }
}
