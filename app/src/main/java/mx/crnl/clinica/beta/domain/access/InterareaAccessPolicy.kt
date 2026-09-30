package mx.crnl.clinica.beta.domain.access

import java.time.Duration
import java.time.Instant
import mx.crnl.clinica.beta.domain.model.AccessGrant
import mx.crnl.clinica.beta.domain.model.AccessGrantStatus
import mx.crnl.clinica.beta.domain.model.AccessScope
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole

/**
 * Acceso interárea de solo lectura en la Beta local. Una concesión vigente añade únicamente la lectura de un área de
 * un paciente concreto a una persona concreta: no cambia asignaciones y nunca habilita agendar, registrar atención,
 * editar, exportar ni documentos. Es una defensa de la aplicación, no seguridad de producción, y su matriz de
 * revisión es provisional.
 */
object InterareaAccessPolicy {
    /** Vigencia que la pantalla de revisión propone; quien revisa puede cambiarla antes de aprobar. Solo conveniencia de la Beta. */
    const val DEFAULT_DURATION_DAYS = 7
    val DURATION_OPTIONS_DAYS: List<Int> = listOf(1, 7, 30)
    const val MIN_REASON_LENGTH = 10
    const val MAX_REASON_LENGTH = 300

    fun isValidDuration(days: Int): Boolean = days in DURATION_OPTIONS_DAYS

    fun isValidReason(reason: String): Boolean = reason.trim().length in MIN_REASON_LENGTH..MAX_REASON_LENGTH

    /** Quién pide lectura de un área que su rol no le deja ver. Administración clínica ya ve todo y el sistema no lee clínica. */
    fun canRequest(user: UserAccount, area: ClinicalArea): Boolean =
        user.status == AccountStatus.ACTIVE &&
            (user.role == UserRole.PROFESSIONAL || user.role == UserRole.AREA_COORDINATOR) &&
            area !in BetaClinicalAccessPolicy.viewableAreas(user)

    /** Coordinación del área propietaria o administración clínica, y nunca la misma persona que la solicitó. */
    fun canReview(reviewer: UserAccount, ownerArea: ClinicalArea, requesterId: String): Boolean =
        reviewer.userId != requesterId && canManageArea(reviewer, ownerArea)

    fun canRevoke(user: UserAccount, ownerArea: ClinicalArea): Boolean = canManageArea(user, ownerArea)

    /** Si el propio solicitante o quien revisa el área puede ver la solicitud en su bandeja. El sistema no ve solicitudes de pacientes. */
    fun canSeeRequest(viewer: UserAccount, ownerArea: ClinicalArea, requesterId: String): Boolean =
        viewer.status == AccountStatus.ACTIVE &&
            viewer.role != UserRole.SYSTEM_ADMIN &&
            (viewer.userId == requesterId || canManageArea(viewer, ownerArea))

    fun expiresAt(from: Instant, days: Int): Instant = from.plus(Duration.ofDays(days.toLong()))

    /** Una concesión rige solo para su titular activo, dentro de su vigencia y mientras no se revoque. */
    fun isEffective(grant: AccessGrant, user: UserAccount, now: Instant): Boolean =
        grant.scope == AccessScope.READ &&
            grant.granteeUserId == user.userId &&
            user.status == AccountStatus.ACTIVE &&
            grant.storedStatus == AccessGrantStatus.ACTIVE &&
            !now.isBefore(grant.validFrom) &&
            now.isBefore(grant.expiresAt)

    /**
     * Las áreas de [patientId] que [grants] permiten leer a [user] ahora. Si hubiera dos concesiones para la misma área
     * rige la que vence más tarde. El resultado nunca incluye acciones: es únicamente lectura.
     */
    fun readableAreas(user: UserAccount, grants: List<AccessGrant>, patientId: String, now: Instant): Map<ClinicalArea, AccessGrant> =
        grants
            .filter { it.patientId == patientId && isEffective(it, user, now) }
            .groupBy { it.ownerArea }
            .mapValues { (_, byArea) -> byArea.maxBy { it.expiresAt } }

    private fun canManageArea(user: UserAccount, area: ClinicalArea): Boolean = user.status == AccountStatus.ACTIVE &&
        (user.role == UserRole.CLINICAL_ADMIN || (user.role == UserRole.AREA_COORDINATOR && user.area == area))
}
