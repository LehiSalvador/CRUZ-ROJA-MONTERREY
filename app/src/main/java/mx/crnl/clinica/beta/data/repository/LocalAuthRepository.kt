package mx.crnl.clinica.beta.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.core.security.PasswordDigest
import mx.crnl.clinica.beta.core.security.PasswordHasher
import mx.crnl.clinica.beta.data.local.entity.DemoCredentialEntity
import mx.crnl.clinica.beta.data.local.entity.DemoUserEntity
import mx.crnl.clinica.beta.data.local.mapper.toDomain
import mx.crnl.clinica.beta.domain.account.AccountRequest
import mx.crnl.clinica.beta.domain.account.AccountRequestValidator
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.AuditAction
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AccountRequestResult
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.SessionRepository
import mx.crnl.clinica.beta.domain.repository.SignInResult
import mx.crnl.clinica.beta.domain.text.TextNormalizer

/** La decisión de acceso vive aquí: la interfaz solo muestra el resultado. */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalAuthRepository(
    private val database: ClinicalDatabase,
    private val sessionRepository: SessionRepository,
    private val passwordHasher: PasswordHasher,
    private val audit: AuditRecorder,
    private val clock: Clock,
    private val idGenerator: () -> String,
) : AuthRepository {
    private val userDao = database.userDao()
    private val credentialDao = database.credentialDao()

    override val currentUser: Flow<UserAccount?> = sessionRepository.sessionUserId.flatMapLatest { userId ->
        if (userId == null) {
            flowOf(null)
        } else {
            userDao.observeById(userId).map { entity -> entity?.toDomain()?.takeIf { it.status == AccountStatus.ACTIVE } }
        }
    }

    override suspend fun restoreSession(): UserAccount? {
        val userId = sessionUserId() ?: return null
        val user = userDao.getById(userId)?.toDomain()?.takeIf { it.status == AccountStatus.ACTIVE }
        if (user == null) sessionRepository.endSession()
        return user
    }

    override suspend fun signIn(email: String, password: String): SignInResult {
        val normalizedEmail = TextNormalizer.email(email)
        if (normalizedEmail.isEmpty() || password.isEmpty()) return SignInResult.InvalidCredentials

        val entity = userDao.findByEmail(normalizedEmail)
        val credential = entity?.let { credentialDao.getByUserId(it.userId) }
        val passwordMatches = withContext(Dispatchers.Default) {
            if (credential == null) {
                passwordHasher.spendComparableEffort(password)
                false
            } else {
                passwordHasher.verify(password, credential.toDigest())
            }
        }
        if (entity == null || !passwordMatches) return SignInResult.InvalidCredentials

        val user = entity.toDomain()
        if (user.status != AccountStatus.ACTIVE) return SignInResult.AccountNotActive(user.status)

        try {
            sessionRepository.startSession(user.userId)
            audit.record(AuditAction.LOGIN, actorUserId = user.userId, entityType = ENTITY_USER, entityId = user.userId)
        } catch (error: Exception) {
            runCatching { sessionRepository.endSession() }
            throw error
        }
        return SignInResult.Success(user)
    }

    override suspend fun signOut() {
        val userId = sessionUserId()
        try {
            if (userId != null) {
                audit.record(AuditAction.LOGOUT, actorUserId = userId, entityType = ENTITY_USER, entityId = userId)
            }
        } finally {
            sessionRepository.endSession()
        }
    }

    override suspend fun requestAccount(request: AccountRequest): AccountRequestResult {
        val issues = AccountRequestValidator.validate(request)
        if (issues.isNotEmpty()) return AccountRequestResult.Invalid(issues)

        val email = TextNormalizer.email(request.email)
        if (userDao.findByEmail(email) != null) return AccountRequestResult.EmailAlreadyRegistered

        val digest = withContext(Dispatchers.Default) { passwordHasher.hash(request.password) }
        val role = requireNotNull(request.role)
        val area = requireNotNull(request.area)
        val userId = idGenerator()
        val now = clock.millis()
        try {
            database.withTransaction {
                userDao.insert(
                    DemoUserEntity(
                        userId = userId,
                        firstName = TextNormalizer.tidy(request.firstName),
                        paternalSurname = TextNormalizer.tidy(request.paternalSurname),
                        maternalSurname = TextNormalizer.tidy(request.maternalSurname).ifEmpty { null },
                        email = email,
                        roleCode = role.name,
                        areaCode = area.name,
                        status = AccountStatus.PENDING_APPROVAL.name,
                        createdAt = now,
                        updatedAt = now,
                        professionalLicense = request.professionalLicense.trim().ifEmpty { null },
                    ),
                )
                credentialDao.insert(
                    DemoCredentialEntity(
                        userId = userId,
                        algorithm = digest.algorithm,
                        iterations = digest.iterations,
                        salt = digest.salt,
                        passwordHash = digest.hash,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                audit.record(
                    AuditAction.USER_REQUESTED,
                    actorUserId = userId,
                    entityType = ENTITY_USER,
                    entityId = userId,
                    metadata = mapOf("role" to AuditRecorder.text(role.name), "area" to AuditRecorder.text(area.name)),
                )
            }
        } catch (error: SQLiteConstraintException) {
            // Dos solicitudes simultáneas con el mismo correo: la restricción única deja pasar solo una.
            if (userDao.findByEmail(email) != null) return AccountRequestResult.EmailAlreadyRegistered
            throw error
        }
        return AccountRequestResult.Submitted
    }

    private suspend fun sessionUserId(): String? = sessionRepository.sessionUserId.first()

    private fun DemoCredentialEntity.toDigest() = PasswordDigest(algorithm, iterations, salt, passwordHash)

    private companion object {
        const val ENTITY_USER = "USER"
    }
}
