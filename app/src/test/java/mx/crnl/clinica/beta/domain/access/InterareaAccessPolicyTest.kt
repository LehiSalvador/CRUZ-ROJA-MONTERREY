package mx.crnl.clinica.beta.domain.access

import java.time.Instant
import mx.crnl.clinica.beta.domain.model.AccessGrant
import mx.crnl.clinica.beta.domain.model.AccessGrantStatus
import mx.crnl.clinica.beta.domain.model.AccessScope
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterareaAccessPolicyTest {
    private val now = Instant.parse("2026-09-29T16:00:00Z")
    private val mariana = userAccount(id = "mariana", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)
    private val paola = userAccount(id = "paola", role = UserRole.PROFESSIONAL, area = ClinicalArea.NUTRITION)
    private val coordinator = userAccount(id = "claudia", role = UserRole.AREA_COORDINATOR, area = ClinicalArea.PSYCHOLOGY)
    private val admin = userAccount(id = "hector", role = UserRole.CLINICAL_ADMIN, area = null)
    private val system = userAccount(id = "system", role = UserRole.SYSTEM_ADMIN, area = null)

    private fun grant(
        grantee: String = "mariana",
        patient: String = "p1",
        area: ClinicalArea = ClinicalArea.NUTRITION,
        from: Instant = now.minusSeconds(60),
        until: Instant = now.plusSeconds(3_600),
        status: AccessGrantStatus = AccessGrantStatus.ACTIVE,
        scope: AccessScope = AccessScope.READ,
    ) = AccessGrant("g-$grantee-$area", "r1", patient, grantee, area, scope, from, until, status, revokedAt = null)

    // ---------------------------------------------------------------- pedir

    @Test
    fun `profesional y coordinacion piden lectura de un area que su rol no ve`() {
        assertTrue(InterareaAccessPolicy.canRequest(mariana, ClinicalArea.NUTRITION))
        assertTrue(InterareaAccessPolicy.canRequest(coordinator, ClinicalArea.GENERAL_MEDICINE))
    }

    @Test
    fun `no se pide lectura de un area que ya se ve ni la piden administracion clinica o del sistema`() {
        assertFalse(InterareaAccessPolicy.canRequest(mariana, ClinicalArea.PSYCHOLOGY))
        assertFalse(InterareaAccessPolicy.canRequest(admin, ClinicalArea.NUTRITION))
        assertFalse(InterareaAccessPolicy.canRequest(system, ClinicalArea.NUTRITION))
    }

    @Test
    fun `una cuenta que no esta activa no pide acceso`() {
        assertFalse(InterareaAccessPolicy.canRequest(mariana.copy(status = AccountStatus.SUSPENDED), ClinicalArea.NUTRITION))
    }

    // ---------------------------------------------------------------- revisar

    @Test
    fun `revisa la coordinacion del area propietaria y administracion clinica`() {
        assertTrue(InterareaAccessPolicy.canReview(paola.copy(role = UserRole.AREA_COORDINATOR), ClinicalArea.NUTRITION, "mariana"))
        assertTrue(InterareaAccessPolicy.canReview(admin, ClinicalArea.NUTRITION, "mariana"))
    }

    @Test
    fun `la coordinacion de otra area no revisa`() {
        assertFalse(InterareaAccessPolicy.canReview(coordinator, ClinicalArea.NUTRITION, "mariana"))
    }

    @Test
    fun `quien la pidio no la resuelve aunque tenga rol de revision`() {
        assertFalse(InterareaAccessPolicy.canReview(coordinator, ClinicalArea.GENERAL_MEDICINE, "claudia"))
        assertFalse(InterareaAccessPolicy.canReview(admin, ClinicalArea.NUTRITION, "hector"))
    }

    @Test
    fun `un profesional o el administrador del sistema no revisan ni revocan`() {
        assertFalse(InterareaAccessPolicy.canReview(paola, ClinicalArea.NUTRITION, "mariana"))
        assertFalse(InterareaAccessPolicy.canReview(system, ClinicalArea.NUTRITION, "mariana"))
        assertFalse(InterareaAccessPolicy.canRevoke(paola, ClinicalArea.NUTRITION))
        assertFalse(InterareaAccessPolicy.canRevoke(system, ClinicalArea.NUTRITION))
    }

    @Test
    fun `revoca la coordinacion del area propietaria y administracion clinica`() {
        assertTrue(InterareaAccessPolicy.canRevoke(coordinator, ClinicalArea.PSYCHOLOGY))
        assertFalse(InterareaAccessPolicy.canRevoke(coordinator, ClinicalArea.NUTRITION))
        assertTrue(InterareaAccessPolicy.canRevoke(admin, ClinicalArea.NUTRITION))
    }

    // ---------------------------------------------------------------- ver la bandeja

    @Test
    fun `la bandeja muestra lo propio y lo que se revisa pero nunca al administrador del sistema`() {
        assertTrue(InterareaAccessPolicy.canSeeRequest(mariana, ClinicalArea.NUTRITION, "mariana"))
        assertFalse(InterareaAccessPolicy.canSeeRequest(paola, ClinicalArea.NUTRITION, "mariana"))
        assertTrue(InterareaAccessPolicy.canSeeRequest(admin, ClinicalArea.NUTRITION, "mariana"))
        assertFalse(InterareaAccessPolicy.canSeeRequest(system, ClinicalArea.NUTRITION, "mariana"))
    }

    // ---------------------------------------------------------------- vigencia

    @Test
    fun `una concesion activa y vigente rige para su titular`() {
        assertTrue(InterareaAccessPolicy.isEffective(grant(), mariana, now))
    }

    @Test
    fun `una concesion vencida no rige y vence justo en el instante de expiracion`() {
        val g = grant(until = now)

        assertFalse(InterareaAccessPolicy.isEffective(g, mariana, now))
        assertTrue(InterareaAccessPolicy.isEffective(g, mariana, now.minusMillis(1)))
    }

    @Test
    fun `una concesion revocada o que aun no empieza no rige`() {
        assertFalse(InterareaAccessPolicy.isEffective(grant(status = AccessGrantStatus.REVOKED), mariana, now))
        assertFalse(InterareaAccessPolicy.isEffective(grant(from = now.plusSeconds(1)), mariana, now))
    }

    @Test
    fun `una concesion no rige para otra persona ni para una cuenta que dejo de estar activa`() {
        assertFalse(InterareaAccessPolicy.isEffective(grant(), paola, now))
        assertFalse(InterareaAccessPolicy.isEffective(grant(), mariana.copy(status = AccountStatus.SUSPENDED), now))
    }

    @Test
    fun `solo las areas del paciente indicado se abren y rige la que vence mas tarde`() {
        val soon = grant(until = now.plusSeconds(60))
        val later = grant(until = now.plusSeconds(7_200))
        val otherPatient = grant(patient = "p2", area = ClinicalArea.GENERAL_MEDICINE)

        val areas = InterareaAccessPolicy.readableAreas(mariana, listOf(soon, later, otherPatient), "p1", now)

        assertEquals(setOf(ClinicalArea.NUTRITION), areas.keys)
        assertEquals(later.expiresAt, areas.getValue(ClinicalArea.NUTRITION).expiresAt)
    }

    @Test
    fun `la duracion y el motivo se validan`() {
        assertTrue(InterareaAccessPolicy.isValidDuration(InterareaAccessPolicy.DEFAULT_DURATION_DAYS))
        assertFalse(InterareaAccessPolicy.isValidDuration(0))
        assertFalse(InterareaAccessPolicy.isValidDuration(365))
        assertFalse(InterareaAccessPolicy.isValidReason("corto"))
        assertTrue(InterareaAccessPolicy.isValidReason("Necesito revisar el seguimiento"))
        assertFalse(InterareaAccessPolicy.isValidReason("x".repeat(InterareaAccessPolicy.MAX_REASON_LENGTH + 1)))
    }

    @Test
    fun `el vencimiento se calcula en dias completos desde el momento de aprobar`() {
        assertEquals(now.plusSeconds(7 * 86_400L), InterareaAccessPolicy.expiresAt(now, 7))
    }
}
