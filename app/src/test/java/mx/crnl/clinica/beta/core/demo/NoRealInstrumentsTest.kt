package mx.crnl.clinica.beta.core.demo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los instrumentos psicológicos reales son contenido licenciado: la Beta solo trae marcadores ficticios. Ningún texto
 * que la persona vea, ni ningún dato precargado, puede nombrarlos o interpretar resultados.
 */
class NoRealInstrumentsTest {
    private val main = File("src/main")
    private val forbidden = Regex("""\bBAI\b|\bBDI\b|BDI-II|\bBeck\b""")

    // Solo los comentarios y la nota del conjunto ficticio pueden mencionarlos, para decir que NO lo son.
    private val disclaimers = setOf("assets/seed_assessment_results.json", "java/mx/crnl/clinica/beta/core/demo/DemoSeedValidator.kt")

    @Test
    fun `ningun texto visible ni recurso nombra un instrumento real`() {
        val offenders = main.walkTopDown()
            .filter { it.isFile && (it.extension in setOf("xml", "json", "kt")) }
            .filter { file -> disclaimers.none { file.invariantSeparatorsPath.endsWith(it) } }
            .filter { forbidden.containsMatchIn(it.readText(Charsets.UTF_8)) }
            .map { it.invariantSeparatorsPath }
            .toList()

        assertEquals("archivos que nombran un instrumento real: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun `los textos de la aplicacion no interpretan resultados ni clasifican`() {
        val strings = File("src/main/res/values/strings.xml").readText(Charsets.UTF_8).lowercase()

        listOf("ansiedad", "depresión", "depresion", "severo", "moderado", "leve", "diagnóstico", "diagnostico").forEach { word ->
            // Las palabras solo pueden aparecer en frases que niegan una interpretación o un diagnóstico.
            Regex("""[^<>]*\b$word\b[^<>]*""").findAll(strings).forEach { match ->
                assertTrue(
                    "«$word» aparece en un texto que podría interpretar resultados: ${match.value.trim()}",
                    match.value.contains("no ") || match.value.contains("sin "),
                )
            }
        }
    }

    @Test
    fun `los marcadores del conjunto ficticio conservan su codigo y su clasificacion sin definir`() {
        val seed = File("src/main/assets/seed_assessment_results.json").readText(Charsets.UTF_8)

        assertTrue(seed.contains("DEV_PLACEHOLDER_A"))
        assertTrue(seed.contains("DEV_PLACEHOLDER_B"))
        assertTrue(seed.contains("\"classificationCode\": \"TBD\""))
        assertTrue(seed.contains("placeholder-0"))
    }
}
