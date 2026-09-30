package mx.crnl.clinica.beta.domain.access

import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BetaUserAdministrationPolicyTest {
    private val coordinator = userAccount(id = "coord", role = UserRole.AREA_COORDINATOR, area = ClinicalArea.PSYCHOLOGY)
    private val admin = userAccount(id = "admin", role = UserRole.CLINICAL_ADMIN, area = null)
    private val system = userAccount(id = "system", role = UserRole.SYSTEM_ADMIN, area = null)
    private val professional = userAccount(id = "pro", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)

    private fun pendingProfessional(area: ClinicalArea = ClinicalArea.PSYCHOLOGY) =
        userAccount(id = "nuevo", role = UserRole.PROFESSIONAL, area = area, status = AccountStatus.PENDING_APPROVAL)

    private fun pendingCoordinator(area: ClinicalArea = ClinicalArea.PSYCHOLOGY) =
        userAccount(id = "nuevo-coord", role = UserRole.AREA_COORDINATOR, area = area, status = AccountStatus.PENDING_APPROVAL)

    @Test
    fun `coordinacion aprueba y rechaza profesionales pendientes de su propia area`() {
        val target = pendingProfessional()

        assertTrue(BetaUserAdministrationPolicy.canApply(coordinator, target, AccountAction.APPROVE))
        assertTrue(BetaUserAdministrationPolicy.canApply(coordinator, target, AccountAction.REJECT))
    }

    @Test
    fun `coordinacion no revisa cuentas de otra area`() {
        val target = pendingProfessional(ClinicalArea.NUTRITION)

        assertFalse(BetaUserAdministrationPolicy.canApply(coordinator, target, AccountAction.APPROVE))
        assertFalse(BetaUserAdministrationPolicy.canApply(coordinator, target, AccountAction.REJECT))
        assertFalse(BetaUserAdministrationPolicy.canSee(coordinator, target))
    }

    @Test
    fun `coordinacion no aprueba a otra coordinacion`() {
        assertFalse(BetaUserAdministrationPolicy.canApply(coordinator, pendingCoordinator(), AccountAction.APPROVE))
        assertFalse(BetaUserAdministrationPolicy.canApply(coordinator, pendingCoordinator(), AccountAction.REJECT))
    }

    @Test
    fun `administracion clinica aprueba profesionales y coordinacion de cualquier area`() {
        assertTrue(BetaUserAdministrationPolicy.canApply(admin, pendingCoordinator(ClinicalArea.NUTRITION), AccountAction.APPROVE))
        assertTrue(BetaUserAdministrationPolicy.canApply(admin, pendingProfessional(ClinicalArea.GENERAL_MEDICINE), AccountAction.REJECT))
    }

    @Test
    fun `administracion clinica no aprueba una cuenta sin area clinica ni un administrador`() {
        val withoutArea = userAccount(id = "x", role = UserRole.PROFESSIONAL, area = null, status = AccountStatus.PENDING_APPROVAL)
        val otherAdmin = userAccount(id = "y", role = UserRole.CLINICAL_ADMIN, area = null, status = AccountStatus.PENDING_APPROVAL)

        assertFalse(BetaUserAdministrationPolicy.canApply(admin, withoutArea, AccountAction.APPROVE))
        assertFalse(BetaUserAdministrationPolicy.canApply(admin, otherAdmin, AccountAction.APPROVE))
    }

    @Test
    fun `un profesional o el administrador del sistema no aprueban nada`() {
        val target = pendingProfessional()

        assertTrue(BetaUserAdministrationPolicy.availableActions(professional, target).isEmpty())
        assertTrue(BetaUserAdministrationPolicy.availableActions(system, target).isEmpty())
    }

    @Test
    fun `nadie actua sobre su propia cuenta`() {
        val self = userAccount(id = "admin", role = UserRole.AREA_COORDINATOR, area = ClinicalArea.PSYCHOLOGY, status = AccountStatus.ACTIVE)

        assertTrue(BetaUserAdministrationPolicy.availableActions(admin.copy(userId = "admin"), self).isEmpty())
    }

    @Test
    fun `una accion solo es posible desde el estado que le corresponde`() {
        val active = userAccount(id = "a", role = UserRole.PROFESSIONAL, area = ClinicalArea.NUTRITION, status = AccountStatus.ACTIVE)
        val suspended = active.copy(userId = "b", status = AccountStatus.SUSPENDED)
        val rejected = active.copy(userId = "c", status = AccountStatus.REJECTED)

        assertEquals(setOf(AccountAction.SUSPEND), BetaUserAdministrationPolicy.availableActions(admin, active))
        assertEquals(setOf(AccountAction.REACTIVATE), BetaUserAdministrationPolicy.availableActions(admin, suspended))
        assertTrue("una cuenta rechazada no se reactiva", BetaUserAdministrationPolicy.availableActions(admin, rejected).isEmpty())
    }

    @Test
    fun `coordinacion no suspende ni reactiva`() {
        val active = userAccount(id = "a", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY, status = AccountStatus.ACTIVE)

        assertFalse(BetaUserAdministrationPolicy.canApply(coordinator, active, AccountAction.SUSPEND))
    }

    @Test
    fun `una cuenta inactiva no administra nada`() {
        val inactiveAdmin = admin.copy(status = AccountStatus.SUSPENDED)

        assertTrue(BetaUserAdministrationPolicy.availableActions(inactiveAdmin, pendingProfessional()).isEmpty())
        assertFalse(BetaUserAdministrationPolicy.canViewUserDirectory(inactiveAdmin))
    }

    @Test
    fun `el directorio lo abren administracion clinica y del sistema pero solo la clinica lo gestiona`() {
        assertTrue(BetaUserAdministrationPolicy.canViewUserDirectory(admin))
        assertTrue(BetaUserAdministrationPolicy.canViewUserDirectory(system))
        assertFalse(BetaUserAdministrationPolicy.canViewUserDirectory(coordinator))
        assertFalse(BetaUserAdministrationPolicy.canViewUserDirectory(professional))
        assertTrue(BetaUserAdministrationPolicy.canManageUsers(admin))
        assertFalse(BetaUserAdministrationPolicy.canManageUsers(system))
    }

    @Test
    fun `coordinacion solo ve profesionales de su area pendientes o rechazados`() {
        val active = userAccount(id = "a", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY, status = AccountStatus.ACTIVE)
        val rejected = active.copy(userId = "r", status = AccountStatus.REJECTED)

        assertTrue(BetaUserAdministrationPolicy.canSee(coordinator, pendingProfessional()))
        assertTrue(BetaUserAdministrationPolicy.canSee(coordinator, rejected))
        assertFalse(BetaUserAdministrationPolicy.canSee(coordinator, active))
        assertFalse(BetaUserAdministrationPolicy.canSee(professional, pendingProfessional()))
    }

    @Test
    fun `el administrador del sistema ve las cuentas pero no puede actuar sobre ellas`() {
        assertTrue(BetaUserAdministrationPolicy.canSee(system, pendingProfessional()))
        assertTrue(BetaUserAdministrationPolicy.availableActions(system, pendingProfessional()).isEmpty())
    }
}
