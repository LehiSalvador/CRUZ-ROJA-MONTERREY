package mx.crnl.clinica.beta.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.model.AssessmentStatus
import mx.crnl.clinica.beta.domain.model.AuditAction
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.DuplicateReason
import mx.crnl.clinica.beta.domain.model.EncounterType
import mx.crnl.clinica.beta.domain.model.PatientDraft
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.domain.repository.PatientFilter
import mx.crnl.clinica.beta.domain.repository.PatientNotFoundException
import mx.crnl.clinica.beta.testing.BetaAccounts
import mx.crnl.clinica.beta.testing.RepositoryTest
import mx.crnl.clinica.beta.testing.SeedIds
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.assertFailsWithType
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.observeAround
import mx.crnl.clinica.beta.testing.patientDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPatientRepositoryTest : RepositoryTest() {

    private suspend fun folios(query: String, filter: PatientFilter = PatientFilter.All) =
        patients.observePatients(query, filter).first().map { it.patient.patientNumber }

    private fun folio(number: Int) = "CRNL-%06d".format(number)

    private suspend fun createdAs(actor: String = BetaAccounts.PSYCHOLOGIST_ID, draft: PatientDraft = patientDraft()) =
        patients.createPatient(draft, actor)

    // ---------------------------------------------------------------- búsqueda

    @Test
    fun `sin texto lista a todos los pacientes incluido el inactivo`() = runTest {
        assertEquals(8, folios("").size)
        assertTrue(folio(8) in folios(""))
    }

    @Test
    fun `busca por folio con o sin prefijo`() = runTest {
        assertEquals(listOf(folio(6)), folios("CRNL-000006"))
        assertEquals(listOf(folio(6)), folios("crnl-000006"))
        assertEquals(listOf(folio(6)), folios("6"))
    }

    @Test
    fun `busca por nombre y apellidos sin distinguir acentos ni mayusculas`() = runTest {
        assertEquals(listOf(folio(2)), folios("trevino"))
        assertEquals(listOf(folio(1)), folios("LUCIA"))
        assertEquals(listOf(folio(7)), folios("sepulveda"))
        assertEquals(listOf(folio(7)), folios("maria del carmen"))
        assertEquals(listOf(folio(1)), folios("ana cavazos ibarra"))
        assertEquals(listOf(folio(4)), folios("Ochoa Lozano"))
    }

    @Test
    fun `busca por telefono y por correo`() = runTest {
        assertEquals(listOf(folio(3)), folios("8100000103"))
        assertEquals(listOf(folio(4)), folios("+52 81 0000 0104"))
        assertEquals(listOf(folio(6)), folios("luis.mireles@example.org"))
        assertEquals(listOf(folio(1)), folios("ANA.CAVAZOS@example.org"))
    }

    @Test
    fun `busca por fecha de nacimiento en formato amigable o interno`() = runTest {
        assertEquals(listOf(folio(6)), folios("05/10/1992"))
        assertEquals(listOf(folio(6)), folios("1992-10-05"))
        assertEquals(listOf(folio(8)), folios("19-12-2000"))
    }

    @Test
    fun `una busqueda sin coincidencias devuelve una lista vacia`() = runTest {
        assertEquals(emptyList<String>(), folios("zzzz"))
        assertEquals(emptyList<String>(), folios("31/02/1998"))
    }

    @Test
    fun `no se buscan contactos desactivados`() = runTest {
        db.openHelper.writableDatabase.execSQL("UPDATE patient_contacts SET status = 'INACTIVE' WHERE contactValue = '+528100000103'")

        assertEquals(emptyList<String>(), folios("8100000103"))
    }

    @Test
    fun `mis pacientes muestra solo las asignaciones vigentes del profesional`() = runTest {
        assertEquals(listOf(folio(1)), folios("", PatientFilter.AssignedTo(BetaAccounts.PSYCHOLOGIST_ID)))
        assertEquals(
            listOf(folio(3), folio(5), folio(2)),
            folios("", PatientFilter.AssignedTo(BetaAccounts.SECOND_PSYCHOLOGIST_ID)),
        )
        assertEquals(
            listOf(folio(3), folio(6), folio(7)),
            folios("", PatientFilter.AssignedTo(BetaAccounts.NUTRITIONIST_ID)),
        )
    }

    @Test
    fun `una asignacion terminada no cuenta como paciente propio`() = runTest {
        // CRNL-000003 fue de Mariana hasta hace 30 días y ahora es de Rodrigo.
        assertFalse(folio(3) in folios("", PatientFilter.AssignedTo(BetaAccounts.PSYCHOLOGIST_ID)))
        assertEquals(emptyList<String>(), folios("", PatientFilter.AssignedTo("sin-pacientes")))
    }

    @Test
    fun `la busqueda se combina con el filtro de pacientes propios`() = runTest {
        val rodrigo = PatientFilter.AssignedTo(BetaAccounts.SECOND_PSYCHOLOGIST_ID)

        assertEquals(listOf(folio(5)), folios("regina", rodrigo))
        assertEquals(emptyList<String>(), folios("cavazos", rodrigo))
    }

    // ---------------------------------------------------------------- duplicados

    @Test
    fun `detecta coincidencias por correo, telefono y nombre con fecha de nacimiento`() = runTest {
        val byEmail = patients.findDuplicateCandidates(patientDraft(email = "ANA.CAVAZOS@example.org", phone = null))
        val byPhone = patients.findDuplicateCandidates(patientDraft(phone = "8100000102", email = null))
        val byName = patients.findDuplicateCandidates(
            patientDraft(
                firstName = "Luis Fernando",
                paternalSurname = "Mireles",
                maternalSurname = "Cortes",
                birthDate = LocalDate.of(1992, 10, 5),
                phone = null,
                email = null,
            ),
        )

        assertEquals(mapOf(folio(1) to setOf(DuplicateReason.SAME_EMAIL)), byEmail.associate { it.patient.patientNumber to it.reasons })
        assertEquals(mapOf(folio(2) to setOf(DuplicateReason.SAME_PHONE)), byPhone.associate { it.patient.patientNumber to it.reasons })
        assertEquals(mapOf(folio(6) to setOf(DuplicateReason.SAME_NAME_AND_BIRTH_DATE)), byName.associate { it.patient.patientNumber to it.reasons })
    }

    @Test
    fun `un paciente distinto no tiene candidatos`() = runTest {
        assertEquals(emptyList<Any>(), patients.findDuplicateCandidates(patientDraft()))
    }

    @Test
    fun `al editar se excluye al propio paciente`() = runTest {
        val draft = patients.getPatientDraft("b0000000-0000-4000-8000-000000000001")!!

        assertEquals(1, patients.findDuplicateCandidates(draft).size)
        assertEquals(emptyList<Any>(), patients.findDuplicateCandidates(draft, excludePatientId = "b0000000-0000-4000-8000-000000000001"))
    }

    @Test
    fun `un contacto desactivado ya no genera coincidencias`() = runTest {
        db.openHelper.writableDatabase.execSQL("UPDATE patient_contacts SET status = 'INACTIVE' WHERE contactValue = 'ana.cavazos@example.org'")

        assertEquals(emptyList<Any>(), patients.findDuplicateCandidates(patientDraft(email = "ana.cavazos@example.org", phone = null)))
    }

    // ---------------------------------------------------------------- alta

    @Test
    fun `crear un paciente le asigna UUID tecnico, folio consecutivo y metadatos del actor`() = runTest {
        val created = createdAs(BetaAccounts.PSYCHOLOGIST_ID)

        assertEquals(folio(9), created.patientNumber)
        assertEquals(PatientStatus.ACTIVE, created.status)
        assertEquals(TestNow.toInstant(), created.createdAt)
        assertEquals(TestNow.toInstant(), created.updatedAt)
        db.query("SELECT patientId, createdBy, updatedBy, createdAt, updatedAt FROM patients WHERE patientNumber = '${folio(9)}'", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals(created.patientId, cursor.getString(0))
            assertEquals(BetaAccounts.PSYCHOLOGIST_ID, cursor.getString(1))
            assertEquals(BetaAccounts.PSYCHOLOGIST_ID, cursor.getString(2))
            assertEquals(TestNow.toInstant().toEpochMilli(), cursor.getLong(3))
            assertEquals(cursor.getLong(3), cursor.getLong(4))
        }
    }

    @Test
    fun `el identificador tecnico y el folio humano son distintos`() = runTest {
        val created = createdAs()

        assertNotNull(created.patientId)
        assertTrue(created.patientId != created.patientNumber)
        assertFalse(created.patientId.startsWith("CRNL-"))
    }

    @Test
    fun `cada alta recibe el siguiente folio sin repetir`() = runTest {
        val first = createdAs()
        val second = createdAs(draft = patientDraft(firstName = "Otra", email = "otra@example.org", phone = "8199999999"))

        assertEquals(folio(9), first.patientNumber)
        assertEquals(folio(10), second.patientNumber)
        assertEquals(10, count("patients"))
    }

    @Test
    fun `el siguiente folio ignora huecos y folios con formato ajeno`() = runTest {
        db.openHelper.writableDatabase.execSQL(
            "UPDATE patients SET patientNumber = 'CRNL-000020' WHERE patientNumber = 'CRNL-000003'",
        )
        db.openHelper.writableDatabase.execSQL(
            "UPDATE patients SET patientNumber = 'FOLIO-99' WHERE patientNumber = 'CRNL-000004'",
        )

        assertEquals(folio(21), createdAs().patientNumber)
    }

    @Test
    fun `guarda telefono y correo como contactos principales vigentes separados del paciente`() = runTest {
        val created = createdAs(draft = patientDraft(phone = "81 2345 6789", email = "Beatriz.Lozano@Example.org"))

        db.query(
            "SELECT contactType, contactValue, isPrimary, status FROM patient_contacts WHERE patientId = '${created.patientId}' ORDER BY contactType",
            null,
        ).use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals("EMAIL", cursor.getString(0))
            assertEquals("beatriz.lozano@example.org", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals("ACTIVE", cursor.getString(3))
            cursor.moveToNext()
            assertEquals("PHONE", cursor.getString(0))
            assertEquals("81 2345 6789", cursor.getString(1))
        }
    }

    @Test
    fun `sin telefono ni correo no se crean contactos`() = runTest {
        val created = createdAs(draft = patientDraft(phone = null, email = null))

        db.query("SELECT COUNT(*) FROM patient_contacts WHERE patientId = '${created.patientId}'", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun `no se almacena la edad y la fecha de nacimiento se guarda en ISO`() = runTest {
        val created = createdAs()

        assertEquals(LocalDate.of(1995, 3, 8), patients.getPatient(created.patientId)!!.birthDate)
        db.query("SELECT birthDate FROM patients WHERE patientId = '${created.patientId}'", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals("1995-03-08", cursor.getString(0))
        }
        db.query("SELECT name FROM pragma_table_info('patients')", null).use { cursor ->
            val columns = buildList { while (cursor.moveToNext()) add(cursor.getString(0).lowercase()) }
            assertFalse("age" in columns || "edad" in columns)
        }
    }

    @Test
    fun `el alta registra PATIENT_CREATED con actor y paciente pero sin datos personales`() = runTest {
        val created = createdAs(BetaAccounts.ADMIN_ID)

        assertEquals(listOf(AuditAction.PATIENT_CREATED.name), auditedActions())
        db.query("SELECT actorUserId, entityType, entityId, patientId, metadata FROM audit_entries WHERE action = 'PATIENT_CREATED'", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals(BetaAccounts.ADMIN_ID, cursor.getString(0))
            assertEquals("PATIENT", cursor.getString(1))
            assertEquals(created.patientId, cursor.getString(2))
            assertEquals(created.patientId, cursor.getString(3))
            assertEquals("""{"patientNumber":"${folio(9)}"}""", cursor.getString(4))
        }
    }

    @Test
    fun `el alta es atomica y si falla un contacto no queda ni el paciente ni la auditoria`() = runTest {
        val brokenIds = LocalPatientRepository(db, audit, clock) { "mismo-id" }
        val patientsBefore = count("patients")
        val auditBefore = count("audit_entries")

        assertFailsWithType<SQLiteConstraintException> { brokenIds.createPatient(patientDraft(), BetaAccounts.PSYCHOLOGIST_ID) }

        assertEquals(patientsBefore, count("patients"))
        assertEquals(auditBefore, count("audit_entries"))
        assertEquals(0, foreignKeyViolations())
    }

    @Test
    fun `un actor inexistente no permite crear pacientes`() = runTest {
        assertFailsWithType<SQLiteConstraintException> { createdAs("fantasma") }

        assertEquals(8, count("patients"))
    }

    @Test
    fun `el paciente creado aparece de inmediato en el listado observado`() = runTest {
        val seen = patients.observePatients().observeAround(change = { createdAs() }, until = { it.size == 9 })

        assertEquals(8, seen.first().size)
        assertEquals(9, seen.last().size)
    }

    @Test
    fun `el paciente creado se puede buscar por folio, nombre, telefono, correo y fecha`() = runTest {
        createdAs(draft = patientDraft(firstName = "Beatriz", paternalSurname = "Lozano", phone = "8123456789", email = "beatriz.lozano@example.org"))

        listOf(folio(9), "beatriz", "8123456789", "beatriz.lozano@example.org", "08/03/1995").forEach { query ->
            assertEquals(query, listOf(folio(9)), folios(query))
        }
    }

    // ---------------------------------------------------------------- edición

    private val ana = "b0000000-0000-4000-8000-000000000001"

    private val laterClock = fixedClock(TestNow.plusDays(1))
    private val laterPatients by lazy { LocalPatientRepository(db, AuditRecorder(db.auditDao(), laterClock, newId), laterClock, newId) }

    @Test
    fun `editar conserva identificador, folio y datos de creacion y actualiza quien y cuando`() = runTest {
        val before = patients.getPatient(ana)!!
        val draft = patients.getPatientDraft(ana)!!.copy(municipality = "Guadalupe", birthPlace = "Saltillo, Coahuila")

        val updated = laterPatients.updatePatient(ana, draft, BetaAccounts.COORDINATOR_ID)

        assertEquals(ana, updated.patientId)
        assertEquals("CRNL-000001", updated.patientNumber)
        assertEquals(before.createdAt, updated.createdAt)
        assertEquals(TestNow.plusDays(1).toInstant(), updated.updatedAt)
        assertEquals("Guadalupe", updated.municipality)
        db.query("SELECT createdBy, updatedBy, updatedAt, municipality, birthPlace FROM patients WHERE patientId = '$ana'", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals(BetaAccounts.PSYCHOLOGIST_ID, cursor.getString(0))
            assertEquals(BetaAccounts.COORDINATOR_ID, cursor.getString(1))
            assertEquals(TestNow.plusDays(1).toInstant().toEpochMilli(), cursor.getLong(2))
            assertEquals("Guadalupe", cursor.getString(3))
            assertEquals("Saltillo, Coahuila", cursor.getString(4))
        }
    }

    @Test
    fun `editar puede cambiar nombre, apellidos, fecha, sexo y poblacion`() = runTest {
        val draft = patients.getPatientDraft(ana)!!.copy(
            firstName = "Ana Lucía María",
            paternalSurname = "Cavazos",
            maternalSurname = null,
            birthDate = LocalDate.of(1998, 5, 15),
            sex = Sex.OTHER,
            populationType = PopulationType.EMPLOYEE,
        )

        val updated = patients.updatePatient(ana, draft, BetaAccounts.PSYCHOLOGIST_ID)

        assertEquals("Ana Lucía María Cavazos", updated.fullName)
        assertNull(updated.maternalSurname)
        assertEquals(LocalDate.of(1998, 5, 15), updated.birthDate)
        assertEquals(Sex.OTHER, updated.sex)
        assertEquals(PopulationType.EMPLOYEE, updated.populationType)
        assertEquals(updated, patients.getPatient(ana))
    }

    @Test
    fun `cambiar el telefono desactiva el anterior en lugar de borrarlo y crea el nuevo principal`() = runTest {
        val draft = patients.getPatientDraft(ana)!!.copy(phone = "8188889999")

        patients.updatePatient(ana, draft, BetaAccounts.PSYCHOLOGIST_ID)

        assertEquals("8188889999", patients.getPatientDraft(ana)!!.phone)
        db.query("SELECT contactValue, isPrimary, status FROM patient_contacts WHERE patientId = '$ana' AND contactType = 'PHONE' ORDER BY createdAt, contactValue", null).use { cursor ->
            val rows = buildList { while (cursor.moveToNext()) add(Triple(cursor.getString(0), cursor.getInt(1), cursor.getString(2))) }
            assertEquals(2, rows.size)
            assertTrue(Triple("+528100000101", 0, "INACTIVE") in rows)
            assertTrue(Triple("8188889999", 1, "ACTIVE") in rows)
        }
    }

    @Test
    fun `quitar el correo lo desactiva y agregar uno donde no habia crea el contacto`() = runTest {
        val diego = "b0000000-0000-4000-8000-000000000002"
        patients.updatePatient(ana, patients.getPatientDraft(ana)!!.copy(email = null), BetaAccounts.PSYCHOLOGIST_ID)
        patients.updatePatient(diego, patients.getPatientDraft(diego)!!.copy(email = "diego.nuevo@example.org"), BetaAccounts.PSYCHOLOGIST_ID)

        assertNull(patients.getPatientDraft(ana)!!.email)
        assertEquals("diego.nuevo@example.org", patients.getPatientDraft(diego)!!.email)
        db.query("SELECT status FROM patient_contacts WHERE contactValue = 'ana.cavazos@example.org'", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals("INACTIVE", cursor.getString(0))
        }
    }

    @Test
    fun `cambiar solo el formato del telefono no toca los contactos ni la auditoria`() = runTest {
        val draft = patients.getPatientDraft(ana)!!.copy(phone = "81 0000 0101")

        patients.updatePatient(ana, draft, BetaAccounts.PSYCHOLOGIST_ID)

        assertEquals("+528100000101", patients.getPatientDraft(ana)!!.phone)
        assertEquals(emptyList<String>(), auditedActions())
    }

    @Test
    fun `guardar sin cambios no escribe nada`() = runTest {
        val before = patients.getPatient(ana)

        val result = patients.updatePatient(ana, patients.getPatientDraft(ana)!!, BetaAccounts.COORDINATOR_ID)

        assertEquals(before, result)
        assertEquals(before, patients.getPatient(ana))
        assertEquals(emptyList<String>(), auditedActions())
    }

    @Test
    fun `la edicion registra PATIENT_UPDATED con los campos cambiados y sin sus valores`() = runTest {
        val draft = patients.getPatientDraft(ana)!!.copy(municipality = "Apodaca", phone = "8177776666")

        patients.updatePatient(ana, draft, BetaAccounts.PSYCHOLOGIST_ID)

        assertEquals(listOf(AuditAction.PATIENT_UPDATED.name), auditedActions())
        db.query("SELECT actorUserId, patientId, metadata FROM audit_entries WHERE action = 'PATIENT_UPDATED'", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals(BetaAccounts.PSYCHOLOGIST_ID, cursor.getString(0))
            assertEquals(ana, cursor.getString(1))
            val metadata = cursor.getString(2)
            assertEquals("""{"fields":["MUNICIPALITY","PHONE"]}""", metadata)
            assertFalse(metadata.contains("Apodaca") || metadata.contains("8177776666"))
        }
    }

    @Test
    fun `editar un paciente inexistente falla sin escribir`() = runTest {
        assertFailsWithType<PatientNotFoundException> { patients.updatePatient("no-existe", patientDraft(), BetaAccounts.ADMIN_ID) }

        assertEquals(emptyList<String>(), auditedActions())
    }

    @Test
    fun `una edicion con un actor inexistente no deja cambios parciales`() = runTest {
        val before = patients.getPatient(ana)
        val draft = patients.getPatientDraft(ana)!!.copy(municipality = "Apodaca", phone = "8177776666")

        assertFailsWithType<SQLiteConstraintException> { patients.updatePatient(ana, draft, "fantasma") }

        assertEquals(before, patients.getPatient(ana))
        assertEquals("+528100000101", patients.getPatientDraft(ana)!!.phone)
        assertEquals(0, foreignKeyViolations())
    }

    @Test
    fun `editar no borra ninguna fila de pacientes ni de contactos`() = runTest {
        val patientsBefore = count("patients")
        val contactsBefore = count("patient_contacts")

        patients.updatePatient(ana, patients.getPatientDraft(ana)!!.copy(phone = "8188889999", email = null), BetaAccounts.PSYCHOLOGIST_ID)

        assertEquals(patientsBefore, count("patients"))
        assertTrue(count("patient_contacts") >= contactsBefore)
    }

    // ---------------------------------------------------------------- consulta del expediente

    @Test
    fun `registra la consulta del expediente con actor y paciente`() = runTest {
        patients.recordPatientViewed(ana, BetaAccounts.PSYCHOLOGIST_ID)

        assertEquals(listOf(AuditAction.PATIENT_VIEWED.name), auditedActions())
        db.query("SELECT actorUserId, patientId, entityId, metadata FROM audit_entries WHERE action = 'PATIENT_VIEWED'", null).use { cursor ->
            cursor.moveToFirst()
            assertEquals(BetaAccounts.PSYCHOLOGIST_ID, cursor.getString(0))
            assertEquals(ana, cursor.getString(1))
            assertEquals(ana, cursor.getString(2))
            assertTrue(cursor.isNull(3))
        }
    }

    @Test
    fun `no se puede auditar la consulta de un paciente inexistente`() = runTest {
        assertFailsWithType<SQLiteConstraintException> { patients.recordPatientViewed("no-existe", BetaAccounts.PSYCHOLOGIST_ID) }
    }

    // ---------------------------------------------------------------- expediente

    @Test
    fun `el expediente reune contactos, asignaciones, citas, consultas y evaluaciones reales`() = runTest {
        val fernanda = "b0000000-0000-4000-8000-000000000003"

        val detail = patients.observePatientDetail(fernanda, account(SeedIds.HECTOR)).first()!!

        assertEquals("CRNL-000003", detail.patient.patientNumber)
        assertEquals(listOf("+528100000103", "fernanda.guerra@example.org"), detail.contacts.map { it.value }.sortedBy { !it.startsWith("+") })
        assertEquals(
            listOf(ClinicalArea.PSYCHOLOGY to "Rodrigo Villarreal", ClinicalArea.NUTRITION to "Paola Garza"),
            detail.assignments.map { it.area to it.professionalName },
        )
        assertEquals(3, detail.appointments.size)
        assertEquals(setOf(ClinicalArea.NUTRITION, ClinicalArea.PSYCHOLOGY), detail.encounters.map { it.area }.toSet())
        assertEquals(EncounterType.INITIAL, detail.encounters.first { it.area == ClinicalArea.NUTRITION }.type)
        val assessment = detail.assessments.single()
        assertEquals(ClinicalArea.PSYCHOLOGY, assessment.area)
        assertEquals(AssessmentStatus.COMPLETED, assessment.status)
        assertTrue(assessment.hasResult)
        assertNull("el resultado sin clasificar no inventa una etiqueta", assessment.classificationLabel)
    }

    @Test
    fun `la proxima cita del expediente es la mas cercana que todavia puede ocurrir`() = runTest {
        val detail = patients.observePatientDetail("b0000000-0000-4000-8000-000000000003", account(SeedIds.HECTOR)).first()!!

        val next = detail.nextAppointment(TestNow.toInstant())!!

        assertEquals("e0000000-0000-4000-8000-000000000007", next.appointmentId)
        assertNull(detail.nextAppointment(TestNow.toInstant().plusSeconds(60L * 24 * 3600)))
    }

    @Test
    fun `un paciente sin consultas ni evaluaciones tiene secciones vacias en lugar de datos inventados`() = runTest {
        val detail = patients.observePatientDetail("b0000000-0000-4000-8000-000000000005", account(SeedIds.HECTOR)).first()!!

        assertEquals(emptyList<Any>(), detail.encounters)
        assertEquals(emptyList<Any>(), detail.assessments)
        assertEquals(1, detail.assignments.size)
    }

    @Test
    fun `un paciente inexistente emite nulo`() = runTest {
        assertNull(patients.observePatientDetail("no-existe", account(SeedIds.HECTOR)).first())
    }

    @Test
    fun `los contactos desactivados no aparecen en el expediente`() = runTest {
        patients.updatePatient(ana, patients.getPatientDraft(ana)!!.copy(phone = "8188889999"), BetaAccounts.PSYCHOLOGIST_ID)

        val detail = patients.observePatientDetail(ana, account(SeedIds.HECTOR)).first()!!

        assertEquals(setOf("8188889999", "ana.cavazos@example.org"), detail.contacts.map { it.value }.toSet())
    }

    @Test
    fun `el expediente se actualiza cuando se edita el paciente`() = runTest {
        val seen = patients.observePatientDetail(ana, account(SeedIds.HECTOR)).observeAround(
            change = { patients.updatePatient(ana, patients.getPatientDraft(ana)!!.copy(municipality = "Guadalupe"), BetaAccounts.PSYCHOLOGIST_ID) },
            until = { it!!.patient.municipality == "Guadalupe" },
        )

        assertEquals("Monterrey", seen.first()!!.patient.municipality)
        assertEquals("Guadalupe", seen.last()!!.patient.municipality)
    }

    // ---------------------------------------------------------------- visibilidad multidisciplinaria

    @Test
    fun `coordinacion de Psicologia ve el detalle de Psicologia y solo constancia de Nutricion`() = runTest {
        val detail = patients.observePatientDetail(SeedIds.FERNANDA, account(SeedIds.CLAUDIA)).first()!!

        assertEquals(setOf(ClinicalArea.PSYCHOLOGY), detail.viewableAreas)
        assertEquals(listOf(ClinicalArea.PSYCHOLOGY), detail.assignments.map { it.area })
        assertEquals("Rodrigo Villarreal", detail.assignments.single().professionalName)
        assertTrue(detail.appointments.all { it.area == ClinicalArea.PSYCHOLOGY })
        assertTrue(detail.encounters.all { it.area == ClinicalArea.PSYCHOLOGY })
        assertEquals(listOf(ClinicalArea.NUTRITION), detail.restrictedAreas.map { it.area })
        assertEquals(1, detail.restrictedAreas.single().encounterCount)
        assertNotNull(detail.restrictedAreas.single().lastActivityAt)
    }

    @Test
    fun `un profesional de otra area no obtiene profesional, citas ni encuentros de las areas ajenas`() = runTest {
        val detail = patients.observePatientDetail(SeedIds.FERNANDA, account(SeedIds.PAOLA)).first()!!

        assertEquals(setOf(ClinicalArea.NUTRITION), detail.viewableAreas)
        val leaked = detail.assignments.map { it.professionalName } + detail.appointments.map { it.professionalName } +
            detail.encounters.map { it.professionalName }
        assertFalse(leaked.any { it.contains("Rodrigo") || it.contains("Mariana") })
        assertEquals(listOf(ClinicalArea.PSYCHOLOGY), detail.restrictedAreas.map { it.area })
    }

    @Test
    fun `administracion clinica ve las tres areas y el historial de asignaciones`() = runTest {
        val detail = patients.observePatientDetail(SeedIds.FERNANDA, account(SeedIds.HECTOR)).first()!!

        assertEquals(ClinicalArea.entries.toSet(), detail.viewableAreas)
        assertTrue(detail.restrictedAreas.isEmpty())
        // Fernanda tuvo a Mariana en Psicología (cerrada) y luego a Rodrigo; además Paola en Nutrición.
        assertEquals(3, detail.assignmentHistory.size)
        assertEquals(2, detail.assignments.size)
    }

    @Test
    fun `el administrador del sistema ve los datos generales y ningun detalle clinico`() = runTest {
        val detail = patients.observePatientDetail(SeedIds.FERNANDA, insertSystemAdmin()).first()!!

        assertEquals("CRNL-000003", detail.patient.patientNumber)
        assertTrue(detail.contacts.isNotEmpty())
        assertTrue(detail.viewableAreas.isEmpty())
        assertTrue(detail.assignments.isEmpty())
        assertTrue(detail.appointments.isEmpty())
        assertTrue(detail.encounters.isEmpty())
        assertTrue(detail.assessments.isEmpty())
    }

    @Test
    fun `una asignacion nueva aparece en el expediente sin recargar`() = runTest {
        setPatientStatus(SeedIds.ANDRES, "ACTIVE")
        val seen = patients.observePatientDetail(SeedIds.ANDRES, account(SeedIds.CLAUDIA)).observeAround(
            change = { assignments.createInitialAssignment(SeedIds.ANDRES, ClinicalArea.PSYCHOLOGY, SeedIds.RODRIGO, null, SeedIds.CLAUDIA) },
            until = { it!!.assignments.isNotEmpty() },
        )

        assertTrue(seen.first()!!.assignments.isEmpty())
        assertEquals("Rodrigo Villarreal", seen.last()!!.assignments.single().professionalName)
    }
}
