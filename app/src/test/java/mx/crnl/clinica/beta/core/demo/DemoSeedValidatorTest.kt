package mx.crnl.clinica.beta.core.demo

import java.time.LocalDate
import mx.crnl.clinica.beta.testing.FileSystemSeedReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSeedValidatorTest {
    private val baseline = DemoSeedLoader(FileSystemSeedReader()).load()
    private val validator = DemoSeedValidator(today = LocalDate.of(2026, 9, 29))

    private val unknownId = "99999999-9999-4999-8999-999999999999"

    private fun assertIssue(seed: DemoSeed, fragment: String) {
        val issues = validator.validate(seed)
        assertTrue("Se esperaba un problema con «$fragment», pero se obtuvo: $issues", issues.any { fragment in it })
    }

    private fun DemoSeed.mapPatients(transform: (SeedPatient) -> SeedPatient) = copy(patients = patients.map(transform))

    private fun DemoSeed.mapAppointments(transform: (SeedAppointment) -> SeedAppointment) =
        copy(appointments = appointments.map(transform))

    private fun DemoSeed.mapFirstAppointment(transform: (SeedAppointment) -> SeedAppointment): DemoSeed {
        val first = appointments.first()
        return copy(appointments = listOf(transform(first)) + appointments.drop(1))
    }

    @Test
    fun `el conjunto base no tiene problemas`() {
        assertEquals(emptyList<String>(), validator.validate(baseline))
    }

    @Test
    fun `rechaza datos que no se declaran ficticios`() {
        val meta = baseline.metas.getValue(DemoSeed.USERS_FILE).copy(fictitious = false)
        assertIssue(baseline.copy(metas = baseline.metas + (DemoSeed.USERS_FILE to meta)), "meta.fictitious")
    }

    @Test
    fun `rechaza una version de datos que la compilacion no soporta`() {
        val meta = baseline.metas.getValue(DemoSeed.PATIENTS_FILE).copy(seedVersion = 2)
        assertIssue(baseline.copy(metas = baseline.metas + (DemoSeed.PATIENTS_FILE to meta)), "meta.seedVersion")
    }

    @Test
    fun `rechaza ids duplicados`() {
        assertIssue(baseline.copy(patients = baseline.patients + baseline.patients.first()), "id duplicado")
    }

    @Test
    fun `rechaza ids que no son UUID`() {
        assertIssue(baseline.mapPatients { it.copy(patientId = "no-es-uuid") }, "no es un UUID")
    }

    @Test
    fun `rechaza correos de dominios reales`() {
        val seed = baseline.copy(users = baseline.users.map { it.copy(email = "alguien@gmail.com") })
        assertIssue(seed, "dominio reservado")
    }

    @Test
    fun `rechaza telefonos fuera del rango ficticio`() {
        val seed = baseline.mapPatients { patient ->
            patient.copy(contacts = patient.contacts.map { if (it.type == "PHONE") it.copy(value = "+528112345678") else it })
        }
        assertIssue(seed, "rango ficticio")
    }

    @Test
    fun `rechaza fechas de nacimiento futuras`() {
        assertIssue(baseline.mapPatients { it.copy(birthDate = "2031-01-01") }, "no puede ser futura")
    }

    @Test
    fun `rechaza fechas con formato invalido`() {
        assertIssue(baseline.mapPatients { it.copy(birthDate = "14/05/1998") }, "inválida")
    }

    @Test
    fun `rechaza folios mal formados`() {
        assertIssue(baseline.mapPatients { it.copy(patientNumber = "CRNL-1") }, "no cumple el formato")
    }

    @Test
    fun `rechaza codigos de catalogo desconocidos`() {
        assertIssue(baseline.mapPatients { it.copy(sex = "X") }, "no válido")
    }

    @Test
    fun `rechaza citas de pacientes inexistentes`() {
        assertIssue(baseline.mapFirstAppointment { it.copy(patientId = unknownId) }, "paciente '$unknownId' inexistente")
    }

    @Test
    fun `rechaza citas en linea sin enlace`() {
        val online = baseline.appointments.first { it.modality == "ONLINE" }
        val seed = baseline.mapAppointments { if (it.appointmentId == online.appointmentId) it.copy(meetingUrl = null) else it }
        assertIssue(seed, "cita en línea requiere un enlace")
    }

    @Test
    fun `rechaza citas presenciales sin ubicacion`() {
        val inPerson = baseline.appointments.first { it.modality == "IN_PERSON" }
        val seed = baseline.mapAppointments { if (it.appointmentId == inPerson.appointmentId) it.copy(location = null) else it }
        assertIssue(seed, "cita presencial requiere ubicación")
    }

    @Test
    fun `rechaza profesionales de otra area`() {
        assertIssue(baseline.mapFirstAppointment { it.copy(area = "NUTRITION") }, "pertenece a PSYCHOLOGY")
    }

    @Test
    fun `rechaza citas pasadas que siguen programadas`() {
        val past = baseline.appointments.first { it.dayOffset < 0 }
        val seed = baseline.mapAppointments { if (it.appointmentId == past.appointmentId) it.copy(status = "SCHEDULED") else it }
        assertIssue(seed, "cita pasada no puede estar SCHEDULED")
    }

    @Test
    fun `rechaza citas futuras que ya figuran como realizadas`() {
        val future = baseline.appointments.first { it.dayOffset > 0 }
        val seed = baseline.mapAppointments { if (it.appointmentId == future.appointmentId) it.copy(status = "COMPLETED") else it }
        assertIssue(seed, "cita futura no puede estar COMPLETED")
    }

    @Test
    fun `rechaza dos asignaciones activas del mismo paciente en la misma area`() {
        val active = baseline.assignments.first { it.status == "ACTIVE" }
        val duplicate = active.copy(assignmentId = "d9999999-9999-4999-8999-999999999999")
        assertIssue(baseline.copy(assignments = baseline.assignments + duplicate), "más de una asignación ACTIVE")
    }

    @Test
    fun `rechaza asignaciones terminadas sin fecha de fin`() {
        val ended = baseline.assignments.first { it.status == "ENDED" }
        val seed = baseline.copy(
            assignments = baseline.assignments.map { if (it.assignmentId == ended.assignmentId) it.copy(endDayOffset = null) else it },
        )
        assertIssue(seed, "ENDED requiere endDayOffset")
    }

    @Test
    fun `rechaza encuentros que no corresponden a su cita`() {
        val linked = baseline.encounters.first { it.appointmentId != null }
        val otherPatient = baseline.patients.first { it.patientId != linked.patientId }.patientId
        val seed = baseline.copy(
            encounters = baseline.encounters.map { if (it.encounterId == linked.encounterId) it.copy(patientId = otherPatient) else it },
        )
        assertIssue(seed, "mismo paciente, profesional y área")
    }

    @Test
    fun `rechaza instrumentos reales que no sean marcadores`() {
        val seed = baseline.copy(assessments = baseline.assessments.map { it.copy(instrumentCode = "BAI") })
        assertIssue(seed, "solo se admiten instrumentos marcadores")
    }

    @Test
    fun `rechaza evaluaciones completadas sin resultado`() {
        val completed = baseline.assessments.first { it.status == "COMPLETED" }
        val seed = baseline.copy(
            assessments = baseline.assessments.map { if (it.assessmentId == completed.assessmentId) it.copy(result = null) else it },
        )
        assertIssue(seed, "COMPLETED requiere resultado")
    }

    @Test
    fun `reporta todos los problemas y no solo el primero`() {
        val seed = baseline
            .mapPatients { it.copy(sex = "X") }
            .copy(users = baseline.users.map { it.copy(email = "alguien@gmail.com") })
        val issues = validator.validate(seed)
        assertTrue("Se esperaban varios problemas: $issues", issues.size >= 2)
        assertTrue(issues.any { "no válido" in it })
        assertTrue(issues.any { "dominio reservado" in it })
    }
}
