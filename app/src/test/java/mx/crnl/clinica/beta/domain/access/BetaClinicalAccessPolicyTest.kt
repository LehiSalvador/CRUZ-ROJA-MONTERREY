package mx.crnl.clinica.beta.domain.access

import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ClinicalArea.GENERAL_MEDICINE
import mx.crnl.clinica.beta.domain.model.ClinicalArea.NUTRITION
import mx.crnl.clinica.beta.domain.model.ClinicalArea.PSYCHOLOGY
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BetaClinicalAccessPolicyTest {
    private val psychologist = userAccount(id = "psy", role = UserRole.PROFESSIONAL, area = PSYCHOLOGY)
    private val nutritionist = userAccount(id = "nut", role = UserRole.PROFESSIONAL, area = NUTRITION)
    private val coordinator = userAccount(id = "coord", role = UserRole.AREA_COORDINATOR, area = PSYCHOLOGY)
    private val clinicalAdmin = userAccount(id = "admin", role = UserRole.CLINICAL_ADMIN, area = null)
    private val systemAdmin = userAccount(id = "sys", role = UserRole.SYSTEM_ADMIN, area = null)

    private fun inactive(role: UserRole, area: ClinicalArea?) =
        AccountStatus.entries.filter { it != AccountStatus.ACTIVE }.map { userAccount(id = "x-$it", role = role, area = area, status = it) }

    // ---- ver el detalle de un área ----

    @Test
    fun `un profesional ve el detalle de su propia area y no el de las demas`() {
        assertTrue(BetaClinicalAccessPolicy.canViewAreaDetail(psychologist, PSYCHOLOGY))
        assertFalse(BetaClinicalAccessPolicy.canViewAreaDetail(psychologist, NUTRITION))
        assertFalse(BetaClinicalAccessPolicy.canViewAreaDetail(psychologist, GENERAL_MEDICINE))
        assertEquals(setOf(PSYCHOLOGY), BetaClinicalAccessPolicy.viewableAreas(psychologist))
    }

    @Test
    fun `coordinacion ve solo el detalle de su area`() {
        assertTrue(BetaClinicalAccessPolicy.canViewAreaDetail(coordinator, PSYCHOLOGY))
        assertFalse(BetaClinicalAccessPolicy.canViewAreaDetail(coordinator, NUTRITION))
        assertEquals(setOf(PSYCHOLOGY), BetaClinicalAccessPolicy.viewableAreas(coordinator))
    }

    @Test
    fun `administracion clinica ve las tres areas`() {
        for (area in ClinicalArea.entries) assertTrue(BetaClinicalAccessPolicy.canViewAreaDetail(clinicalAdmin, area))
        assertEquals(ClinicalArea.entries.toSet(), BetaClinicalAccessPolicy.viewableAreas(clinicalAdmin))
    }

    @Test
    fun `el administrador del sistema no obtiene capacidad clinica por serlo`() {
        for (area in ClinicalArea.entries) {
            assertFalse(BetaClinicalAccessPolicy.canViewAreaDetail(systemAdmin, area))
            assertFalse(BetaClinicalAccessPolicy.canCreateAssignment(systemAdmin, area))
            assertFalse(BetaClinicalAccessPolicy.canManageAppointment(systemAdmin, area, "pro"))
            assertFalse(BetaClinicalAccessPolicy.canCreateEncounter(systemAdmin, area, "pro", assignedProfessionalId = "pro"))
        }
        assertTrue(BetaClinicalAccessPolicy.viewableAreas(systemAdmin).isEmpty())
        assertEquals(AppointmentScope.None, BetaClinicalAccessPolicy.appointmentScope(systemAdmin))
    }

    @Test
    fun `una cuenta que no esta activa no puede nada`() {
        for (user in inactive(UserRole.PROFESSIONAL, PSYCHOLOGY) + inactive(UserRole.AREA_COORDINATOR, PSYCHOLOGY) + inactive(UserRole.CLINICAL_ADMIN, null)) {
            assertFalse(user.status.name, BetaClinicalAccessPolicy.canViewAreaDetail(user, PSYCHOLOGY))
            assertFalse(user.status.name, BetaClinicalAccessPolicy.canCreateAssignment(user, PSYCHOLOGY))
            assertFalse(user.status.name, BetaClinicalAccessPolicy.canManageAppointment(user, PSYCHOLOGY, user.userId))
            assertFalse(user.status.name, BetaClinicalAccessPolicy.canCreateEncounter(user, PSYCHOLOGY, user.userId, user.userId))
            assertEquals(AppointmentScope.None, BetaClinicalAccessPolicy.appointmentScope(user))
            assertTrue(BetaClinicalAccessPolicy.viewableAreas(user).isEmpty())
        }
    }

    // ---- asignación inicial ----

    @Test
    fun `un profesional nunca puede asignar pacientes, ni siquiera a si mismo`() {
        for (area in ClinicalArea.entries) assertFalse(BetaClinicalAccessPolicy.canCreateAssignment(psychologist, area))
    }

    @Test
    fun `coordinacion asigna solo en su area`() {
        assertTrue(BetaClinicalAccessPolicy.canCreateAssignment(coordinator, PSYCHOLOGY))
        assertFalse(BetaClinicalAccessPolicy.canCreateAssignment(coordinator, NUTRITION))
        assertFalse(BetaClinicalAccessPolicy.canCreateAssignment(coordinator, GENERAL_MEDICINE))
    }

    @Test
    fun `administracion clinica asigna en las tres areas`() {
        for (area in ClinicalArea.entries) assertTrue(BetaClinicalAccessPolicy.canCreateAssignment(clinicalAdmin, area))
    }

    // ---- citas ----

    @Test
    fun `un profesional gestiona solo las citas en las que es el profesional y dentro de su area`() {
        assertTrue(BetaClinicalAccessPolicy.canManageAppointment(psychologist, PSYCHOLOGY, "psy"))
        assertFalse(BetaClinicalAccessPolicy.canManageAppointment(psychologist, PSYCHOLOGY, "otro"))
        assertFalse(BetaClinicalAccessPolicy.canManageAppointment(psychologist, NUTRITION, "psy"))
    }

    @Test
    fun `coordinacion gestiona las citas de su area sin importar el profesional`() {
        assertTrue(BetaClinicalAccessPolicy.canManageAppointment(coordinator, PSYCHOLOGY, "cualquiera"))
        assertFalse(BetaClinicalAccessPolicy.canManageAppointment(coordinator, NUTRITION, "cualquiera"))
    }

    @Test
    fun `administracion clinica gestiona las citas de todas las areas`() {
        for (area in ClinicalArea.entries) assertTrue(BetaClinicalAccessPolicy.canManageAppointment(clinicalAdmin, area, "pro"))
    }

    @Test
    fun `el alcance de la agenda depende del rol`() {
        assertEquals(AppointmentScope.OwnAsProfessional("psy"), BetaClinicalAccessPolicy.appointmentScope(psychologist))
        assertEquals(AppointmentScope.InArea(PSYCHOLOGY), BetaClinicalAccessPolicy.appointmentScope(coordinator))
        assertEquals(AppointmentScope.All, BetaClinicalAccessPolicy.appointmentScope(clinicalAdmin))
        assertEquals(AppointmentScope.None, BetaClinicalAccessPolicy.appointmentScope(systemAdmin))
    }

    @Test
    fun `cada alcance acepta solo las citas que le corresponden`() {
        assertTrue(AppointmentScope.OwnAsProfessional("psy").accepts(PSYCHOLOGY, "psy"))
        assertFalse(AppointmentScope.OwnAsProfessional("psy").accepts(PSYCHOLOGY, "otro"))
        assertTrue(AppointmentScope.InArea(NUTRITION).accepts(NUTRITION, "cualquiera"))
        assertFalse(AppointmentScope.InArea(NUTRITION).accepts(PSYCHOLOGY, "cualquiera"))
        assertTrue(AppointmentScope.All.accepts(GENERAL_MEDICINE, "x"))
        assertFalse(AppointmentScope.None.accepts(PSYCHOLOGY, "x"))
    }

    @Test
    fun `las areas en las que se puede agendar dependen del rol`() {
        assertEquals(setOf(PSYCHOLOGY), BetaClinicalAccessPolicy.manageableAreas(psychologist))
        assertEquals(setOf(PSYCHOLOGY), BetaClinicalAccessPolicy.manageableAreas(coordinator))
        assertEquals(ClinicalArea.entries.toSet(), BetaClinicalAccessPolicy.manageableAreas(clinicalAdmin))
        assertTrue(BetaClinicalAccessPolicy.manageableAreas(systemAdmin).isEmpty())
    }

    // ---- encuentros ----

    @Test
    fun `un profesional registra encuentros solo de pacientes asignados a el, en su area`() {
        assertTrue(BetaClinicalAccessPolicy.canCreateEncounter(psychologist, PSYCHOLOGY, "psy", assignedProfessionalId = "psy"))
    }

    @Test
    fun `un profesional no registra encuentros de un paciente asignado a otro profesional`() {
        assertFalse(BetaClinicalAccessPolicy.canCreateEncounter(psychologist, PSYCHOLOGY, "psy", assignedProfessionalId = "otro"))
        assertFalse(BetaClinicalAccessPolicy.canCreateEncounter(psychologist, PSYCHOLOGY, "otro", assignedProfessionalId = "otro"))
    }

    @Test
    fun `un profesional no registra encuentros de otra area aunque el paciente este asignado a el`() {
        assertFalse(BetaClinicalAccessPolicy.canCreateEncounter(nutritionist, PSYCHOLOGY, "nut", assignedProfessionalId = "nut"))
    }

    @Test
    fun `sin profesional asignado nadie registra encuentros`() {
        assertFalse(BetaClinicalAccessPolicy.canCreateEncounter(psychologist, PSYCHOLOGY, "psy", assignedProfessionalId = null))
        assertFalse(BetaClinicalAccessPolicy.canCreateEncounter(coordinator, PSYCHOLOGY, "psy", assignedProfessionalId = null))
        assertFalse(BetaClinicalAccessPolicy.canCreateEncounter(clinicalAdmin, PSYCHOLOGY, "psy", assignedProfessionalId = null))
    }

    @Test
    fun `el encuentro siempre se atribuye al profesional asignado`() {
        assertFalse(BetaClinicalAccessPolicy.canCreateEncounter(coordinator, PSYCHOLOGY, "otro", assignedProfessionalId = "psy"))
        assertTrue(BetaClinicalAccessPolicy.canCreateEncounter(coordinator, PSYCHOLOGY, "psy", assignedProfessionalId = "psy"))
    }

    @Test
    fun `coordinacion registra encuentros solo en su area`() {
        assertTrue(BetaClinicalAccessPolicy.canCreateEncounter(coordinator, PSYCHOLOGY, "psy", assignedProfessionalId = "psy"))
        assertFalse(BetaClinicalAccessPolicy.canCreateEncounter(coordinator, NUTRITION, "nut", assignedProfessionalId = "nut"))
    }

    @Test
    fun `administracion clinica registra encuentros en las tres areas`() {
        for (area in ClinicalArea.entries) {
            assertTrue(BetaClinicalAccessPolicy.canCreateEncounter(clinicalAdmin, area, "pro", assignedProfessionalId = "pro"))
        }
    }
}
