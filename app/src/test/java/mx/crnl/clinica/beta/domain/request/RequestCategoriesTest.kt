package mx.crnl.clinica.beta.domain.request

import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestCategoriesTest {
    @Test
    fun `un profesional ve sus solicitudes de acceso y de cambio, sin cuentas`() {
        val professional = userAccount(role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)

        assertEquals(listOf(RequestCategory.ACCESS, RequestCategory.CHANGES), RequestCategories.forUser(professional))
    }

    @Test
    fun `coordinacion y administracion clinica ven las tres bandejas`() {
        val all = listOf(RequestCategory.ACCOUNTS, RequestCategory.ACCESS, RequestCategory.CHANGES)

        assertEquals(all, RequestCategories.forUser(userAccount(role = UserRole.AREA_COORDINATOR, area = ClinicalArea.PSYCHOLOGY)))
        assertEquals(all, RequestCategories.forUser(userAccount(role = UserRole.CLINICAL_ADMIN, area = null)))
    }

    @Test
    fun `el administrador del sistema solo ve cuentas porque lo demas esta ligado a pacientes`() {
        assertEquals(listOf(RequestCategory.ACCOUNTS), RequestCategories.forUser(userAccount(role = UserRole.SYSTEM_ADMIN, area = null)))
    }

    @Test
    fun `una cuenta que no esta activa no ve ninguna bandeja`() {
        val suspended = userAccount(role = UserRole.CLINICAL_ADMIN, area = null, status = AccountStatus.SUSPENDED)

        assertEquals(emptyList<RequestCategory>(), RequestCategories.forUser(suspended))
    }

    @Test
    fun `la bandeja de cuentas muestra pendientes y rechazadas como historial`() {
        assertEquals(setOf(AccountStatus.PENDING_APPROVAL, AccountStatus.REJECTED), RequestCategories.ACCOUNT_STATUSES)
    }
}
