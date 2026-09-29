package mx.crnl.clinica.beta.domain.patient

import java.time.LocalDate
import mx.crnl.clinica.beta.domain.model.DuplicateReason
import mx.crnl.clinica.beta.domain.model.PatientDraft
import mx.crnl.clinica.beta.testing.domainPatient
import mx.crnl.clinica.beta.testing.patientDraft
import mx.crnl.clinica.beta.testing.patientRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {
    private val ana = patientRecord(
        domainPatient(id = "ana", number = "CRNL-000001", firstName = "Ana Lucía", paternalSurname = "Cavazos", maternalSurname = "Ibarra", birthDate = LocalDate.of(1998, 5, 14)),
        phone = "+528100000101",
        email = "ana.cavazos@example.org",
    )
    private val diego = patientRecord(
        domainPatient(id = "diego", number = "CRNL-000002", firstName = "Diego", paternalSurname = "Treviño", maternalSurname = "Salinas", birthDate = LocalDate.of(2003, 11, 2)),
        phone = "+528100000102",
    )
    private val existing = listOf(ana, diego)

    private fun detect(
        draft: PatientDraft,
        exclude: String? = null,
    ) = DuplicateDetector.detect(draft, existing, exclude)

    private fun reasons(draft: PatientDraft, exclude: String? = null) =
        detect(draft, exclude).associate { it.patient.patientId to it.reasons }

    @Test
    fun `un paciente distinto no genera coincidencias`() {
        assertEquals(emptyMap<String, Set<DuplicateReason>>(), reasons(patientDraft()))
    }

    @Test
    fun `el mismo correo es una coincidencia sin importar mayusculas ni espacios`() {
        val draft = patientDraft(email = "  ANA.Cavazos@Example.ORG ")

        assertEquals(mapOf("ana" to setOf(DuplicateReason.SAME_EMAIL)), reasons(draft))
    }

    @Test
    fun `el mismo telefono es una coincidencia con o sin lada y con cualquier formato`() {
        listOf("8100000101", "+52 81 0000 0101", "(81) 0000-0101", "528100000101").forEach { phone ->
            assertEquals(mapOf("ana" to setOf(DuplicateReason.SAME_PHONE)), reasons(patientDraft(phone = phone)))
        }
    }

    @Test
    fun `un telefono demasiado corto no se compara`() {
        assertEquals(emptyMap<String, Set<DuplicateReason>>(), reasons(patientDraft(phone = "0101")))
    }

    @Test
    fun `sin correo ni telefono capturados no hay coincidencia por contacto`() {
        val bare = patientRecord(domainPatient(id = "solo", firstName = "Sin", paternalSurname = "Contacto"))
        val draft = patientDraft(phone = null, email = null, firstName = "Otra", paternalSurname = "Persona")

        assertTrue(DuplicateDetector.detect(draft, listOf(bare, ana)).isEmpty())
        assertTrue(DuplicateDetector.detect(draft.copy(phone = "", email = ""), listOf(bare, ana)).isEmpty())
    }

    @Test
    fun `mismo nombre, apellidos y fecha de nacimiento coinciden sin distinguir acentos ni mayusculas`() {
        val draft = patientDraft(firstName = "ANA LUCIA", paternalSurname = "cavazos", maternalSurname = "IBARRA", birthDate = LocalDate.of(1998, 5, 14), phone = null, email = null)

        assertEquals(mapOf("ana" to setOf(DuplicateReason.SAME_NAME_AND_BIRTH_DATE)), reasons(draft))
    }

    @Test
    fun `el mismo nombre con otra fecha de nacimiento no es coincidencia`() {
        val draft = patientDraft(firstName = "Ana Lucía", paternalSurname = "Cavazos", maternalSurname = "Ibarra", birthDate = LocalDate.of(1998, 5, 15), phone = null, email = null)

        assertTrue(detect(draft).isEmpty())
    }

    @Test
    fun `sin apellido materno se compara por nombre, apellido paterno y fecha de nacimiento`() {
        val draft = patientDraft(firstName = "Ana Lucía", paternalSurname = "Cavazos", maternalSurname = null, birthDate = LocalDate.of(1998, 5, 14), phone = null, email = null)

        assertEquals(mapOf("ana" to setOf(DuplicateReason.SIMILAR_NAME_AND_BIRTH_DATE)), reasons(draft))
    }

    @Test
    fun `el paciente registrado sin apellido materno tambien se compara`() {
        val registered = patientRecord(domainPatient(id = "sin-materno", firstName = "Beatriz", paternalSurname = "Lozano", maternalSurname = null, birthDate = LocalDate.of(1995, 3, 8)))
        val draft = patientDraft(phone = null, email = null)

        val found = DuplicateDetector.detect(draft, listOf(registered))

        assertEquals(setOf(DuplicateReason.SIMILAR_NAME_AND_BIRTH_DATE), found.single().reasons)
    }

    @Test
    fun `un nombre compuesto y uno de sus nombres coinciden como nombre similar`() {
        val draft = patientDraft(firstName = "Ana", paternalSurname = "Cavazos", maternalSurname = "Ibarra", birthDate = LocalDate.of(1998, 5, 14), phone = null, email = null)

        assertEquals(mapOf("ana" to setOf(DuplicateReason.SIMILAR_NAME_AND_BIRTH_DATE)), reasons(draft))
    }

    @Test
    fun `dos apellidos maternos distintos indican homonimos y no una coincidencia`() {
        val draft = patientDraft(firstName = "Ana Lucía", paternalSurname = "Cavazos", maternalSurname = "Garza", birthDate = LocalDate.of(1998, 5, 14), phone = null, email = null)

        assertTrue(detect(draft).isEmpty())
    }

    @Test
    fun `un nombre de pila distinto con los mismos apellidos y fecha no es coincidencia`() {
        val draft = patientDraft(firstName = "Marcela", paternalSurname = "Cavazos", maternalSurname = "Ibarra", birthDate = LocalDate.of(1998, 5, 14), phone = null, email = null)

        assertTrue(detect(draft).isEmpty())
    }

    @Test
    fun `un homonimo con el mismo telefono se advierte aunque el nombre difiera`() {
        val draft = patientDraft(phone = "8100000102", firstName = "Otro", paternalSurname = "Nombre")

        assertEquals(mapOf("diego" to setOf(DuplicateReason.SAME_PHONE)), reasons(draft))
    }

    @Test
    fun `acumula todas las razones y ordena primero al candidato con mas coincidencias`() {
        val draft = patientDraft(
            firstName = "Ana Lucía",
            paternalSurname = "Cavazos",
            maternalSurname = "Ibarra",
            birthDate = LocalDate.of(1998, 5, 14),
            phone = "8100000101",
            email = "ana.cavazos@example.org",
        )
        val sharedPhoneOnly = patientRecord(
            domainPatient(id = "otro", number = "CRNL-000000", firstName = "Otro", paternalSurname = "Nombre", birthDate = LocalDate.of(1980, 1, 1)),
            phone = "8100000101",
        )

        val found = DuplicateDetector.detect(draft, listOf(sharedPhoneOnly, ana))

        assertEquals(listOf("ana", "otro"), found.map { it.patient.patientId })
        assertEquals(
            setOf(DuplicateReason.SAME_EMAIL, DuplicateReason.SAME_PHONE, DuplicateReason.SAME_NAME_AND_BIRTH_DATE),
            found.first().reasons,
        )
    }

    @Test
    fun `al editar se excluye al propio paciente`() {
        val draft = patientDraft(
            firstName = "Ana Lucía",
            paternalSurname = "Cavazos",
            maternalSurname = "Ibarra",
            birthDate = LocalDate.of(1998, 5, 14),
            phone = "8100000101",
            email = "ana.cavazos@example.org",
        )

        assertEquals(setOf("ana"), detect(draft).map { it.patient.patientId }.toSet())
        assertTrue(detect(draft, exclude = "ana").isEmpty())
    }

    @Test
    fun `excluir al propio paciente no oculta a otro con el mismo contacto`() {
        val draft = patientDraft(phone = "8100000102", email = null)

        assertEquals(setOf("diego"), reasons(draft, exclude = "ana").keys)
    }

    @Test
    fun `solo cambia la identidad cuando cambia nombre, nacimiento o contacto`() {
        val base = patientDraft()

        assertFalse(DuplicateDetector.changesIdentity(base, base.copy(municipality = "Apodaca", birthPlace = "Otro")))
        assertFalse("un cambio solo de acentos o mayúsculas", DuplicateDetector.changesIdentity(base, base.copy(firstName = "BEATRIZ")))
        assertFalse("un cambio solo de formato del teléfono", DuplicateDetector.changesIdentity(base, base.copy(phone = "81 1234 5678")))
        assertTrue(DuplicateDetector.changesIdentity(base, base.copy(paternalSurname = "Otro")))
        assertTrue(DuplicateDetector.changesIdentity(base, base.copy(birthDate = LocalDate.of(1995, 3, 9))))
        assertTrue(DuplicateDetector.changesIdentity(base, base.copy(phone = "8100000000")))
        assertTrue(DuplicateDetector.changesIdentity(base, base.copy(email = null)))
    }
}
