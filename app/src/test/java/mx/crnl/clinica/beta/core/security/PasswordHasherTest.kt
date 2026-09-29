package mx.crnl.clinica.beta.core.security

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    private val hasher = PasswordHasher(defaultIterations = 1_000)

    @Test
    fun `la contrasena correcta se verifica y una incorrecta no`() {
        val digest = hasher.hash("Beta-Local-2026")

        assertTrue(hasher.verify("Beta-Local-2026", digest))
        assertFalse(hasher.verify("Beta-Local-2027", digest))
        assertFalse(hasher.verify("beta-local-2026", digest))
        assertFalse(hasher.verify("", digest))
    }

    @Test
    fun `cada derivacion usa una sal distinta aunque la contrasena sea la misma`() {
        val first = hasher.hash("misma-contrasena")
        val second = hasher.hash("misma-contrasena")

        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.hash, second.hash)
        assertTrue(hasher.verify("misma-contrasena", first))
        assertTrue(hasher.verify("misma-contrasena", second))
    }

    @Test
    fun `el material guardado no contiene la contrasena y tiene los tamanos esperados`() {
        val password = "Contrasena-Muy-Visible-42"
        val digest = hasher.hash(password)

        assertFalse(digest.hash.contains(password))
        assertFalse(digest.salt.contains(password))
        assertEquals(PasswordHasher.SALT_BYTES, Base64.getDecoder().decode(digest.salt).size)
        assertEquals(PasswordHasher.KEY_BITS / 8, Base64.getDecoder().decode(digest.hash).size)
        assertEquals(PasswordHasher.ALGORITHM, digest.algorithm)
        assertEquals(1_000, digest.iterations)
    }

    @Test
    fun `verificar usa los parametros guardados y no los del generador actual`() {
        val stored = PasswordHasher(defaultIterations = 2_000).hash("clave-antigua")

        assertTrue(PasswordHasher(defaultIterations = 50_000).verify("clave-antigua", stored))
    }

    @Test
    fun `admite caracteres no ASCII y espacios`() {
        val digest = hasher.hash("  contraseña con ñ y acentos é  ")

        assertTrue(hasher.verify("  contraseña con ñ y acentos é  ", digest))
        assertFalse(hasher.verify("contraseña con ñ y acentos é", digest))
    }

    @Test
    fun `un registro danado se rechaza sin lanzar excepcion`() {
        val digest = hasher.hash("clave")

        assertFalse(hasher.verify("clave", digest.copy(hash = "no es base64 !!")))
        assertFalse(hasher.verify("clave", digest.copy(salt = "no es base64 !!")))
    }

    @Test
    fun `un correo inexistente puede gastar el mismo esfuerzo sin efectos`() {
        hasher.spendComparableEffort("cualquier-cosa")
    }
}
