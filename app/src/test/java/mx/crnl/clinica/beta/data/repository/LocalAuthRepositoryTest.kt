package mx.crnl.clinica.beta.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.account.AccountRequest
import mx.crnl.clinica.beta.domain.account.AccountRequestField
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.AuditAction
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.domain.repository.AccountRequestResult
import mx.crnl.clinica.beta.domain.repository.SignInResult
import mx.crnl.clinica.beta.testing.BetaAccounts
import mx.crnl.clinica.beta.testing.RepositoryTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAuthRepositoryTest : RepositoryTest() {

    private suspend fun signInAs(email: String, password: String = BetaAccounts.PASSWORD) = auth.signIn(email, password)

    private fun validRequest(email: String = "nueva.persona@example.org") = AccountRequest(
        firstName = "Nueva",
        paternalSurname = "Persona",
        maternalSurname = "Ejemplo",
        email = email,
        password = "clave-de-prueba-1",
        passwordConfirmation = "clave-de-prueba-1",
        area = ClinicalArea.GENERAL_MEDICINE,
        role = UserRole.PROFESSIONAL,
        professionalLicense = "76543210",
    )

    @Test
    fun `una cuenta activa con la contrasena correcta inicia sesion y la persiste`() = runTest {
        val result = signInAs(BetaAccounts.PSYCHOLOGIST_EMAIL)

        assertTrue(result is SignInResult.Success)
        assertEquals(BetaAccounts.PSYCHOLOGIST_ID, (result as SignInResult.Success).user.userId)
        assertEquals(BetaAccounts.PSYCHOLOGIST_ID, session.sessionUserId.first())
        assertEquals(BetaAccounts.PSYCHOLOGIST_ID, auth.currentUser.first()?.userId)
    }

    @Test
    fun `cada perfil de prueba entra con su cuenta`() = runTest {
        listOf(
            BetaAccounts.PSYCHOLOGIST_EMAIL to UserRole.PROFESSIONAL,
            BetaAccounts.NUTRITIONIST_EMAIL to UserRole.PROFESSIONAL,
            BetaAccounts.COORDINATOR_EMAIL to UserRole.AREA_COORDINATOR,
            BetaAccounts.ADMIN_EMAIL to UserRole.CLINICAL_ADMIN,
        ).forEach { (email, role) ->
            val result = signInAs(email) as SignInResult.Success
            assertEquals(role, result.user.role)
        }
    }

    @Test
    fun `una contrasena incorrecta no inicia sesion ni guarda nada`() = runTest {
        val result = signInAs(BetaAccounts.PSYCHOLOGIST_EMAIL, password = "Beta-Local-2027")

        assertEquals(SignInResult.InvalidCredentials, result)
        assertNull(session.sessionUserId.first())
        assertEquals(emptyList<String>(), auditedActions())
    }

    @Test
    fun `un correo inexistente recibe el mismo rechazo que una contrasena incorrecta`() = runTest {
        val unknown = signInAs("nadie@example.org")
        val wrongPassword = signInAs(BetaAccounts.PSYCHOLOGIST_EMAIL, password = "incorrecta")

        assertEquals(SignInResult.InvalidCredentials, unknown)
        assertEquals(unknown, wrongPassword)
        assertNull(session.sessionUserId.first())
    }

    @Test
    fun `el correo se recorta y no distingue mayusculas`() = runTest {
        val result = signInAs("  MARIANA.Elizondo@Example.ORG  ")

        assertTrue(result is SignInResult.Success)
    }

    @Test
    fun `la contrasena se compara exactamente como se escribio`() = runTest {
        assertEquals(SignInResult.InvalidCredentials, signInAs(BetaAccounts.PSYCHOLOGIST_EMAIL, password = " ${BetaAccounts.PASSWORD}"))
        assertEquals(SignInResult.InvalidCredentials, signInAs(BetaAccounts.PSYCHOLOGIST_EMAIL, password = BetaAccounts.PASSWORD.lowercase()))
        assertEquals(SignInResult.InvalidCredentials, signInAs(BetaAccounts.PSYCHOLOGIST_EMAIL, password = ""))
        assertEquals(SignInResult.InvalidCredentials, signInAs("", password = BetaAccounts.PASSWORD))
    }

    @Test
    fun `cada estado que no es activo se rechaza con su motivo solo si la contrasena es correcta`() = runTest {
        mapOf(
            BetaAccounts.PENDING_EMAIL to AccountStatus.PENDING_APPROVAL,
            BetaAccounts.SUSPENDED_EMAIL to AccountStatus.SUSPENDED,
            BetaAccounts.REJECTED_EMAIL to AccountStatus.REJECTED,
            BetaAccounts.INACTIVE_EMAIL to AccountStatus.INACTIVE,
        ).forEach { (email, status) ->
            assertEquals(SignInResult.AccountNotActive(status), signInAs(email))
            assertNull("$status no debe abrir sesión", session.sessionUserId.first())
            assertEquals("$status: con contraseña incorrecta no se revela el estado", SignInResult.InvalidCredentials, signInAs(email, "incorrecta"))
        }
        assertEquals(emptyList<String>(), auditedActions())
    }

    @Test
    fun `el inicio de sesion queda auditado con el actor y sin datos sensibles`() = runTest {
        signInAs(BetaAccounts.PSYCHOLOGIST_EMAIL)

        assertEquals(listOf(AuditAction.LOGIN.name), auditedActions())
        db.query("SELECT actorUserId, entityType, entityId, patientId, result, metadata FROM audit_entries WHERE action = 'LOGIN'", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals(BetaAccounts.PSYCHOLOGIST_ID, cursor.getString(0))
            assertEquals("USER", cursor.getString(1))
            assertEquals(BetaAccounts.PSYCHOLOGIST_ID, cursor.getString(2))
            assertTrue(cursor.isNull(3))
            assertEquals("SUCCESS", cursor.getString(4))
            assertTrue(cursor.isNull(5))
        }
    }

    @Test
    fun `cerrar sesion limpia la sesion y queda auditado`() = runTest {
        signInAs(BetaAccounts.PSYCHOLOGIST_EMAIL)

        auth.signOut()

        assertNull(session.sessionUserId.first())
        assertNull(auth.currentUser.first())
        assertEquals(listOf(AuditAction.LOGIN.name, AuditAction.LOGOUT.name), auditedActions())
    }

    @Test
    fun `cerrar sesion sin sesion no falla ni audita`() = runTest {
        auth.signOut()

        assertNull(session.sessionUserId.first())
        assertEquals(emptyList<String>(), auditedActions())
    }

    @Test
    fun `restaurar la sesion devuelve al usuario mientras siga activo`() = runTest {
        session.startSession(BetaAccounts.COORDINATOR_ID)

        assertEquals(BetaAccounts.COORDINATOR_ID, auth.restoreSession()?.userId)
        assertEquals(BetaAccounts.COORDINATOR_ID, session.sessionUserId.first())
    }

    @Test
    fun `una sesion sin usuario o con un usuario inexistente se limpia al restaurar`() = runTest {
        assertNull(auth.restoreSession())

        session.startSession("fantasma")

        assertNull(auth.restoreSession())
        assertNull(session.sessionUserId.first())
    }

    @Test
    fun `una sesion de una cuenta que dejo de estar activa se limpia al restaurar`() = runTest {
        session.startSession(BetaAccounts.PSYCHOLOGIST_ID)
        db.openHelper.writableDatabase.execSQL("UPDATE demo_users SET status = 'SUSPENDED' WHERE userId = '${BetaAccounts.PSYCHOLOGIST_ID}'")

        assertNull(auth.restoreSession())
        assertNull(session.sessionUserId.first())
    }

    @Test
    fun `el usuario actual deja de existir en cuanto la cuenta se suspende`() = runTest {
        signInAs(BetaAccounts.PSYCHOLOGIST_EMAIL)
        assertNotNull(auth.currentUser.first())

        db.openHelper.writableDatabase.execSQL("UPDATE demo_users SET status = 'SUSPENDED' WHERE userId = '${BetaAccounts.PSYCHOLOGIST_ID}'")

        assertNull(auth.currentUser.first())
    }

    @Test
    fun `solicitar una cuenta la deja pendiente de aprobacion sin iniciar sesion`() = runTest {
        val result = auth.requestAccount(validRequest())

        assertEquals(AccountRequestResult.Submitted, result)
        db.query("SELECT status, roleCode, areaCode, professionalLicense, email FROM demo_users WHERE email = 'nueva.persona@example.org'", null).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("PENDING_APPROVAL", cursor.getString(0))
            assertEquals("PROFESSIONAL", cursor.getString(1))
            assertEquals("GENERAL_MEDICINE", cursor.getString(2))
            assertEquals("76543210", cursor.getString(3))
        }
        assertNull("no debe iniciar sesión", session.sessionUserId.first())
        assertNull(auth.currentUser.first())
    }

    @Test
    fun `la solicitud guarda solo material de verificacion y nunca la contrasena`() = runTest {
        auth.requestAccount(validRequest())

        db.query(
            "SELECT c.algorithm, c.iterations, c.salt, c.passwordHash FROM demo_credentials c " +
                "JOIN demo_users u ON u.userId = c.userId WHERE u.email = 'nueva.persona@example.org'",
            null,
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("PBKDF2WithHmacSHA256", cursor.getString(0))
            assertEquals(TEST_ITERATIONS, cursor.getInt(1))
            assertFalse(cursor.getString(2).contains("clave-de-prueba-1"))
            assertFalse(cursor.getString(3).contains("clave-de-prueba-1"))
        }
        val everything = StringBuilder()
        db.query("SELECT * FROM demo_credentials", null).use { cursor ->
            while (cursor.moveToNext()) for (column in 0 until cursor.columnCount) everything.append(cursor.getString(column)).append('|')
        }
        db.query("SELECT * FROM audit_entries", null).use { cursor ->
            while (cursor.moveToNext()) for (column in 0 until cursor.columnCount) everything.append(cursor.getString(column)).append('|')
        }
        assertFalse("la contraseña en claro no debe existir en Room", everything.contains("clave-de-prueba-1"))
    }

    @Test
    fun `una cuenta solicitada puede verificar su contrasena pero sigue sin poder entrar`() = runTest {
        auth.requestAccount(validRequest())

        val result = auth.signIn("nueva.persona@example.org", "clave-de-prueba-1")

        assertEquals(SignInResult.AccountNotActive(AccountStatus.PENDING_APPROVAL), result)
        assertNull(session.sessionUserId.first())
        assertEquals(SignInResult.InvalidCredentials, auth.signIn("nueva.persona@example.org", "otra"))
    }

    @Test
    fun `la solicitud queda auditada con el rol y el area pero sin correo ni contrasena`() = runTest {
        auth.requestAccount(validRequest())

        assertEquals(listOf(AuditAction.USER_REQUESTED.name), auditedActions())
        db.query("SELECT actorUserId, entityId, metadata FROM audit_entries WHERE action = 'USER_REQUESTED'", null).use { cursor ->
            cursor.moveToFirst()
            assertNotNull(cursor.getString(0))
            assertEquals(cursor.getString(0), cursor.getString(1))
            val metadata = cursor.getString(2)
            assertEquals("""{"role":"PROFESSIONAL","area":"GENERAL_MEDICINE"}""", metadata)
        }
    }

    @Test
    fun `un correo ya registrado se rechaza sin tocar la cuenta existente`() = runTest {
        val before = db.userDao().findByEmail(BetaAccounts.PSYCHOLOGIST_EMAIL)

        val result = auth.requestAccount(validRequest(email = "  MARIANA.ELIZONDO@example.org "))

        assertEquals(AccountRequestResult.EmailAlreadyRegistered, result)
        assertEquals(before, db.userDao().findByEmail(BetaAccounts.PSYCHOLOGIST_EMAIL))
        assertEquals(10, count("demo_users"))
        assertEquals(10, count("demo_credentials"))
        assertEquals(emptyList<String>(), auditedActions())
    }

    @Test
    fun `una solicitud invalida no crea nada`() = runTest {
        val result = auth.requestAccount(validRequest().copy(passwordConfirmation = "distinta", role = UserRole.SYSTEM_ADMIN))

        assertTrue(result is AccountRequestResult.Invalid)
        val issues = (result as AccountRequestResult.Invalid).issues
        assertTrue(AccountRequestField.PASSWORD_CONFIRMATION in issues)
        assertTrue(AccountRequestField.ROLE in issues)
        assertEquals(10, count("demo_users"))
    }

    @Test
    fun `dos solicitudes distintas producen sales distintas para la misma contrasena`() = runTest {
        auth.requestAccount(validRequest(email = "uno@example.org"))
        auth.requestAccount(validRequest(email = "dos@example.org"))

        val salts = db.query("SELECT c.salt FROM demo_credentials c JOIN demo_users u ON u.userId = c.userId WHERE u.email IN ('uno@example.org', 'dos@example.org')", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertEquals(2, salts.size)
        assertNotEquals(salts[0], salts[1])
    }

    @Test
    fun `una cuenta aprobada por otra via puede entrar con la contrasena que eligio`() = runTest {
        auth.requestAccount(validRequest())
        db.openHelper.writableDatabase.execSQL("UPDATE demo_users SET status = 'ACTIVE' WHERE email = 'nueva.persona@example.org'")

        val result = auth.signIn("nueva.persona@example.org", "clave-de-prueba-1")

        assertTrue(result is SignInResult.Success)
    }
}
