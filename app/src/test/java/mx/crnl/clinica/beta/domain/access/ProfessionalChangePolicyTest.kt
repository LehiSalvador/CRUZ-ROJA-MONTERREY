package mx.crnl.clinica.beta.domain.access

import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfessionalChangePolicyTest {
    private val mariana = userAccount(id = "mariana", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)
    private val rodrigo = userAccount(id = "rodrigo", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)
    private val coordinator = userAccount(id = "claudia", role = UserRole.AREA_COORDINATOR, area = ClinicalArea.PSYCHOLOGY)
    private val admin = userAccount(id = "hector", role = UserRole.CLINICAL_ADMIN, area = null)
    private val system = userAccount(id = "system", role = UserRole.SYSTEM_ADMIN, area = null)
    private val psychology = ClinicalArea.PSYCHOLOGY

    @Test
    fun `el profesional asignado pide el cambio y otro profesional del area no`() {
        assertTrue(ProfessionalChangePolicy.canRequest(mariana, psychology, currentProfessionalId = "mariana"))
        assertFalse(ProfessionalChangePolicy.canRequest(rodrigo, psychology, currentProfessionalId = "mariana"))
    }

    @Test
    fun `la coordinacion del area y administracion clinica piden el cambio, el sistema no`() {
        assertTrue(ProfessionalChangePolicy.canRequest(coordinator, psychology, "mariana"))
        assertFalse(ProfessionalChangePolicy.canRequest(coordinator, ClinicalArea.NUTRITION, "paola"))
        assertTrue(ProfessionalChangePolicy.canRequest(admin, ClinicalArea.NUTRITION, "paola"))
        assertFalse(ProfessionalChangePolicy.canRequest(system, psychology, "mariana"))
    }

    @Test
    fun `un profesional no aprueba su propio cambio ni el de otro`() {
        assertFalse(ProfessionalChangePolicy.canReview(mariana, psychology, requesterId = "mariana"))
        assertFalse(ProfessionalChangePolicy.canReview(rodrigo, psychology, requesterId = "mariana"))
    }

    @Test
    fun `coordinacion del area y administracion clinica aprueban lo que otra persona pidio`() {
        assertTrue(ProfessionalChangePolicy.canReview(coordinator, psychology, "mariana"))
        assertTrue(ProfessionalChangePolicy.canReview(admin, psychology, "mariana"))
    }

    @Test
    fun `la coordinacion de otra area no aprueba`() {
        assertFalse(ProfessionalChangePolicy.canReview(coordinator, ClinicalArea.NUTRITION, "paola"))
    }

    @Test
    fun `quien pidio el cambio no lo resuelve aunque su rol lo permitiria`() {
        assertFalse(ProfessionalChangePolicy.canReview(coordinator, psychology, requesterId = "claudia"))
        assertFalse(ProfessionalChangePolicy.canReview(admin, psychology, requesterId = "hector"))
    }

    @Test
    fun `la bandeja muestra lo propio y lo que se revisa pero no al administrador del sistema`() {
        assertTrue(ProfessionalChangePolicy.canSee(mariana, psychology, "mariana"))
        assertFalse(ProfessionalChangePolicy.canSee(rodrigo, psychology, "mariana"))
        assertTrue(ProfessionalChangePolicy.canSee(coordinator, psychology, "mariana"))
        assertFalse(ProfessionalChangePolicy.canSee(system, psychology, "mariana"))
    }

    @Test
    fun `el sustituto debe ser un profesional activo del area y distinto del vigente`() {
        assertTrue(ProfessionalChangePolicy.isValidCandidate(rodrigo, psychology, "mariana"))
        assertFalse(ProfessionalChangePolicy.isValidCandidate(mariana, psychology, "mariana"))
        assertFalse(ProfessionalChangePolicy.isValidCandidate(rodrigo, ClinicalArea.NUTRITION, "paola"))
        assertFalse(ProfessionalChangePolicy.isValidCandidate(rodrigo.copy(status = AccountStatus.SUSPENDED), psychology, "mariana"))
        assertFalse(ProfessionalChangePolicy.isValidCandidate(coordinator, psychology, "mariana"))
    }

    @Test
    fun `el motivo se valida por longitud`() {
        assertFalse(ProfessionalChangePolicy.isValidReason("  corto  "))
        assertTrue(ProfessionalChangePolicy.isValidReason("Cambio por carga de agenda"))
        assertFalse(ProfessionalChangePolicy.isValidReason("x".repeat(ProfessionalChangePolicy.MAX_REASON_LENGTH + 1)))
    }
}
