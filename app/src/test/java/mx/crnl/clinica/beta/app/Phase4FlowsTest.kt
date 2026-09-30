package mx.crnl.clinica.beta.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.core.demo.AssetSeedFileReader
import mx.crnl.clinica.beta.core.demo.DemoDataInitializer
import mx.crnl.clinica.beta.core.demo.DemoSeedLoader
import mx.crnl.clinica.beta.core.ui.theme.ClinicalTheme
import mx.crnl.clinica.beta.domain.account.AccountRequest
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.PatientDraft
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.BetaAccounts
import mx.crnl.clinica.beta.testing.FakeSessionRepository
import mx.crnl.clinica.beta.testing.RoomAppContainer
import mx.crnl.clinica.beta.testing.SeedIds
import mx.crnl.clinica.beta.testing.assertVisible
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.hasTextNow
import mx.crnl.clinica.beta.testing.tap
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

/**
 * Los recorridos de la Fase 4 sobre la aplicación completa: pantallas reales, repositorios reales, Room en memoria y el
 * conjunto ficticio. Cada recorrido cambia de persona cerrando sesión, como se haría en el teléfono.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class Phase4FlowsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: ClinicalDatabase
    private lateinit var container: RoomAppContainer

    @Before
    fun assemble() {
        database = Room.inMemoryDatabaseBuilder(context, ClinicalDatabase::class.java).allowMainThreadQueries().build()
        val session = FakeSessionRepository()
        val initializer = DemoDataInitializer(database.seedDao(), session, DemoSeedLoader(AssetSeedFileReader(context.assets)), fixedClock())
        runBlocking { initializer.initialize() }
        container = RoomAppContainer(database, sessionRepository = session, initializer = initializer)
    }

    @After
    fun close() {
        database.close()
    }

    private fun launch() {
        composeRule.setContent {
            ClinicalTheme { ClinicalApp(container, splashMinimumDisplayMillis = 0, searchDebounceMillis = 0) }
        }
    }

    private fun openTab(label: String) {
        composeRule.onNode(hasText(label) and hasClickAction()).performClick()
    }

    private fun signIn(email: String) {
        composeRule.typeInto("Correo electrónico", email)
        composeRule.typeInto("Contraseña", BetaAccounts.PASSWORD)
        composeRule.tap("Iniciar sesión")
    }

    private fun signOut() {
        openTab("Perfil")
        composeRule.tap("Cerrar sesión")
        composeRule.waitForText("Acceso del personal")
    }

    private fun switchTo(email: String) {
        signOut()
        signIn(email)
    }

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use {
        it.moveToFirst()
        it.getLong(0)
    }

    private fun text(sql: String): String? = database.openHelper.writableDatabase.query(sql).use {
        it.moveToFirst()
        if (it.isNull(0)) null else it.getString(0)
    }

    private fun openPatient(fullName: String) {
        openTab("Pacientes")
        composeRule.typeInto("Buscar paciente", fullName.split(" ").let { it[it.size - 2] })
        composeRule.waitForText(fullName)
        composeRule.tap(fullName)
        composeRule.waitForText("Expediente")
    }

    private fun requestAccount(email: String, area: ClinicalArea = ClinicalArea.PSYCHOLOGY) {
        runBlocking {
            container.authRepository.requestAccount(
                AccountRequest("Nueva", "Persona", "Ejemplo", email, "clave-de-prueba-1", "clave-de-prueba-1", area, UserRole.PROFESSIONAL, "76543210"),
            )
        }
    }

    // ---------------------------------------------------------------- A y B: cuentas

    @Test
    fun `una cuenta solicitada la aprueba coordinacion y despues inicia sesion`() {
        requestAccount("psicologa.nueva@example.org")
        launch()
        signIn(BetaAccounts.COORDINATOR_EMAIL)
        composeRule.waitForText("Hola, Claudia")

        openTab("Solicitudes")
        composeRule.tap("Nueva Persona Ejemplo")
        composeRule.waitForText("Aprobar cuenta")
        composeRule.assertVisible("psicologa.nueva@example.org")
        composeRule.tap("Aprobar cuenta")
        composeRule.waitForTextContaining("Cuenta aprobada")

        assertEquals("ACTIVE", text("SELECT status FROM demo_users WHERE email = 'psicologa.nueva@example.org'"))
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_entries WHERE action = 'USER_APPROVED'"))

        signOut()
        composeRule.typeInto("Correo electrónico", "psicologa.nueva@example.org")
        composeRule.typeInto("Contraseña", "clave-de-prueba-1")
        composeRule.tap("Iniciar sesión")
        composeRule.waitForText("Hola, Nueva")
    }

    @Test
    fun `una cuenta pendiente no entra y una rechazada tampoco`() {
        launch()
        composeRule.typeInto("Correo electrónico", BetaAccounts.PENDING_EMAIL)
        composeRule.typeInto("Contraseña", BetaAccounts.PASSWORD)
        composeRule.tap("Iniciar sesión")
        composeRule.waitForText("Tu solicitud de cuenta está pendiente de aprobación.")

        signIn(BetaAccounts.ADMIN_EMAIL)
        composeRule.waitForText("Hola, Héctor")
        openTab("Solicitudes")
        composeRule.tap("Valeria Ramos Cisneros")
        composeRule.tap("Rechazar solicitud")
        composeRule.waitForText("¿Rechazar la solicitud?")
        composeRule.tap("Rechazar")
        composeRule.waitForTextContaining("Solicitud rechazada")
        assertEquals("REJECTED", text("SELECT status FROM demo_users WHERE email = '${BetaAccounts.PENDING_EMAIL}'"))

        signOut()
        composeRule.typeInto("Correo electrónico", BetaAccounts.PENDING_EMAIL)
        composeRule.typeInto("Contraseña", BetaAccounts.PASSWORD)
        composeRule.tap("Iniciar sesión")
        composeRule.waitForText("Tu solicitud de cuenta fue rechazada.")
    }

    @Test
    fun `coordinacion no ve las cuentas de otra area y administracion clinica si`() {
        launch()
        signIn(BetaAccounts.COORDINATOR_EMAIL)
        composeRule.waitForText("Hola, Claudia")
        openTab("Solicitudes")
        // Solo la cuenta rechazada de su área aparece como historial; la pendiente de Nutrición no es suya.
        composeRule.waitForText("Iván Rangel Solís")
        assertFalse(composeRule.hasTextNow("Valeria Ramos Cisneros"))

        switchTo(BetaAccounts.ADMIN_EMAIL)
        composeRule.waitForText("Hola, Héctor")
        openTab("Solicitudes")
        composeRule.waitForText("Valeria Ramos Cisneros")
    }

    // ---------------------------------------------------------------- C y D: acceso interárea

    @Test
    fun `un profesional pide lectura de otra area, coordinacion la aprueba y despues la revoca`() {
        launch()
        signIn(BetaAccounts.NUTRITIONIST_EMAIL)
        composeRule.waitForText("Hola, Paola")
        openPatient("Fernanda Guerra Domínguez")

        // Psicología es ajena para Nutrición: solo hay constancia de atención y la opción de pedir lectura.
        composeRule.waitForText("Solicitar acceso de lectura")
        composeRule.tap("Solicitar acceso de lectura")
        composeRule.waitForText("Motivo administrativo")
        composeRule.tap("Enviar solicitud")
        composeRule.waitForText("Escribe un motivo de entre 10 y 300 caracteres.")
        composeRule.typeInto("Motivo administrativo", "Necesito consultar el seguimiento psicológico")
        composeRule.tap("Enviar solicitud")
        composeRule.waitForText("Solicitud enviada")
        composeRule.waitForText("Ver solicitud pendiente")
        // Un aviso vigente tapa el borde inferior y se traga los toques: se espera a que se retire.
        composeRule.waitForTextGone("Solicitud enviada")
        assertEquals(1, scalar("SELECT COUNT(*) FROM interarea_access_requests WHERE status = 'PENDING'"))

        // Coordinación de Psicología (área propietaria) la revisa.
        switchTo(BetaAccounts.COORDINATOR_EMAIL)
        composeRule.waitForText("Hola, Claudia")
        openTab("Solicitudes")
        composeRule.tap("Acceso interárea · 1")
        composeRule.tap("Fernanda Guerra Domínguez")
        composeRule.waitForText("Aprobar acceso de lectura")
        composeRule.assertVisible("Necesito consultar el seguimiento psicológico")
        composeRule.tap("Aprobar acceso de lectura")
        composeRule.waitForText("Acceso de lectura aprobado.")
        assertEquals(1, scalar("SELECT COUNT(*) FROM access_grants WHERE status = 'ACTIVE'"))

        // La persona solicitante ya lee Psicología, en solo lectura y con la vigencia a la vista.
        switchTo(BetaAccounts.NUTRITIONIST_EMAIL)
        composeRule.waitForText("Hola, Paola")
        openPatient("Fernanda Guerra Domínguez")
        composeRule.tap("Psicología")
        composeRule.waitForText("Acceso temporal de lectura")
        assertTrue(composeRule.hasTextContainingNow("Rodrigo Villarreal"))
        assertFalse(composeRule.hasTextNow("Registrar atención"))
        assertFalse(composeRule.hasTextNow("Nueva cita"))
        assertFalse(composeRule.hasTextNow("Asignar profesional"))
        assertFalse(composeRule.hasTextNow("Solicitar cambio de profesional"))
        assertFalse(composeRule.hasTextNow("Solicitar acceso de lectura"))

        // Coordinación revoca y la lectura se retira.
        switchTo(BetaAccounts.COORDINATOR_EMAIL)
        composeRule.waitForText("Hola, Claudia")
        openTab("Solicitudes")
        composeRule.tap("Acceso interárea")
        composeRule.tap("Fernanda Guerra Domínguez")
        composeRule.waitForText("Revocar acceso")
        composeRule.tap("Revocar acceso")
        composeRule.waitForText("¿Revocar el acceso?")
        composeRule.tap("Revocar")
        composeRule.waitForText("Acceso revocado.")
        assertEquals("REVOKED", text("SELECT status FROM access_grants"))

        switchTo(BetaAccounts.NUTRITIONIST_EMAIL)
        composeRule.waitForText("Hola, Paola")
        openPatient("Fernanda Guerra Domínguez")
        composeRule.tap("Psicología")
        composeRule.waitForText("El detalle solo lo ven quienes atienden esta área.")
        assertFalse(composeRule.hasTextNow("Acceso temporal de lectura"))
    }

    @Test
    fun `quien pidio el acceso no puede resolverlo y otra area de coordinacion no lo ve`() {
        runBlocking {
            container.accessRequestRepository.create(
                mx.crnl.clinica.beta.domain.model.AccessRequestDraft(SeedIds.FERNANDA, ClinicalArea.NUTRITION, "Necesito consultar el seguimiento nutricional"),
                SeedIds.MARIANA,
            )
        }
        launch()
        signIn(BetaAccounts.PSYCHOLOGIST_EMAIL)
        composeRule.waitForText("Hola, Mariana")
        openTab("Solicitudes")
        composeRule.tap("Fernanda Guerra Domínguez")
        composeRule.waitForText("Tu solicitud está pendiente. La revisa la coordinación del área o la administración clínica.")
        assertFalse(composeRule.hasTextNow("Aprobar acceso de lectura"))

        switchTo(BetaAccounts.COORDINATOR_EMAIL)
        composeRule.waitForText("Hola, Claudia")
        openTab("Solicitudes")
        composeRule.tap("Acceso interárea")
        composeRule.waitForText("Sin solicitudes de acceso")
    }

    // ---------------------------------------------------------------- E: cambio de profesional

    @Test
    fun `el cambio de profesional lo pide el asignado y lo aprueba coordinacion sin perder la historia`() {
        launch()
        signIn(BetaAccounts.SECOND_PSYCHOLOGIST_EMAIL)
        composeRule.waitForText("Hola, Rodrigo")
        openPatient("Fernanda Guerra Domínguez")
        composeRule.tap("Psicología")
        composeRule.waitForText("Solicitar cambio de profesional")
        composeRule.tap("Solicitar cambio de profesional")
        composeRule.waitForText("Profesionales del área")
        composeRule.tap("Mariana Elizondo Cantú")
        composeRule.typeInto("Motivo administrativo", "Cambio por carga de agenda del profesional")
        composeRule.tap("Solicitar cambio")
        composeRule.waitForText("Solicitud de cambio enviada")
        composeRule.waitForText("Ver cambio de profesional pendiente")
        composeRule.waitForTextGone("Solicitud de cambio enviada")
        // Todavía no cambió nada.
        assertEquals(SeedIds.RODRIGO, text("SELECT professionalId FROM professional_assignments WHERE patientId = '${SeedIds.FERNANDA}' AND areaCode = 'PSYCHOLOGY' AND status = 'ACTIVE'"))

        switchTo(BetaAccounts.COORDINATOR_EMAIL)
        composeRule.waitForText("Hola, Claudia")
        openTab("Solicitudes")
        composeRule.tap("Cambio de profesional · 1")
        composeRule.tap("Fernanda Guerra Domínguez")
        composeRule.waitForText("Hay 1 cita por ocurrir con el profesional actual")
        composeRule.tap("Aprobar cambio")
        composeRule.waitForText("¿Aprobar el cambio?")
        composeRule.tap("Confirmar cambio")
        composeRule.waitForText("Cambio de profesional aprobado. Se abrió una nueva asignación.")

        assertEquals(SeedIds.MARIANA, text("SELECT professionalId FROM professional_assignments WHERE patientId = '${SeedIds.FERNANDA}' AND areaCode = 'PSYCHOLOGY' AND status = 'ACTIVE'"))
        assertEquals(3, scalar("SELECT COUNT(*) FROM professional_assignments WHERE patientId = '${SeedIds.FERNANDA}' AND areaCode = 'PSYCHOLOGY'"))

        // El expediente muestra las tres asignaciones del área, la nueva vigente.
        composeRule.tap("Abrir expediente")
        composeRule.waitForText("Expediente")
        composeRule.tap("Psicología")
        composeRule.waitForText("Historial de asignaciones")
        assertTrue(composeRule.hasTextContainingNow("Mariana Elizondo"))
    }

    @Test
    fun `un profesional no ve el cambio de profesional de un paciente ajeno`() {
        launch()
        signIn(BetaAccounts.PSYCHOLOGIST_EMAIL)
        composeRule.waitForText("Hola, Mariana")
        openPatient("Fernanda Guerra Domínguez")
        composeRule.tap("Psicología")
        composeRule.waitForText("Profesional asignado")
        assertFalse(composeRule.hasTextNow("Solicitar cambio de profesional"))
    }

    // ---------------------------------------------------------------- F y G: evaluaciones

    @Test
    fun `las evaluaciones se ven como instrumentos genericos sin interpretacion ni nombres reales`() {
        launch()
        signIn(BetaAccounts.PSYCHOLOGIST_EMAIL)
        composeRule.waitForText("Hola, Mariana")
        openPatient("Ana Lucía Cavazos Ibarra")
        composeRule.tap("Psicología")
        composeRule.waitForText("Evaluaciones")
        composeRule.tap("Instrumento A")
        composeRule.waitForText("Resultado")
        composeRule.assertVisible("Captura profesional")
        composeRule.assertVisible("Sin\u00A0clasificación")
        composeRule.assertVisible("18")
        composeRule.assertVisible("placeholder-0")
        listOf("BAI", "BDI", "Beck", "leve", "moderad", "severo", "ansiedad", "depresión").forEach { forbidden ->
            assertFalse("no debe aparecer «$forbidden»", composeRule.hasTextContainingNow(forbidden))
        }
    }

    @Test
    fun `una aplicacion cancelada se muestra sin resultado`() {
        launch()
        signIn(BetaAccounts.SECOND_PSYCHOLOGIST_EMAIL)
        composeRule.waitForText("Hola, Rodrigo")
        openPatient("Diego Alejandro Treviño Salinas")
        composeRule.tap("Psicología")
        composeRule.waitForText("Instrumento B")
        composeRule.tap("Instrumento B")
        composeRule.waitForText("Cancelada · Sin resultado")
        assertFalse(composeRule.hasTextNow("Puntaje registrado"))
    }

    @Test
    fun `el modo supervisado es una pantalla aislada que no crea nada y devuelve con confirmacion`() {
        launch()
        signIn(BetaAccounts.SECOND_PSYCHOLOGIST_EMAIL)
        composeRule.waitForText("Hola, Rodrigo")
        openPatient("Diego Alejandro Treviño Salinas")
        composeRule.tap("Psicología")
        composeRule.waitForText("Instrumento A")
        composeRule.tap("Instrumento A")
        composeRule.waitForText("Aplicación supervisada")
        val assessmentsBefore = scalar("SELECT COUNT(*) FROM assessments")
        val resultsBefore = scalar("SELECT COUNT(*) FROM assessment_results")

        composeRule.tap("Ver modo supervisado")
        composeRule.waitForText("El contenido del instrumento se habilita con la versión institucional autorizada.")
        // Aislada: sin barra inferior, sin datos del paciente y sin contenido del instrumento.
        assertFalse(composeRule.hasTextNow("Inicio"))
        assertFalse(composeRule.hasTextNow("Pacientes"))
        assertFalse(composeRule.hasTextContainingNow("Diego"))
        assertFalse(composeRule.hasTextContainingNow("CRNL-"))
        assertFalse(composeRule.hasTextContainingNow("Resultado"))

        composeRule.tap("Volver con profesional")
        composeRule.waitForText("¿Volver con el profesional?")
        composeRule.tap("Volver")
        composeRule.waitForText("Resultado")

        assertEquals(assessmentsBefore, scalar("SELECT COUNT(*) FROM assessments"))
        assertEquals(resultsBefore, scalar("SELECT COUNT(*) FROM assessment_results"))
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_entries WHERE action = 'SUPERVISED_PREVIEW_OPENED'"))
    }

    @Test
    fun `Atras en el modo supervisado pide confirmar antes de salir`() {
        launch()
        signIn(BetaAccounts.SECOND_PSYCHOLOGIST_EMAIL)
        composeRule.waitForText("Hola, Rodrigo")
        openPatient("Diego Alejandro Treviño Salinas")
        composeRule.tap("Psicología")
        composeRule.tap("Instrumento A")
        composeRule.tap("Ver modo supervisado")
        composeRule.waitForText("Volver con profesional")

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForText("¿Volver con el profesional?")
        composeRule.tap("Volver")
        composeRule.waitForText("Resultado")
    }

    @Test
    fun `otra area no ve las evaluaciones de Psicologia`() {
        launch()
        signIn(BetaAccounts.NUTRITIONIST_EMAIL)
        composeRule.waitForText("Hola, Paola")
        openPatient("Fernanda Guerra Domínguez")
        composeRule.tap("Psicología")
        composeRule.waitForText("El detalle solo lo ven quienes atienden esta área.")
        assertFalse(composeRule.hasTextNow("Evaluaciones"))
        assertFalse(composeRule.hasTextNow("Instrumento B"))
    }

    // ---------------------------------------------------------------- H, I y L: administración

    @Test
    fun `administracion clinica consulta la bitacora con filtros y sin datos sensibles`() {
        launch()
        signIn(BetaAccounts.ADMIN_EMAIL)
        composeRule.waitForText("Hola, Héctor")
        openTab("Perfil")
        composeRule.tap("Auditoría")
        composeRule.waitForText("Inicio de sesión")
        composeRule.waitForText("Otra acción")
        composeRule.tap("Sesiones")
        composeRule.waitForTextGone("Otra acción")
        composeRule.waitForText("Inicio de sesión")
        composeRule.tap("Sistema")
        composeRule.waitForText("Otra acción")
        assertFalse(composeRule.hasTextContainingNow("@example.org"))
    }

    @Test
    fun `administracion clinica suspende una cuenta activa y esa cuenta ya no entra`() {
        launch()
        signIn(BetaAccounts.ADMIN_EMAIL)
        composeRule.waitForText("Hola, Héctor")
        openTab("Perfil")
        composeRule.tap("Usuarios")
        composeRule.waitForText("Pendientes (1)")
        composeRule.tap("Activos (6)")
        composeRule.tap("Paola Garza Leal")
        composeRule.tap("Suspender cuenta")
        composeRule.waitForText("¿Suspender la cuenta?")
        composeRule.tap("Suspender")
        composeRule.waitForTextContaining("Cuenta suspendida")
        assertEquals("SUSPENDED", text("SELECT status FROM demo_users WHERE email = '${BetaAccounts.NUTRITIONIST_EMAIL}'"))

        signOut()
        composeRule.typeInto("Correo electrónico", BetaAccounts.NUTRITIONIST_EMAIL)
        composeRule.typeInto("Contraseña", BetaAccounts.PASSWORD)
        composeRule.tap("Iniciar sesión")
        composeRule.waitForText("Tu cuenta está suspendida. Comunícate con un administrador.")
    }

    @Test
    fun `restablecer los datos de Beta pide dos confirmaciones, vuelve al inicio de sesion y restaura el seed`() {
        runBlocking {
            container.patientRepository.createPatient(
                PatientDraft("Camila", "Ríos", "Soto", LocalDate.of(1990, 1, 15), null, Sex.FEMALE, null, PopulationType.GENERAL_PUBLIC, "8100009999", null),
                SeedIds.HECTOR,
            )
        }
        assertEquals(9, scalar("SELECT COUNT(*) FROM patients"))
        launch()
        signIn(BetaAccounts.ADMIN_EMAIL)
        composeRule.waitForText("Hola, Héctor")
        openTab("Perfil")
        composeRule.tap("Herramientas de Beta")
        composeRule.waitForText("Elimina los cambios locales hechos durante el uso", substring = true)

        composeRule.tap("Restablecer datos de Beta")
        composeRule.waitForText("Se eliminarán los cambios locales y se restaurarán los datos ficticios iniciales. ¿Quieres continuar?")
        assertEquals("nada se borra con la primera confirmación", 9, scalar("SELECT COUNT(*) FROM patients"))
        composeRule.tap("Continuar")
        composeRule.waitForText("Esta acción no se puede deshacer y cerrará tu sesión. ¿Restablecer ahora?")
        composeRule.tap("Restablecer ahora")

        composeRule.waitForText("Acceso del personal")
        assertEquals(8, scalar("SELECT COUNT(*) FROM patients"))
        assertEquals(0, scalar("SELECT COUNT(*) FROM patients WHERE firstName = 'Camila'"))
        signIn(BetaAccounts.PSYCHOLOGIST_EMAIL)
        composeRule.waitForText("Hola, Mariana")
    }

    @Test
    fun `cancelar la primera confirmacion no restablece nada`() {
        launch()
        signIn(BetaAccounts.ADMIN_EMAIL)
        composeRule.waitForText("Hola, Héctor")
        openTab("Perfil")
        composeRule.tap("Herramientas de Beta")
        composeRule.tap("Restablecer datos de Beta")
        composeRule.waitForText("¿Quieres continuar?", substring = true)
        composeRule.tap("Cancelar")
        composeRule.waitForTextGone("¿Quieres continuar?", substring = true)
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_entries WHERE action = 'LOGIN'"))
    }

    // ---------------------------------------------------------------- J y K: roles, Home y navegación

    @Test
    fun `cada rol ve solo las herramientas y bandejas que le corresponden`() {
        launch()
        signIn(BetaAccounts.PSYCHOLOGIST_EMAIL)
        composeRule.waitForText("Hola, Mariana")
        openTab("Perfil")
        composeRule.waitForText("Cerrar sesión")
        assertFalse(composeRule.hasTextNow("Administración"))
        openTab("Solicitudes")
        composeRule.waitForText("Sin solicitudes de acceso")
        assertFalse(composeRule.hasTextNow("Cuentas"))

        switchTo(BetaAccounts.COORDINATOR_EMAIL)
        composeRule.waitForText("Hola, Claudia")
        openTab("Perfil")
        composeRule.waitForText("Cerrar sesión")
        assertFalse(composeRule.hasTextNow("Administración"))
        openTab("Solicitudes")
        composeRule.waitForText("Cuentas")

        switchTo(BetaAccounts.ADMIN_EMAIL)
        composeRule.waitForText("Hola, Héctor")
        openTab("Perfil")
        composeRule.waitForText("Administración")
        composeRule.assertVisible("Usuarios")
        composeRule.assertVisible("Auditoría")
        composeRule.assertVisible("Herramientas de Beta")
    }

    @Test
    fun `el Inicio cuenta lo pendiente de cada rol y se actualiza al resolverlo`() {
        launch()
        signIn(BetaAccounts.ADMIN_EMAIL)
        composeRule.waitForText("Hola, Héctor")
        composeRule.waitForText("Cuentas por revisar")
        composeRule.onNode(hasText("Cuentas por revisar")).assertTextEquals("Cuentas por revisar", "1")

        runBlocking {
            container.accountAdministrationRepository.apply(
                SeedIds.VALERIA,
                mx.crnl.clinica.beta.domain.access.AccountAction.APPROVE,
                mx.crnl.clinica.beta.domain.model.AccountStatus.PENDING_APPROVAL,
                SeedIds.HECTOR,
            )
        }
        composeRule.waitUntil(10_000) {
            runCatching { composeRule.onNode(hasText("Cuentas por revisar")).assertTextEquals("Cuentas por revisar", "0") }.isSuccess
        }
    }

    @Test
    fun `tocar la pestana ya abierta regresa a su pantalla raiz`() {
        launch()
        signIn(BetaAccounts.ADMIN_EMAIL)
        composeRule.waitForText("Hola, Héctor")
        openTab("Perfil")
        composeRule.tap("Usuarios")
        composeRule.waitForText("Pendientes (1)")
        composeRule.tap("Activos (6)")
        composeRule.tap("Paola Garza Leal")
        composeRule.waitForText("Suspender cuenta")

        openTab("Perfil")

        composeRule.waitForText("Cerrar sesión")
        assertFalse(composeRule.hasTextNow("Suspender cuenta"))
    }

    @Test
    fun `con fuente normal la barra inferior conserva sus etiquetas`() {
        launch()
        signIn(BetaAccounts.PSYCHOLOGIST_EMAIL)
        composeRule.waitForText("Hola, Mariana")

        composeRule.assertVisible("Solicitudes")
        composeRule.assertVisible("Pacientes")
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.hasTextContainingNow(text: String): Boolean =
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.waitForText(text: String, substring: Boolean) {
        if (substring) waitForTextContaining(text) else waitForText(text)
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.waitForTextGone(text: String, substring: Boolean) {
        waitUntil(20_000) { !onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }
    }
}

private const val PHONE_QUALIFIERS = "w411dp-h891dp"
