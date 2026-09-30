package mx.crnl.clinica.beta.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.core.demo.AssetSeedFileReader
import mx.crnl.clinica.beta.core.demo.DemoDataInitializer
import mx.crnl.clinica.beta.core.demo.DemoSeedLoader
import mx.crnl.clinica.beta.core.ui.theme.ClinicalTheme
import mx.crnl.clinica.beta.testing.FakeSessionRepository
import mx.crnl.clinica.beta.testing.RoomAppContainer
import mx.crnl.clinica.beta.testing.SeedIds
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.hasTextNow
import mx.crnl.clinica.beta.testing.tap
import mx.crnl.clinica.beta.testing.userEntity
import mx.crnl.clinica.beta.testing.waitForText
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * El administrador del sistema (cuenta técnica) ve los datos generales de un paciente pero no obtiene capacidad
 * clínica por su rol: sin agenda, sin detalle de áreas y sin acciones de asignar, agendar o registrar.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class SystemAdminFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: ClinicalDatabase

    @Before
    fun assemble() {
        database = Room.inMemoryDatabaseBuilder(context, ClinicalDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            DemoDataInitializer(database.seedDao(), FakeSessionRepository(), DemoSeedLoader(AssetSeedFileReader(context.assets)), fixedClock()).initialize()
            database.userDao().insert(userEntity(id = SeedIds.SYSTEM_ADMIN, role = "SYSTEM_ADMIN", area = null, firstName = "Sistema", paternalSurname = "Técnico"))
        }
        // La sesión ya está iniciada con la cuenta técnica: la aplicación abre directo en la shell.
        val container = RoomAppContainer(database, sessionRepository = FakeSessionRepository(userId = SeedIds.SYSTEM_ADMIN))
        composeRule.setContent {
            ClinicalTheme { ClinicalApp(container, splashMinimumDisplayMillis = 0, searchDebounceMillis = 0) }
        }
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun `no ve citas ni puede agendar en Inicio ni en la agenda`() {
        composeRule.waitForText("Hola, Sistema")
        assertFalse(composeRule.hasTextNow("Nueva cita"))

        composeRule.tap("Citas")
        composeRule.waitForText("Sin citas programadas")
        assertFalse(composeRule.hasTextNow("Nueva cita"))
    }

    @Test
    fun `ve los datos generales de un paciente pero ningun detalle clinico ni acciones`() {
        composeRule.waitForText("Hola, Sistema")
        composeRule.tap("Pacientes")
        composeRule.waitForText("Fernanda Guerra Domínguez")
        composeRule.tap("Fernanda Guerra Domínguez")
        composeRule.waitForText("Expediente")
        composeRule.waitForText("Datos generales")

        // Solo constancia de que hay atención; nada de quién, cuándo por tipo ni acciones.
        composeRule.waitForText("Atención por área")
        assertTrue(composeRule.hasTextNow("El paciente cuenta con atención registrada en esta área."))
        assertFalse(composeRule.hasTextNow("Rodrigo Villarreal"))
        assertFalse(composeRule.hasTextNow("Paola Garza"))
        assertFalse(composeRule.hasTextNow("Asignar profesional"))
        assertFalse(composeRule.hasTextNow("Nueva cita"))
        assertFalse(composeRule.hasTextNow("Registrar atención"))

        composeRule.tap("Psicología")
        composeRule.waitForText("El paciente cuenta con atención registrada en esta área.")
        assertFalse(composeRule.hasTextNow("Historial de atención"))
    }
}
