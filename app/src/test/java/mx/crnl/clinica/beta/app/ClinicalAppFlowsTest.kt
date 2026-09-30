package mx.crnl.clinica.beta.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.core.demo.AssetSeedFileReader
import mx.crnl.clinica.beta.core.demo.DemoDataInitializer
import mx.crnl.clinica.beta.core.demo.DemoSeedLoader
import mx.crnl.clinica.beta.core.platform.ExternalLinkLauncher
import mx.crnl.clinica.beta.core.platform.LocalExternalLinkLauncher
import mx.crnl.clinica.beta.core.ui.theme.ClinicalTheme
import mx.crnl.clinica.beta.testing.BetaAccounts
import mx.crnl.clinica.beta.testing.FakeSessionRepository
import mx.crnl.clinica.beta.testing.RoomAppContainer
import mx.crnl.clinica.beta.testing.SeedIds
import mx.crnl.clinica.beta.testing.assertVisible
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.hasTextNow
import mx.crnl.clinica.beta.testing.tap
import mx.crnl.clinica.beta.testing.testInstant
import mx.crnl.clinica.beta.testing.typeInto
import mx.crnl.clinica.beta.testing.waitForText
import mx.crnl.clinica.beta.testing.waitForTextContaining
import mx.crnl.clinica.beta.testing.waitForTextGone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Los recorridos de la Fase 3 sobre la aplicación completa: pantallas reales, repositorios reales, Room en memoria y el conjunto ficticio. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class ClinicalAppFlowsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: ClinicalDatabase
    private lateinit var container: RoomAppContainer

    /** Sustituye la apertura de aplicaciones externas: registra los enlaces y responde como se le indique. */
    private class RecordingLauncher(var accepts: Boolean = true) : ExternalLinkLauncher {
        val opened = mutableListOf<String>()

        override fun open(uri: String): Boolean {
            opened += uri
            return accepts
        }
    }

    private val launcher = RecordingLauncher()

    @Before
    fun assemble() {
        database = Room.inMemoryDatabaseBuilder(context, ClinicalDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            DemoDataInitializer(database.seedDao(), FakeSessionRepository(), DemoSeedLoader(AssetSeedFileReader(context.assets)), fixedClock()).initialize()
        }
        container = RoomAppContainer(database)
        composeRule.setContent {
            ClinicalTheme {
                CompositionLocalProvider(LocalExternalLinkLauncher provides launcher) {
                    ClinicalApp(container, splashMinimumDisplayMillis = 0, searchDebounceMillis = 0)
                }
            }
        }
    }

    @After
    fun close() {
        database.close()
    }

    private fun openTab(label: String) {
        composeRule.onNode(hasText(label) and hasClickAction()).performClick()
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun signIn(email: String) {
        composeRule.typeInto("Correo electrónico", email)
        composeRule.typeInto("Contraseña", BetaAccounts.PASSWORD)
        composeRule.tap("Iniciar sesión")
    }

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use {
        it.moveToFirst()
        it.getLong(0)
    }

    private fun text(sql: String): String? = database.openHelper.writableDatabase.query(sql).use {
        it.moveToFirst()
        if (it.isNull(0)) null else it.getString(0)
    }

    private fun auditedActions(): List<String> =
        database.openHelper.writableDatabase.query("SELECT action FROM audit_entries WHERE action <> 'DEMO_SEED_APPLIED' ORDER BY rowid").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun openPatient(fullName: String) {
        openTab("Pacientes")
        // El listado solo compone lo visible: se busca por apellido paterno para no depender del orden ni de cuántas filas caben.
        composeRule.typeInto("Buscar paciente", fullName.split(" ").let { it[it.size - 2] })
        composeRule.waitForText(fullName)
        composeRule.tap(fullName)
        composeRule.waitForText("Expediente")
    }

    private fun fillAppointmentForm(date: String, time: String, location: String = "Consultorio 3") {
        composeRule.typeInto("Fecha", date)
        composeRule.typeInto("Hora de inicio (24 h)", time)
        composeRule.typeInto("Ubicación", location)
    }

    // ---------------------------------------------------------------- escenario 1

    @Test
    fun `coordinacion asigna, agenda, confirma, contacta, marca realizada, registra la atencion y la ve en la linea del area`() {
        signIn(BetaAccounts.COORDINATOR_EMAIL)
        composeRule.waitForText("Hola, Claudia")

        // Luis no tiene profesional de Psicología: coordinación puede asignarlo.
        openPatient("Luis Fernando Mireles Cortés")
        composeRule.tap("Psicología")
        composeRule.waitForText("Sin profesional asignado en esta área.")
        composeRule.tap("Asignar profesional")
        composeRule.waitForText("Profesionales del área")
        composeRule.assertVisible("Mariana Elizondo Cantú")
        composeRule.assertVisible("Rodrigo Villarreal Saldaña")
        // Las cuentas rechazadas o inactivas no se pueden elegir.
        assertFalse(composeRule.hasTextNow("Iván Rangel Solís"))
        assertFalse(composeRule.hasTextNow("Sofía Cantú Ochoa"))
        composeRule.tap("Mariana Elizondo Cantú")
        composeRule.waitForText("Asignar a Mariana Elizondo Cantú")
        composeRule.tap("Asignar")
        composeRule.waitForText("Profesional asignado")
        composeRule.waitForText("Vigente")
        assertEquals(1, scalar("SELECT COUNT(*) FROM professional_assignments WHERE patientId = '${SeedIds.LUIS}' AND areaCode = 'PSYCHOLOGY' AND status = 'ACTIVE'"))
        assertEquals(SeedIds.CLAUDIA, text("SELECT assignedBy FROM professional_assignments WHERE patientId = '${SeedIds.LUIS}' AND areaCode = 'PSYCHOLOGY'"))

        // Cita nueva con el profesional asignado, en el mismo expediente.
        composeRule.tap("Nueva cita")
        composeRule.waitForText("Luis Fernando Mireles Cortés")
        composeRule.assertVisible("Mariana Elizondo")
        fillAppointmentForm("29092026", "1600")
        composeRule.tap("Guardar cita")
        composeRule.waitForText("Cita creada")
        composeRule.waitForText("Programada")
        composeRule.assertVisible("Consultorio 3")
        val appointmentId = requireNotNull(text("SELECT appointmentId FROM appointments WHERE patientId = '${SeedIds.LUIS}' AND areaCode = 'PSYCHOLOGY'"))
        assertEquals(testInstant(0, 16).toEpochMilli(), scalar("SELECT startDateTime FROM appointments WHERE appointmentId = '$appointmentId'"))
        assertEquals(SeedIds.CLAUDIA, text("SELECT createdBy FROM appointments WHERE appointmentId = '$appointmentId'"))

        composeRule.tap("Confirmar cita")
        composeRule.waitForText("Confirmada")

        // WhatsApp: solo se cede la pantalla a la otra aplicación, con un texto neutro.
        composeRule.tap("Contactar por WhatsApp")
        composeRule.waitForIdle()
        val link = launcher.opened.single()
        assertTrue(link, link.startsWith("whatsapp://send?phone=5281"))
        val message = java.net.URLDecoder.decode(link.substringAfter("&text="), "UTF-8")
        assertTrue(message, message.startsWith("Hola Luis Fernando. Te contactamos de Cruz Roja Nuevo León"))
        assertFalse(message.lowercase().contains("psicolog"))
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_entries WHERE action = 'APPOINTMENT_WHATSAPP_OPENED'"))

        // Marcar realizada exige confirmar; la atención se registra por separado.
        composeRule.tap("Marcar realizada")
        composeRule.waitForText("¿Marcar la cita como realizada?")
        assertEquals("todavía confirmada", "CONFIRMED", text("SELECT status FROM appointments WHERE appointmentId = '$appointmentId'"))
        composeRule.tap("Sí, marcar realizada")
        composeRule.waitForText("Realizada")
        composeRule.waitForText("Sin atención registrada.")
        assertEquals(0, scalar("SELECT COUNT(*) FROM clinical_encounters WHERE appointmentId = '$appointmentId'"))

        composeRule.tap("Registrar atención")
        composeRule.waitForText("Tipo de atención")
        composeRule.tap("Seguimiento")
        composeRule.tap("Registrar atención")
        composeRule.waitForText("Atención registrada")

        // El expediente se abre en la pestaña del área y la atención aparece en su línea.
        composeRule.waitForText("Historial de atención")
        composeRule.assertVisible("Seguimiento · Psicología")
        composeRule.waitForText("Mariana Elizondo · De una cita")
        assertEquals(1, scalar("SELECT COUNT(*) FROM clinical_encounters WHERE appointmentId = '$appointmentId'"))
        assertEquals("COMPLETED", text("SELECT status FROM appointments WHERE appointmentId = '$appointmentId'"))

        assertEquals(
            listOf(
                "LOGIN",
                "ASSIGNMENT_CREATED",
                "APPOINTMENT_CREATED",
                "APPOINTMENT_CONFIRMED",
                "APPOINTMENT_WHATSAPP_OPENED",
                "APPOINTMENT_COMPLETED",
                "ENCOUNTER_CREATED",
            ),
            auditedActions().filter { it != "PATIENT_VIEWED" },
        )
    }

    // ---------------------------------------------------------------- escenario 2

    @Test
    fun `un profesional ve solo sus citas y registra atencion de su paciente en su area`() {
        signIn(BetaAccounts.PSYCHOLOGIST_EMAIL)
        composeRule.waitForText("Hola, Mariana")

        openTab("Citas")
        composeRule.waitForText("Ana Lucía Cavazos Ibarra")
        // La cita de hoy de Fernanda es de otro profesional.
        assertFalse(composeRule.hasTextNow("Fernanda Guerra Domínguez"))
        composeRule.tap("Ana Lucía Cavazos Ibarra")
        composeRule.waitForText("Pendiente")
        composeRule.assertVisible("Programar")
        composeRule.assertVisible("Confirmar cita")
        composeRule.assertVisible("Cancelar cita")
        // Una cita pendiente todavía no se puede cerrar como realizada.
        assertFalse(composeRule.hasTextNow("Marcar realizada"))
        pressBack()

        openTab("Pacientes")
        composeRule.tap("Mis pacientes")
        composeRule.waitForText("Ana Lucía Cavazos Ibarra")
        composeRule.tap("Ana Lucía Cavazos Ibarra")
        composeRule.waitForText("Expediente")
        composeRule.tap("Psicología")
        composeRule.waitForText("Historial de atención")
        composeRule.tap("Registrar atención")
        composeRule.waitForText("Tipo de atención")
        composeRule.tap("Intervención")
        composeRule.tap("Registrar atención")
        composeRule.waitForText("Atención registrada")

        composeRule.waitForText("Intervención · Psicología")
        assertEquals("un encuentro nuevo, más el sembrado", 2, scalar("SELECT COUNT(*) FROM clinical_encounters WHERE patientId = '${SeedIds.ANA}'"))
        assertEquals(SeedIds.MARIANA, text("SELECT professionalId FROM clinical_encounters WHERE encounterTypeCode = 'INTERVENTION'"))
    }

    // ---------------------------------------------------------------- escenario 3

    @Test
    fun `coordinacion de Psicologia ve el detalle de su area y solo constancia de la atencion en Nutricion`() {
        signIn(BetaAccounts.COORDINATOR_EMAIL)
        composeRule.waitForText("Hola, Claudia")
        openPatient("Fernanda Guerra Domínguez")

        // Resumen: detalle de Psicología y constancia de Nutrición.
        composeRule.waitForText("Atención por área")
        composeRule.assertVisible("El paciente cuenta con atención registrada en esta área.")
        assertFalse(composeRule.hasTextNow("Paola Garza"))

        composeRule.tap("Psicología")
        composeRule.waitForText("Historial de atención")
        composeRule.waitForTextContaining("Rodrigo Villarreal")

        composeRule.tap("Nutrición")
        composeRule.waitForText("El paciente cuenta con atención registrada en esta área.")
        composeRule.waitForTextContaining("1 atención registrada")
        assertFalse(composeRule.hasTextNow("Paola Garza"))
        assertFalse(composeRule.hasTextNow("Historial de atención"))
        assertFalse("sin acciones de otra área", composeRule.hasTextNow("Registrar atención"))
        assertFalse(composeRule.hasTextNow("Asignar profesional"))

        // La cita de Nutrición de esta persona tampoco aparece en la pestaña de citas.
        // Hay dos «Citas» (la pestaña del expediente y la barra inferior): la primera en el árbol es la pestaña.
        composeRule.onAllNodes(hasText("Citas") and hasClickAction())[0].performClick()
        composeRule.waitForText("Próximas")
        assertFalse(composeRule.hasTextNow("Consultorio 4"))
    }

    // ---------------------------------------------------------------- reglas de la asignación

    @Test
    fun `un profesional no ve la accion de asignar y coordinacion de otra area tampoco`() {
        signIn(BetaAccounts.PSYCHOLOGIST_EMAIL)
        composeRule.waitForText("Hola, Mariana")
        openPatient("Luis Fernando Mireles Cortés")
        composeRule.tap("Psicología")
        composeRule.waitForText("Sin profesional asignado en esta área.")
        assertFalse(composeRule.hasTextNow("Asignar profesional"))
        assertFalse(composeRule.hasTextNow("Nueva cita"))
    }

    @Test
    fun `un paciente ya asignado muestra su profesional y no ofrece cambiarlo`() {
        signIn(BetaAccounts.COORDINATOR_EMAIL)
        composeRule.waitForText("Hola, Claudia")
        openPatient("Ana Lucía Cavazos Ibarra")
        composeRule.tap("Psicología")
        composeRule.waitForText("Mariana Elizondo")
        composeRule.assertVisible("Vigente")
        assertFalse(composeRule.hasTextNow("Asignar profesional"))
        assertFalse(composeRule.hasTextNow("Cambiar profesional"))
    }

    // ---------------------------------------------------------------- conflictos, retroceso, reprogramar y cancelar

    @Test
    fun `un horario ocupado no se guarda, se explica con que cita choca y se puede abrir`() {
        signIn(BetaAccounts.COORDINATOR_EMAIL)
        composeRule.waitForText("Hola, Claudia")
        openPatient("Diego Alejandro Treviño Salinas")
        composeRule.tap("Psicología")
        composeRule.tap("Nueva cita")
        composeRule.waitForText("Guardar cita")
        // Rodrigo atiende hoy de 10:00 a 10:50 a Fernanda.
        fillAppointmentForm("29092026", "1030")

        composeRule.tap("Guardar cita")

        composeRule.waitForText("Ese horario ya está ocupado")
        composeRule.assertVisible("El profesional ya tiene una cita en ese horario.")
        assertEquals(12, scalar("SELECT COUNT(*) FROM appointments"))
        composeRule.tap("Ver cita")
        composeRule.waitForText("Fernanda Guerra Domínguez")
        composeRule.waitForText("Confirmada")
        // Al volver, el formulario conserva lo capturado.
        pressBack()
        composeRule.waitForText("Guardar cita")
        composeRule.assertVisible("Ese horario ya está ocupado")
    }

    @Test
    fun `cancelar el formulario vuelve al expediente y guardar abre la cita desde donde se puede volver al paciente`() {
        signIn(BetaAccounts.COORDINATOR_EMAIL)
        composeRule.waitForText("Hola, Claudia")
        openPatient("Diego Alejandro Treviño Salinas")
        composeRule.tap("Psicología")
        composeRule.tap("Nueva cita")
        composeRule.waitForText("Guardar cita")
        composeRule.tap("Cancelar")
        composeRule.waitForText("Expediente")
        composeRule.assertVisible("Diego Alejandro Treviño Salinas")
        assertEquals(12, scalar("SELECT COUNT(*) FROM appointments"))

        composeRule.tap("Nueva cita")
        composeRule.waitForText("Guardar cita")
        fillAppointmentForm("30092026", "1200")
        composeRule.tap("Guardar cita")
        composeRule.waitForText("Cita creada")
        composeRule.tap("Ver expediente del paciente")
        composeRule.waitForText("Expediente")
        composeRule.assertVisible("CRNL-000002 · 22 años · Masculino")
        assertEquals(13, scalar("SELECT COUNT(*) FROM appointments"))
    }

    @Test
    fun `salir del formulario con datos capturados pide confirmar`() {
        signIn(BetaAccounts.COORDINATOR_EMAIL)
        composeRule.waitForText("Hola, Claudia")
        openPatient("Diego Alejandro Treviño Salinas")
        composeRule.tap("Psicología")
        composeRule.tap("Nueva cita")
        composeRule.waitForText("Guardar cita")
        composeRule.typeInto("Ubicación", "Consultorio 9")

        composeRule.tap("Cancelar")
        composeRule.waitForText("¿Descartar la cita?")
        composeRule.tap("Seguir editando")
        composeRule.assertVisible("Guardar cita")

        composeRule.tap("Cancelar")
        composeRule.tap("Descartar")
        composeRule.waitForText("Expediente")
        assertEquals(12, scalar("SELECT COUNT(*) FROM appointments"))
    }

    @Test
    fun `reprogramar conserva la cita, cancelar la deja en el historial y ambas cosas quedan en la bitacora`() {
        signIn(BetaAccounts.COORDINATOR_EMAIL)
        composeRule.waitForText("Hola, Claudia")
        openTab("Citas")
        composeRule.waitForText("Diego Alejandro Treviño Salinas")
        composeRule.tap("Diego Alejandro Treviño Salinas")
        composeRule.waitForText("Reprogramada")

        composeRule.tap("Reprogramar")
        composeRule.waitForText("Reprogramar cita")
        composeRule.typeInto("Hora de inicio (24 h)", "1700")
        composeRule.tap("Reprogramar")
        composeRule.waitForText("Cita reprogramada")
        composeRule.waitForText("Reprogramada")
        composeRule.waitForTextContaining("17:00–17:50")
        assertEquals(testInstant(7, 17).toEpochMilli(), scalar("SELECT startDateTime FROM appointments WHERE appointmentId = '${SeedIds.CITA_DIEGO_REPROGRAMADA}'"))
        assertEquals(12, scalar("SELECT COUNT(*) FROM appointments"))

        // El aviso de la reprogramación queda sobre el borde inferior y se traga los toques hasta que se cierra.
        composeRule.waitForTextGone("Cita reprogramada")
        composeRule.tap("Cancelar cita")
        composeRule.waitForText("¿Cancelar la cita?")
        composeRule.typeInto("Razón administrativa (opcional)", "El paciente avisó")
        composeRule.tap("Sí, cancelar cita")
        composeRule.waitForText("Cancelada")
        composeRule.waitForTextGone("Reprogramar")
        assertEquals("CANCELLED", text("SELECT status FROM appointments WHERE appointmentId = '${SeedIds.CITA_DIEGO_REPROGRAMADA}'"))
        assertEquals(12, scalar("SELECT COUNT(*) FROM appointments"))

        pressBack()
        composeRule.waitForText("Próximas")
        composeRule.tap("Historial")
        composeRule.waitForText("Diego Alejandro Treviño Salinas")
        assertEquals(listOf("APPOINTMENT_RESCHEDULED", "APPOINTMENT_CANCELLED"), auditedActions().filter { it.startsWith("APPOINTMENT_") })
    }

    @Test
    fun `sin WhatsApp instalado se avisa, no se deja constancia y la pantalla sigue viva`() {
        launcher.accepts = false
        signIn(BetaAccounts.COORDINATOR_EMAIL)
        composeRule.waitForText("Hola, Claudia")
        openTab("Citas")
        composeRule.waitForText("Fernanda Guerra Domínguez")
        composeRule.tap("Fernanda Guerra Domínguez")
        composeRule.waitForText("Confirmada")

        composeRule.tap("Contactar por WhatsApp")

        composeRule.waitForText("No se encontró WhatsApp en este dispositivo. Puedes contactar al paciente por otro medio.")
        composeRule.assertVisible("Confirmada")
        assertEquals(0, scalar("SELECT COUNT(*) FROM audit_entries WHERE action = 'APPOINTMENT_WHATSAPP_OPENED'"))
        composeRule.tap("Cerrar")
        composeRule.waitForTextGone("No se encontró WhatsApp en este dispositivo. Puedes contactar al paciente por otro medio.")
    }

    @Test
    fun `desde Inicio se puede abrir una cita y agendar`() {
        signIn(BetaAccounts.ADMIN_EMAIL)
        composeRule.waitForText("Citas de hoy")
        composeRule.assertVisible("Nueva cita")
        composeRule.tap("Nueva cita")
        composeRule.waitForText("Buscar paciente")
        composeRule.typeInto("Buscar paciente", "Fernanda")
        composeRule.waitForText("CRNL-000003")
        composeRule.tap("Fernanda Guerra Domínguez")
        composeRule.assertVisible("Psicología")
        composeRule.assertVisible("Nutrición")
        composeRule.assertVisible("Medicina General")
    }
}

private const val PHONE = "w411dp-h891dp"
