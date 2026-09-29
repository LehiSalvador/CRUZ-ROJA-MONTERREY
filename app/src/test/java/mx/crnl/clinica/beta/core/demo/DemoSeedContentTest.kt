package mx.crnl.clinica.beta.core.demo

import java.io.File
import java.time.LocalDate
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.testing.FileSystemSeedReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSeedContentTest {
    private val seed = DemoSeedLoader(FileSystemSeedReader()).load()

    @Test
    fun `todos los archivos declaran datos ficticios de la version soportada`() {
        assertEquals(6, seed.metas.size)
        seed.metas.forEach { (file, meta) ->
            assertTrue("$file debe declararse ficticio", meta.fictitious)
            assertEquals("$file: versión", DemoSeed.SUPPORTED_VERSION, meta.seedVersion)
        }
    }

    @Test
    fun `el conjunto cumple todas las validaciones`() {
        val issues = DemoSeedValidator(today = LocalDate.of(2026, 9, 29)).validate(seed)
        assertEquals(emptyList<String>(), issues)
    }

    @Test
    fun `tiene la poblacion pedida para una Beta poblada`() {
        assertTrue("pacientes: ${seed.patients.size}", seed.patients.size in 6..8)
        assertEquals(4, seed.users.count { it.role == "PROFESSIONAL" })
        assertEquals(1, seed.users.count { it.role == "AREA_COORDINATOR" })
        assertEquals(1, seed.users.count { it.role == "CLINICAL_ADMIN" })
        assertTrue("citas: ${seed.appointments.size}", seed.appointments.size in 8..12)
        assertTrue(seed.encounters.size >= 4)
        assertTrue(seed.assignments.size >= seed.patients.size)
        assertTrue(seed.assessments.any { it.result != null })
    }

    @Test
    fun `las citas cubren todos los estados admitidos`() {
        val expected = AppointmentStatus.entries.map { it.name }.toSet()
        assertEquals(expected, seed.appointments.map { it.status }.toSet())
    }

    @Test
    fun `el historial de asignaciones se conserva en lugar de sobrescribirse`() {
        val withHistory = seed.assignments.groupBy { it.patientId to it.area }.filterValues { it.size > 1 }
        assertTrue("debe existir al menos un paciente reasignado", withHistory.isNotEmpty())
        withHistory.values.forEach { assignments ->
            assertEquals(1, assignments.count { it.status == "ACTIVE" })
            assertTrue(assignments.any { it.status == "ENDED" })
        }
    }

    @Test
    fun `ningun archivo contiene correos de dominios reales`() {
        val allowed = setOf("example.org", "example.com", "example.net")
        val domains = Regex("@([A-Za-z0-9.-]+)")
        listOf(
            DemoSeed.USERS_FILE,
            DemoSeed.PATIENTS_FILE,
            DemoSeed.ASSIGNMENTS_FILE,
            DemoSeed.APPOINTMENTS_FILE,
            DemoSeed.ENCOUNTERS_FILE,
            DemoSeed.ASSESSMENTS_FILE,
        ).forEach { file ->
            val text = File("src/main/assets", file).readText(Charsets.UTF_8)
            val real = domains.findAll(text).map { it.groupValues[1].lowercase() }.filterNot { it in allowed }.toList()
            assertEquals("$file contiene dominios no reservados", emptyList<String>(), real)
        }
    }
}
