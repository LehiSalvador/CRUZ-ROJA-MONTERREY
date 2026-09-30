package mx.crnl.clinica.beta.domain.clinical

import java.time.Instant
import mx.crnl.clinica.beta.domain.model.EncounterType
import mx.crnl.clinica.beta.testing.TestNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterRulesTest {
    private val now: Instant = TestNow.toInstant()

    private fun issues(type: EncounterType? = EncounterType.FOLLOW_UP, eventAt: Instant = now.minusSeconds(3_600)) =
        EncounterRules.issues(type, eventAt, now)

    @Test
    fun `un encuentro ocurrido hoy con tipo valido no tiene problemas`() {
        assertTrue(issues().isEmpty())
    }

    @Test
    fun `sin tipo de atencion se pide elegirlo`() {
        assertEquals(EncounterIssue.REQUIRED, issues(type = null)[EncounterField.TYPE])
    }

    @Test
    fun `la fecha no puede estar absurdamente en el futuro pero admite unos minutos de diferencia de reloj`() {
        assertEquals(EncounterIssue.IN_THE_FUTURE, issues(eventAt = now.plusSeconds(3_600))[EncounterField.DATE])
        assertEquals(EncounterIssue.IN_THE_FUTURE, issues(eventAt = now.plusSeconds(86_400 * 30))[EncounterField.DATE])
        assertTrue(issues(eventAt = now.plusSeconds(5 * 60)).isEmpty())
    }

    @Test
    fun `una fecha imposible por antigua se rechaza`() {
        assertEquals(EncounterIssue.TOO_OLD, issues(eventAt = Instant.parse("1990-01-01T00:00:00Z"))[EncounterField.DATE])
        assertTrue(issues(eventAt = Instant.parse("2020-06-01T00:00:00Z")).isEmpty())
    }
}
