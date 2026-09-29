package mx.crnl.clinica.beta.domain.patient

import org.junit.Assert.assertEquals
import org.junit.Test

class PatientNumberTest {
    @Test
    fun `sin pacientes el primer folio es el uno`() {
        assertEquals("CRNL-000001", PatientNumber.next(emptyList()))
    }

    @Test
    fun `continua despues del ultimo folio de la semilla`() {
        val seeded = (1..8).map { "CRNL-%06d".format(it) }

        assertEquals("CRNL-000009", PatientNumber.next(seeded))
    }

    @Test
    fun `usa el mayor folio y no el ultimo de la lista`() {
        assertEquals("CRNL-000021", PatientNumber.next(listOf("CRNL-000020", "CRNL-000003", "CRNL-000007")))
    }

    @Test
    fun `ignora valores que no tienen el formato del folio`() {
        val existing = listOf("CRNL-000004", "FOLIO-000099", "CRNL-12", "crnl-000050", "CRNL-000060x", "", "CRNL-")

        assertEquals("CRNL-000005", PatientNumber.next(existing))
    }

    @Test
    fun `un hueco en la numeracion no se rellena`() {
        assertEquals("CRNL-000011", PatientNumber.next(listOf("CRNL-000001", "CRNL-000010")))
    }

    @Test
    fun `mas alla de seis digitos el folio crece sin recortarse`() {
        assertEquals("CRNL-1000000", PatientNumber.next(listOf("CRNL-999999")))
        assertEquals("CRNL-1000001", PatientNumber.next(listOf("CRNL-1000000")))
    }
}
