package mx.crnl.clinica.beta.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AssessmentInstrumentTest {
    @Test
    fun `los marcadores ficticios se traducen a instrumentos genericos`() {
        assertEquals(AssessmentInstrument.PLACEHOLDER_A, AssessmentInstrument.fromCode("DEV_PLACEHOLDER_A"))
        assertEquals(AssessmentInstrument.PLACEHOLDER_B, AssessmentInstrument.fromCode("DEV_PLACEHOLDER_B"))
    }

    @Test
    fun `ningun codigo se traduce a un instrumento clinico real`() {
        listOf("BAI", "BDI", "BDI-II", "BECK", "bai", "DEV_PLACEHOLDER_C", "").forEach { code ->
            assertEquals(code, AssessmentInstrument.UNRECOGNIZED, AssessmentInstrument.fromCode(code))
        }
    }

    @Test
    fun `el catalogo no contiene nombres de instrumentos reales`() {
        val names = AssessmentInstrument.entries.joinToString(" ") { it.name }.uppercase()

        listOf("BAI", "BDI", "BECK").forEach { forbidden ->
            assertEquals("el catalogo no debe mencionar $forbidden", false, names.contains(forbidden))
        }
    }
}
