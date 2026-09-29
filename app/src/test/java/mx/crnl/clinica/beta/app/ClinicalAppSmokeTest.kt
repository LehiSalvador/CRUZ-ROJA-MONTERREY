package mx.crnl.clinica.beta.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mx.crnl.clinica.beta.BuildConfig
import mx.crnl.clinica.beta.core.ui.theme.ClinicalTheme
import mx.crnl.clinica.beta.testing.FakeAppContainer
import mx.crnl.clinica.beta.testing.FakeAppointmentRepository
import mx.crnl.clinica.beta.testing.FakePatientRepository
import mx.crnl.clinica.beta.testing.FakeSessionRepository
import mx.crnl.clinica.beta.testing.TestNow
import mx.crnl.clinica.beta.testing.domainAppointment
import mx.crnl.clinica.beta.testing.domainPatient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Recorre la aplicación real (navegación, ViewModels y pantallas) sobre repositorios en memoria. */
@RunWith(AndroidJUnit4::class)
class ClinicalAppSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val session = FakeSessionRepository()
    private val patients = FakePatientRepository(
        listOf(
            domainPatient(id = "p1", number = "CRNL-000001", firstName = "Ana", paternalSurname = "Cavazos"),
            domainPatient(id = "p2", number = "CRNL-000002", firstName = "Diego", paternalSurname = "Treviño", maternalSurname = "Salinas"),
        ),
    )
    private val appointments = FakeAppointmentRepository(
        listOf(
            domainAppointment(id = "next", patientName = "Fernanda Guerra Domínguez", start = TestNow.toInstant().plus(Duration.ofDays(1))),
            domainAppointment(id = "old", patientName = "Luis Mireles Cortés", start = TestNow.toInstant().minus(Duration.ofDays(7))),
        ),
    )

    private fun launchApp(container: FakeAppContainer = FakeAppContainer(session, patients, appointments)) {
        composeRule.setContent {
            ClinicalTheme {
                ClinicalApp(container, splashMinimumDisplayMillis = 0)
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openTab(label: String) {
        composeRule.onNode(hasText(label) and hasClickAction()).performClick()
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun signInFromLogin() {
        waitForText("Continuar")
        composeRule.onNodeWithText("Continuar").performClick()
        waitForText("Panel de inicio")
    }

    @Test
    fun `sin sesion recorre splash, acceso y cada pestana de la shell`() {
        launchApp()

        signInFromLogin()
        composeRule.onNodeWithText("Panel de inicio").assertIsDisplayed()
        assertTrue(runBlocking { session.isSessionActive.first() })

        openTab("Pacientes")
        waitForText("Ana Cavazos Ibarra")
        composeRule.onNodeWithText("Diego Treviño Salinas").assertIsDisplayed()

        openTab("Citas")
        waitForText("Fernanda Guerra Domínguez")
        composeRule.onNodeWithText("Próximas").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Historial"))
        composeRule.onNodeWithText("Historial").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Luis Mireles Cortés"))
        composeRule.onNodeWithText("Luis Mireles Cortés").assertIsDisplayed()

        openTab("Solicitudes")
        waitForText("Sin solicitudes")

        openTab("Perfil")
        waitForText("Acerca de")
        composeRule.onNodeWithText("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})").assertIsDisplayed()

        openTab("Inicio")
        waitForText("Panel de inicio")
    }

    @Test
    fun `con sesion activa entra directo a la shell`() {
        runBlocking { session.startSession() }
        launchApp()

        waitForText("Panel de inicio")
        assertTrue(composeRule.onAllNodesWithText("Continuar").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `cerrar sesion desde el perfil regresa al acceso y la sesion queda cerrada`() {
        launchApp()
        signInFromLogin()

        openTab("Perfil")
        waitForText("Acerca de")
        composeRule.onNodeWithText("Cerrar sesión").performClick()

        waitForText("Continuar")
        assertFalse(runBlocking { session.isSessionActive.first() })
    }

    @Test
    fun `atras desde otra pestana vuelve a Inicio y desde Inicio cierra la aplicacion`() {
        launchApp()
        signInFromLogin()
        openTab("Pacientes")
        waitForText("Ana Cavazos Ibarra")

        pressBack()
        waitForText("Panel de inicio")
        assertFalse(composeRule.activity.isFinishing)

        pressBack()
        assertTrue(composeRule.activity.isFinishing)
    }

    @Test
    fun `mostrar el mismo destino dos veces no duplica pantallas en la pila`() {
        launchApp()
        signInFromLogin()

        openTab("Pacientes")
        waitForText("Ana Cavazos Ibarra")
        openTab("Pacientes")
        composeRule.waitForIdle()

        pressBack()
        waitForText("Panel de inicio")
    }

    @Test
    fun `sin pacientes muestra el estado vacio`() {
        patients.patients.value = emptyList()
        launchApp()
        signInFromLogin()

        openTab("Pacientes")

        waitForText("Sin pacientes registrados")
    }

    @Test
    fun `un fallo de lectura muestra el error y reintentar recupera la lista`() {
        patients.failing = true
        launchApp()
        signInFromLogin()

        openTab("Pacientes")
        waitForText("No se pudo cargar la información")

        patients.failing = false
        composeRule.onNodeWithText("Reintentar").performClick()

        waitForText("Ana Cavazos Ibarra")
    }
}
