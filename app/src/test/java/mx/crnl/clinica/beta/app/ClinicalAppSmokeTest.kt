package mx.crnl.clinica.beta.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mx.crnl.clinica.beta.BuildConfig
import mx.crnl.clinica.beta.core.ui.theme.ClinicalTheme
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.testing.FakeAppContainer
import mx.crnl.clinica.beta.testing.FakeAppointmentRepository
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.FakeHomeRepository
import mx.crnl.clinica.beta.testing.FakePatientRepository
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.assertVisible
import mx.crnl.clinica.beta.testing.domainAppointment
import mx.crnl.clinica.beta.testing.domainPatient
import mx.crnl.clinica.beta.testing.emptyHomeSummary
import mx.crnl.clinica.beta.testing.hasTextNow
import mx.crnl.clinica.beta.testing.patientDetail
import mx.crnl.clinica.beta.testing.patientRecord
import mx.crnl.clinica.beta.testing.tap
import mx.crnl.clinica.beta.testing.typeInto
import mx.crnl.clinica.beta.testing.userAccount
import mx.crnl.clinica.beta.testing.waitForText
import mx.crnl.clinica.beta.testing.waitForTextGone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Recorre la aplicación real (navegación, ViewModels y pantallas) sobre repositorios en memoria. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_SCREEN)
class ClinicalAppSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val mariana = userAccount(id = "mariana", firstName = "Mariana", email = "mariana@example.org")
    private val pending = userAccount(id = "pendiente", email = "pendiente@example.org", status = AccountStatus.PENDING_APPROVAL)
    private val auth = FakeAuthRepository(listOf(mariana to PASSWORD, pending to PASSWORD))

    private val ana = domainPatient(id = "p1", number = "CRNL-000001", firstName = "Ana", paternalSurname = "Cavazos")
    private val diego = domainPatient(id = "p2", number = "CRNL-000002", firstName = "Diego", paternalSurname = "Treviño", maternalSurname = "Salinas")
    private val patients = FakePatientRepository(listOf(patientRecord(ana, phone = "+528100000101"), patientRecord(diego))).apply {
        details.value = mapOf(
            "p1" to patientDetail(ana),
            "p2" to patientDetail(diego),
        )
    }
    private val appointments = FakeAppointmentRepository(
        listOf(
            domainAppointment(id = "next", patientName = "Fernanda Guerra Domínguez", start = TestNow.toInstant().plus(Duration.ofDays(1)), professionalId = "mariana"),
            domainAppointment(id = "old", patientName = "Luis Mireles Cortés", start = TestNow.toInstant().minus(Duration.ofDays(7)), professionalId = "mariana"),
        ),
    )
    private val home = FakeHomeRepository(
        emptyHomeSummary().copy(patientCount = 3, upcomingAppointmentCount = 2, recentPatients = listOf(ana)),
    )

    private fun launchApp(container: FakeAppContainer = FakeAppContainer(authRepository = auth, patientRepository = patients, appointmentRepository = appointments, homeRepository = home)) {
        composeRule.setContent {
            ClinicalTheme {
                ClinicalApp(container, splashMinimumDisplayMillis = 0, searchDebounceMillis = 0)
            }
        }
    }

    private fun openTab(label: String) {
        composeRule.onNode(hasText(label) and hasClickAction()).performClick()
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun signIn(email: String = "mariana@example.org", password: String = PASSWORD) {
        composeRule.typeInto("Correo electrónico", email)
        composeRule.typeInto("Contraseña", password)
        composeRule.tap("Iniciar sesión")
    }

    private fun signInAndWaitForHome() {
        signIn()
        composeRule.waitForText("Hola, Mariana")
    }

    @Test
    fun `sin sesion recorre splash, acceso y cada pestana de la shell`() {
        launchApp()

        signInAndWaitForHome()
        assertEquals(mariana, runBlocking { auth.currentUser.first() })

        openTab("Pacientes")
        composeRule.waitForText("Ana Cavazos Ibarra")
        composeRule.onNodeWithText("Diego Treviño Salinas").assertIsDisplayed()

        openTab("Citas")
        composeRule.waitForText("Fernanda Guerra Domínguez")
        composeRule.onNodeWithText("Próximas").assertIsDisplayed()
        composeRule.tap("Historial")
        composeRule.waitForText("Luis Mireles Cortés")
        composeRule.onNodeWithText("Luis Mireles Cortés").assertIsDisplayed()

        openTab("Solicitudes")
        composeRule.waitForText("Sin solicitudes")

        openTab("Perfil")
        composeRule.waitForText("Acerca de")
        composeRule.onNodeWithText("mariana@example.org").assertIsDisplayed()
        composeRule.assertVisible("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

        openTab("Inicio")
        composeRule.waitForText("Hola, Mariana")
    }

    @Test
    fun `con sesion activa entra directo a la shell`() {
        val session = FakeAuthRepository(listOf(mariana to PASSWORD), initialUserId = "mariana")
        launchApp(FakeAppContainer(authRepository = session, homeRepository = home))

        composeRule.waitForText("Hola, Mariana")
        assertFalse(composeRule.hasTextNow("Iniciar sesión"))
    }

    @Test
    fun `una sesion guardada de una cuenta suspendida va al acceso y no a la shell`() {
        val suspended = mariana.copy(status = AccountStatus.SUSPENDED)
        val session = FakeAuthRepository(listOf(suspended to PASSWORD), initialUserId = "mariana")
        launchApp(FakeAppContainer(authRepository = session, homeRepository = home))

        composeRule.waitForText("Iniciar sesión")
        assertFalse(composeRule.hasTextNow("Hola, Mariana"))
    }

    @Test
    fun `una contrasena incorrecta se rechaza y se queda en el acceso`() {
        launchApp()

        signIn(password = "otra-clave")

        composeRule.waitForText("Correo o contraseña incorrectos.")
        assertFalse(composeRule.hasTextNow("Hola, Mariana"))
        assertNull(runBlocking { auth.currentUser.first() })
    }

    @Test
    fun `una cuenta pendiente de aprobacion no entra y se le explica por que`() {
        launchApp()

        signIn(email = "pendiente@example.org")

        composeRule.waitForText("Tu solicitud de cuenta está pendiente de aprobación.")
        assertNull(runBlocking { auth.currentUser.first() })
    }

    @Test
    fun `solicitar una cuenta muestra la confirmacion y regresa al acceso`() {
        launchApp()
        composeRule.tap("Solicitar cuenta")
        composeRule.waitForText("Enviar solicitud")

        composeRule.typeInto("Nombre(s)", "Camila")
        composeRule.typeInto("Apellido paterno", "Ríos")
        composeRule.typeInto("Correo electrónico", "camila.rios@example.org")
        composeRule.typeInto("Contraseña", "clave-de-prueba-1")
        composeRule.typeInto("Confirmar contraseña", "clave-de-prueba-1")
        composeRule.tap("Nutrición")
        composeRule.tap("Profesional")
        composeRule.typeInto("Cédula profesional", "12345678")
        composeRule.tap("Enviar solicitud")

        composeRule.waitForText("Solicitud enviada")
        assertNull("solicitar una cuenta no inicia sesión", runBlocking { auth.currentUser.first() })

        composeRule.tap("Volver al acceso")
        composeRule.waitForText("Iniciar sesión")
        signIn(email = "camila.rios@example.org", password = "clave-de-prueba-1")
        composeRule.waitForText("Tu solicitud de cuenta está pendiente de aprobación.")
    }

    @Test
    fun `solicitar una cuenta valida los campos y no envia con errores`() {
        launchApp()
        composeRule.tap("Solicitar cuenta")

        composeRule.tap("Enviar solicitud")

        composeRule.waitForText("Este campo es obligatorio")
        assertTrue(auth.submittedRequests.isEmpty())
    }

    @Test
    fun `cerrar sesion desde el perfil regresa al acceso, deja la sesion cerrada y Atras no vuelve a la zona autenticada`() {
        launchApp()
        signInAndWaitForHome()

        openTab("Perfil")
        composeRule.waitForText("Acerca de")
        composeRule.tap("Cerrar sesión")

        composeRule.waitForText("Iniciar sesión")
        assertNull(runBlocking { auth.currentUser.first() })

        pressBack()
        assertTrue("Atrás desde el acceso cierra la app, no regresa a la sesión", composeRule.activity.isFinishing)
    }

    @Test
    fun `atras desde otra pestana vuelve a Inicio y desde Inicio cierra la aplicacion`() {
        launchApp()
        signInAndWaitForHome()
        openTab("Pacientes")
        composeRule.waitForText("Ana Cavazos Ibarra")

        pressBack()
        composeRule.waitForText("Hola, Mariana")
        assertFalse(composeRule.activity.isFinishing)

        pressBack()
        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun `mostrar el mismo destino dos veces no duplica pantallas en la pila`() {
        launchApp()
        signInAndWaitForHome()

        openTab("Pacientes")
        composeRule.waitForText("Ana Cavazos Ibarra")
        openTab("Pacientes")
        composeRule.waitForIdle()

        pressBack()
        composeRule.waitForText("Hola, Mariana")
    }

    @Test
    fun `abrir un paciente muestra su expediente y atras conserva la busqueda del listado`() {
        launchApp()
        signInAndWaitForHome()
        openTab("Pacientes")
        composeRule.typeInto("Buscar paciente", "trevino")
        composeRule.waitForText("Diego Treviño Salinas")
        composeRule.waitUntil(10_000) { !composeRule.hasTextNow("Ana Cavazos Ibarra") }

        composeRule.tap("Diego Treviño Salinas")
        composeRule.waitForText("Expediente")
        composeRule.assertVisible("CRNL-000002 · 28 años · Femenino")

        pressBack()
        composeRule.waitForText("Diego Treviño Salinas")
        assertFalse("la busqueda se conserva al volver", composeRule.hasTextNow("Ana Cavazos Ibarra"))
    }

    @Test
    fun `al desplazar el expediente la barra superior sigue diciendo de quien es`() {
        launchApp()
        signInAndWaitForHome()
        openTab("Pacientes")
        composeRule.tap("Ana Cavazos Ibarra")
        composeRule.waitForText("Expediente")
        assertFalse("con el encabezado a la vista la barra solo dice Expediente", composeRule.hasTextNow("CRNL-000001"))

        composeRule.onNodeWithText("Evaluaciones").performScrollTo()
        composeRule.waitForText("CRNL-000001")
        composeRule.waitForTextGone("Expediente")
    }

    @Test
    fun `salir del alta con datos capturados pide confirmar y permite seguir editando o descartar`() {
        launchApp()
        signInAndWaitForHome()
        openTab("Pacientes")
        composeRule.tap("Nuevo paciente")
        composeRule.typeInto("Nombre(s)", "Camila")

        pressBack()
        composeRule.waitForText("¿Descartar el registro?")
        composeRule.tap("Seguir editando")
        composeRule.waitForTextGone("¿Descartar el registro?")
        assertTrue("lo capturado se conserva al seguir editando", composeRule.hasTextNow("Camila"))

        pressBack()
        composeRule.waitForText("¿Descartar el registro?")
        composeRule.tap("Descartar")
        composeRule.waitForText("Ana Cavazos Ibarra")
        assertFalse("el alta descartada no deja el formulario", composeRule.hasTextNow("Paso 1 de 6"))
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land")
    fun `en un telefono horizontal la navegacion pasa a un riel lateral y las pestanas siguen funcionando`() {
        launchApp()
        signInAndWaitForHome()
        val profileTab = composeRule.onNode(hasText("Perfil") and hasClickAction()).fetchSemanticsNode()
        assertTrue("el riel queda junto al borde izquierdo", profileTab.boundsInRoot.left < 150f)

        openTab("Pacientes")
        composeRule.waitForText("Ana Cavazos Ibarra")
        openTab("Perfil")
        composeRule.waitForText("Acerca de")
    }

    @Test
    fun `un paciente inexistente en el expediente muestra que no se encontro`() {
        patients.details.value = emptyMap()
        launchApp()
        signInAndWaitForHome()
        openTab("Pacientes")
        composeRule.tap("Ana Cavazos Ibarra")

        composeRule.waitForText("Paciente no encontrado")
    }

    @Test
    fun `sin pacientes muestra el estado vacio`() {
        patients.records.value = emptyList()
        launchApp()
        signInAndWaitForHome()

        openTab("Pacientes")

        composeRule.waitForText("Sin pacientes registrados")
    }

    @Test
    fun `una busqueda sin resultados lo dice sin confundirlo con no tener pacientes`() {
        launchApp()
        signInAndWaitForHome()
        openTab("Pacientes")
        composeRule.typeInto("Buscar paciente", "zzzz")

        composeRule.waitForText("Sin resultados")
        assertFalse(composeRule.hasTextNow("Sin pacientes registrados"))
    }

    @Test
    fun `un fallo de lectura muestra el error y reintentar recupera la lista`() {
        patients.failing = true
        launchApp()
        signInAndWaitForHome()

        openTab("Pacientes")
        composeRule.waitForText("No se pudo cargar la información")

        patients.failing = false
        composeRule.tap("Reintentar")

        composeRule.waitForText("Ana Cavazos Ibarra")
    }

    @Test
    fun `los accesos rapidos de Inicio llevan a las superficies que ya existen`() {
        launchApp()
        signInAndWaitForHome()

        composeRule.tap("Nuevo paciente")
        composeRule.waitForText("Paso 1 de 6")
        pressBack()
        composeRule.waitForText("Pacientes")

        openTab("Inicio")
        composeRule.tap("Buscar paciente")
        composeRule.waitForText("Ana Cavazos Ibarra")
    }

    @Test
    fun `los indicadores de Inicio salen del resumen y no estan escritos en la pantalla`() {
        home.summary.value = emptyHomeSummary().copy(patientCount = 12, upcomingAppointmentCount = 7, pendingRequestCount = 4)
        launchApp()
        signInAndWaitForHome()

        composeRule.waitForText("12")
        composeRule.waitForText("7")
        composeRule.waitForText("4")
    }

    private companion object {
        const val PASSWORD = "clave-de-prueba"
    }
}

/** Un teléfono estándar en vertical; las pruebas de pantallas pequeñas y de fuente grande se hacen en el emulador. */
private const val PHONE_SCREEN = "w411dp-h891dp"
