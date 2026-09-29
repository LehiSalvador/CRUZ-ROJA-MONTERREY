package mx.crnl.clinica.beta.domain.patient

import java.time.LocalDate
import mx.crnl.clinica.beta.domain.model.PatientRecord
import mx.crnl.clinica.beta.testing.domainPatient
import mx.crnl.clinica.beta.testing.patientRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientSearchTest {
    private val ana = patientRecord(
        domainPatient(id = "ana", number = "CRNL-000001", firstName = "Ana Lucía", paternalSurname = "Cavazos", maternalSurname = "Ibarra", birthDate = LocalDate.of(1998, 5, 14)),
        phone = "+528100000101",
        email = "ana.cavazos@example.org",
    )
    private val diego = patientRecord(
        domainPatient(id = "diego", number = "CRNL-000002", firstName = "Diego Alejandro", paternalSurname = "Treviño", maternalSurname = "Salinas", birthDate = LocalDate.of(2003, 11, 2)),
        phone = "+528100000102",
    )
    private val diana = patientRecord(
        domainPatient(id = "diana", number = "CRNL-000012", firstName = "Diana", paternalSurname = "Ruiz", maternalSurname = null, birthDate = LocalDate.of(1990, 1, 30)),
        email = "diana.ruiz@example.org",
    )
    private val everyone = listOf(ana, diego, diana)

    private fun search(query: String): List<String> =
        PatientSearch.filter(everyone, query).map { it.patient.patientId }

    @Test
    fun `una busqueda vacia o solo de espacios no filtra`() {
        assertEquals(listOf("ana", "diego", "diana"), search(""))
        assertEquals(listOf("ana", "diego", "diana"), search("    "))
    }

    @Test
    fun `encuentra por folio completo con o sin prefijo, en cualquier capitalizacion`() {
        assertEquals(listOf("ana"), search("CRNL-000001"))
        assertEquals(listOf("ana"), search("crnl-000001"))
        assertEquals(listOf("ana"), search("000001"))
        assertEquals(listOf("ana"), search("1"))
        assertEquals(listOf("diana"), search("12"))
        assertEquals(listOf("diana"), search("CRNL-12"))
    }

    @Test
    fun `un prefijo de folio lista los folios que empiezan igual`() {
        assertEquals(listOf("ana", "diego", "diana"), search("CRNL-0000"))
        assertEquals(listOf("ana", "diana"), search("CRNL-00001"))
        assertEquals(listOf("ana", "diego", "diana"), search("CRNL"))
    }

    @Test
    fun `encuentra por nombre y apellidos sin distinguir acentos ni mayusculas`() {
        assertEquals(listOf("ana"), search("Lucia"))
        assertEquals(listOf("ana"), search("LUCÍA"))
        assertEquals(listOf("diego"), search("trevino"))
        assertEquals(listOf("diego"), search("Treviño"))
        assertEquals(listOf("ana"), search("ibarra"))
        assertEquals(listOf("diego"), search("salinas"))
        assertEquals(listOf("diana"), search("ruiz"))
    }

    @Test
    fun `admite el nombre y los apellidos en cualquier orden y con espacios sobrantes`() {
        assertEquals(listOf("ana"), search("ana cavazos"))
        assertEquals(listOf("ana"), search("cavazos ana"))
        assertEquals(listOf("ana"), search("  ana    cav  "))
        assertEquals(emptyList<String>(), search("ana trevino"))
    }

    @Test
    fun `un fragmento corto solo coincide con el inicio de una palabra`() {
        assertEquals(listOf("ana", "diego"), search("a"))
        assertEquals(listOf("ana"), search("an"))
        assertFalse("«an» no debe encontrar a Diana", "diana" in search("an"))
        assertTrue("con tres letras sí se busca dentro de la palabra", "diana" in search("ana"))
    }

    @Test
    fun `encuentra por telefono completo, con lada o por sus ultimos digitos`() {
        assertEquals(listOf("ana"), search("8100000101"))
        assertEquals(listOf("ana"), search("+52 81 0000 0101"))
        assertEquals(listOf("ana"), search("0101"))
        assertEquals(listOf("diego"), search("(81) 0000-0102"))
    }

    @Test
    fun `menos de cuatro digitos no se buscan como telefono`() {
        assertEquals(emptyList<String>(), search("101"))
    }

    @Test
    fun `encuentra por correo completo o parcial`() {
        assertEquals(listOf("ana"), search("ana.cavazos@example.org"))
        assertEquals(listOf("ana"), search("ANA.CAVAZOS@EXAMPLE.ORG"))
        assertEquals(listOf("ana"), search("cavazos@"))
        assertEquals(listOf("ana", "diana"), search("@example.org"))
    }

    @Test
    fun `un fragmento del inicio del correo tambien encuentra al paciente`() {
        assertEquals(listOf("ana"), search("ana.cav"))
        assertEquals(emptyList<String>(), search("example"))
    }

    @Test
    fun `encuentra por fecha de nacimiento en formato amigable o interno`() {
        assertEquals(listOf("ana"), search("14/05/1998"))
        assertEquals(listOf("ana"), search("14-05-1998"))
        assertEquals(listOf("ana"), search("14.05.1998"))
        assertEquals(listOf("ana"), search("1998-05-14"))
        assertEquals(listOf("ana"), search("14/5/1998"))
        assertEquals(listOf("diego"), search("2/11/2003"))
    }

    @Test
    fun `una fecha inexistente no encuentra a nadie`() {
        assertEquals(emptyList<String>(), search("31/02/1998"))
        assertEquals(emptyList<String>(), search("05/14/1998"))
        assertNull(PatientSearch.parseBirthDate("31/02/1998"))
    }

    @Test
    fun `un texto sin relacion no encuentra a nadie`() {
        assertEquals(emptyList<String>(), search("zzzz"))
    }

    @Test
    fun `un registro sin contactos solo se encuentra por su nombre`() {
        val noContacts: PatientRecord = patientRecord(domainPatient(id = "solo", firstName = "Solo", paternalSurname = "Nombre"))
        val records = listOf(noContacts)

        assertEquals(records, PatientSearch.filter(records, "solo"))
        assertEquals(emptyList<PatientRecord>(), PatientSearch.filter(records, "8100000101"))
        assertEquals(emptyList<PatientRecord>(), PatientSearch.filter(records, "@example.org"))
    }

    @Test
    fun `un folio completo no arrastra telefonos que contienen los mismos digitos`() {
        // «000001» aparece dentro de +52 81 0000 0102 (Diego), pero es el folio de Ana.
        assertEquals(listOf("ana"), search("000001"))
        assertEquals(listOf("ana"), search("CRNL-000001"))
    }

    @Test
    fun `si nadie tiene ese folio, los digitos se buscan en los telefonos`() {
        assertEquals(listOf("diego"), search("0102"))
        assertEquals(emptyList<String>(), search("999999"))
    }
}
