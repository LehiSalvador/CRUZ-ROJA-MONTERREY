package mx.crnl.clinica.beta.core.demo

import java.io.File
import mx.crnl.clinica.beta.core.security.PasswordDigest
import mx.crnl.clinica.beta.core.security.PasswordHasher
import mx.crnl.clinica.beta.testing.BetaAccounts
import mx.crnl.clinica.beta.testing.FileSystemSeedReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `docs/BETA_ACCESS.md` es lo que lee quien prueba la Beta: sus cuentas tienen que ser las que realmente funcionan. */
class BetaAccessDocsTest {
    private val seed = DemoSeedLoader(FileSystemSeedReader()).load()
    private val docs = File("../docs/BETA_ACCESS.md").readText(Charsets.UTF_8)

    private data class DocumentedAccount(val email: String, val password: String, val status: String)

    private val accounts: List<DocumentedAccount> = docs.lines()
        .filter { it.startsWith("| `") }
        .map { line -> line.split('|').map { it.trim().trim('`') } }
        .map { cells -> DocumentedAccount(email = cells[1], password = cells[2], status = cells[4]) }

    @Test
    fun `documenta exactamente las cuentas de la semilla`() {
        assertEquals(seed.users.map { it.email }.sorted(), accounts.map { it.email }.sorted())
    }

    @Test
    fun `cada contrasena documentada corresponde al material de verificacion de su cuenta`() {
        val hasher = PasswordHasher()
        accounts.forEach { account ->
            val user = seed.users.single { it.email == account.email }
            val credential = seed.credentials.single { it.userId == user.userId }
            val digest = PasswordDigest(credential.algorithm, credential.iterations, credential.salt, credential.hash)
            assertTrue("la contraseña documentada de ${account.email} no coincide", hasher.verify(account.password, digest))
            assertFalse("una contraseña distinta no debe verificar", hasher.verify(account.password + "x", digest))
        }
    }

    @Test
    fun `el estado documentado coincide con el estado de la semilla`() {
        val labels = mapOf(
            "ACTIVE" to "Activa",
            "PENDING_APPROVAL" to "Pendiente de aprobación",
            "SUSPENDED" to "Suspendida",
            "REJECTED" to "Rechazada",
            "INACTIVE" to "Inactiva",
        )
        accounts.forEach { account ->
            val user = seed.users.single { it.email == account.email }
            assertEquals(account.email, labels.getValue(user.status), account.status)
        }
    }

    @Test
    fun `las constantes de prueba usan la misma contrasena documentada`() {
        assertTrue(accounts.all { it.password == BetaAccounts.PASSWORD })
    }

    @Test
    fun `la contrasena documentada no esta en el codigo, los recursos ni los datos de la aplicacion`() {
        File("src/main").walkTopDown().filter { it.isFile }.forEach { file ->
            assertFalse("${file.path} contiene la contraseña de prueba en claro", file.readText(Charsets.UTF_8).contains(BetaAccounts.PASSWORD))
        }
    }

    @Test
    fun `el documento aclara que son credenciales ficticias y no menciona secretos de despliegue`() {
        assertTrue(docs.contains("credenciales ficticias de la Beta local"))
        assertTrue(docs.contains("no son credenciales institucionales"))
        assertFalse(docs.contains("GH_TOKEN"))
        assertFalse(docs.contains("Downloads"))
    }
}
