package mx.crnl.clinica.beta.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Material para verificar una contraseña; la contraseña en claro nunca se guarda. */
data class PasswordDigest(
    val algorithm: String,
    val iterations: Int,
    val salt: String,
    val hash: String,
)

/**
 * Deriva y verifica contraseñas con PBKDF2 (HMAC-SHA256) y una sal distinta por cuenta. Los parámetros
 * viajan con cada registro, de modo que subir el costo no invalida las cuentas ya guardadas.
 */
class PasswordHasher(
    private val random: SecureRandom = SecureRandom(),
    private val defaultIterations: Int = DEFAULT_ITERATIONS,
) {
    fun hash(password: String): PasswordDigest {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return PasswordDigest(
            algorithm = ALGORITHM,
            iterations = defaultIterations,
            salt = ENCODER.encodeToString(salt),
            hash = ENCODER.encodeToString(derive(password, salt, defaultIterations, ALGORITHM)),
        )
    }

    fun verify(password: String, digest: PasswordDigest): Boolean {
        val expected = try {
            DECODER.decode(digest.hash)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val salt = try {
            DECODER.decode(digest.salt)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val actual = derive(password, salt, digest.iterations, digest.algorithm)
        return MessageDigest.isEqual(expected, actual)
    }

    /** Hace el mismo trabajo que una verificación real para que un correo inexistente no se note por el tiempo de respuesta. */
    fun spendComparableEffort(password: String) {
        derive(password, DUMMY_SALT, defaultIterations, ALGORITHM)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int, algorithm: String): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val DEFAULT_ITERATIONS = 120_000
        const val SALT_BYTES = 16
        const val KEY_BITS = 256

        private val ENCODER = Base64.getEncoder()
        private val DECODER = Base64.getDecoder()
        private val DUMMY_SALT = ByteArray(SALT_BYTES)
    }
}
