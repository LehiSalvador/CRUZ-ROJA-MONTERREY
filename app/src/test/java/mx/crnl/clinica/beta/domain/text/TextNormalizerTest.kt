package mx.crnl.clinica.beta.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun `fold quita acentos, mayusculas y espacios sobrantes`() {
        assertEquals("maria del carmen sepulveda", TextNormalizer.fold("  María   del CARMEN  Sepúlveda "))
        assertEquals("trevino", TextNormalizer.fold("Treviño"))
        assertEquals("nandu", TextNormalizer.fold("Ñandú"))
    }

    @Test
    fun `fold de un texto ya normalizado no cambia`() {
        assertEquals("ana lucia", TextNormalizer.fold("ana lucia"))
    }

    @Test
    fun `el telefono conserva solo los diez ultimos digitos`() {
        assertEquals("8100000101", TextNormalizer.phoneDigits("+52 (81) 0000-0101"))
        assertEquals("8100000101", TextNormalizer.phoneDigits("528100000101"))
        assertEquals("8100000101", TextNormalizer.phoneDigits("81 0000 0101"))
        assertEquals("0101", TextNormalizer.phoneDigits("0101"))
        assertEquals("", TextNormalizer.phoneDigits("sin numero"))
    }

    @Test
    fun `el correo se recorta y se pasa a minusculas`() {
        assertEquals("ana@example.org", TextNormalizer.email("  Ana@Example.ORG  "))
    }

    @Test
    fun `tidy recorta y colapsa espacios sin tocar acentos ni mayusculas`() {
        assertEquals("Ana Lucía Cavazos", TextNormalizer.tidy("  Ana   Lucía\tCavazos "))
    }

    @Test
    fun `las reglas de correo aceptan direcciones comunes y rechazan las rotas`() {
        listOf("a@example.org", "nombre.apellido+etiqueta@sub.example.com").forEach { assertTrue(it, EmailRules.isValid(it)) }
        listOf("", "sin-arroba", "a@b", "a b@example.org", "@example.org", "a@@example.org", "a@example.").forEach {
            assertFalse(it, EmailRules.isValid(it))
        }
    }
}
