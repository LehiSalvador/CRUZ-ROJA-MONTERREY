package mx.crnl.clinica.beta.domain.access

import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BetaAdminAccessPolicyTest {
    private val professional = userAccount(id = "pro", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)
    private val coordinator = userAccount(id = "coord", role = UserRole.AREA_COORDINATOR, area = ClinicalArea.PSYCHOLOGY)
    private val admin = userAccount(id = "admin", role = UserRole.CLINICAL_ADMIN, area = null)
    private val system = userAccount(id = "system", role = UserRole.SYSTEM_ADMIN, area = null)

    @Test
    fun `la bitacora la consultan administracion clinica y del sistema`() {
        assertTrue(BetaAdminAccessPolicy.canViewAudit(admin))
        assertTrue(BetaAdminAccessPolicy.canViewAudit(system))
        assertFalse(BetaAdminAccessPolicy.canViewAudit(coordinator))
        assertFalse(BetaAdminAccessPolicy.canViewAudit(professional))
    }

    @Test
    fun `restablecer los datos solo lo hace administracion clinica`() {
        assertTrue(BetaAdminAccessPolicy.canResetBetaData(admin))
        assertFalse(BetaAdminAccessPolicy.canResetBetaData(system))
        assertFalse(BetaAdminAccessPolicy.canResetBetaData(coordinator))
        assertFalse(BetaAdminAccessPolicy.canResetBetaData(professional))
    }

    @Test
    fun `una cuenta que no esta activa no usa ninguna herramienta`() {
        val suspended = admin.copy(status = AccountStatus.SUSPENDED)

        assertFalse(BetaAdminAccessPolicy.canViewAudit(suspended))
        assertFalse(BetaAdminAccessPolicy.canResetBetaData(suspended))
        assertFalse(BetaAdminAccessPolicy.hasAdministration(suspended))
    }

    @Test
    fun `el perfil ofrece la seccion de administracion solo a quien tiene alguna herramienta`() {
        assertTrue(BetaAdminAccessPolicy.hasAdministration(admin))
        assertTrue(BetaAdminAccessPolicy.hasAdministration(system))
        assertFalse(BetaAdminAccessPolicy.hasAdministration(coordinator))
        assertFalse(BetaAdminAccessPolicy.hasAdministration(professional))
    }
}
