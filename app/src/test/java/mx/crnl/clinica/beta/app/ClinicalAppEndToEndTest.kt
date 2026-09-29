package mx.crnl.clinica.beta.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.core.demo.AssetSeedFileReader
import mx.crnl.clinica.beta.core.demo.DemoDataInitializer
import mx.crnl.clinica.beta.core.demo.DemoSeedLoader
import mx.crnl.clinica.beta.core.ui.theme.ClinicalTheme
import mx.crnl.clinica.beta.testing.BetaAccounts
import mx.crnl.clinica.beta.testing.FakeSessionRepository
import mx.crnl.clinica.beta.testing.RoomAppContainer
import mx.crnl.clinica.beta.testing.assertVisible
import mx.crnl.clinica.beta.testing.fixedClock
import mx.crnl.clinica.beta.testing.hasTextNow
import mx.crnl.clinica.beta.testing.scrollListTo
import mx.crnl.clinica.beta.testing.tap
import mx.crnl.clinica.beta.testing.typeInto
import mx.crnl.clinica.beta.testing.waitForText
import mx.crnl.clinica.beta.testing.waitForTextGone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Recorre la aplicación completa: pantallas reales sobre repositorios reales, Room en memoria y el conjunto ficticio. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_SCREEN)
class ClinicalAppEndToEndTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: ClinicalDatabase
    private lateinit var container: RoomAppContainer

    @Before
    fun assemble() {
        database = Room.inMemoryDatabaseBuilder(context, ClinicalDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            DemoDataInitializer(database.seedDao(), FakeSessionRepository(), DemoSeedLoader(AssetSeedFileReader(context.assets)), fixedClock()).initialize()
        }
        container = RoomAppContainer(database)
        composeRule.setContent {
            ClinicalTheme {
                ClinicalApp(container, splashMinimumDisplayMillis = 0, searchDebounceMillis = 0)
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

    private fun signIn(email: String, password: String = BetaAccounts.PASSWORD) {
        composeRule.typeInto("Correo electrónico", email)
        composeRule.typeInto("Contraseña", password)
        composeRule.tap("Iniciar sesión")
    }

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use {
        it.moveToFirst()
        it.getLong(0)
    }

    private fun auditedActions(): List<String> =
        database.openHelper.writableDatabase.query("SELECT action FROM audit_entries WHERE action <> 'DEMO_SEED_APPLIED' ORDER BY rowid").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun fillNewPatientUpToConfirmation(
        firstName: String = "Camila",
        paternal: String = "Ríos",
        maternal: String = "Soto",
        birthDate: String = "12071990",
        phone: String = "8123456789",
        email: String = "camila.rios@example.org",
    ) {
        composeRule.waitForText("Paso 1 de 6")
        composeRule.typeInto("Nombre(s)", firstName)
        composeRule.typeInto("Apellido paterno", paternal)
        composeRule.typeInto("Apellido materno (opcional)", maternal)
        composeRule.typeInto("Fecha de nacimiento", birthDate)
        composeRule.tap("Femenino")
        composeRule.tap("Continuar")

        composeRule.waitForText("Paso 2 de 6")
        composeRule.typeInto("Teléfono (opcional)", phone)
        composeRule.typeInto("Correo electrónico (opcional)", email)
        composeRule.typeInto("Municipio (opcional)", "Monterrey")
        composeRule.tap("Continuar")

        composeRule.waitForText("Paso 3 de 6")
        composeRule.onNode(hasText("Población") and hasClickAction()).performClick()
        composeRule.tap("Alumno")
        composeRule.tap("Continuar")

        composeRule.waitForText("Paso 4 de 6")
        composeRule.waitForText("Los formatos de consentimiento institucionales todavía no están configurados, por lo que no se registra ninguna aceptación. Puedes continuar con el registro.")
        composeRule.tap("Continuar")
        composeRule.waitForText("Paso 5 de 6")
    }

    @Test
    fun `recorrido completo de un profesional desde el acceso hasta cerrar sesion`() {
        composeRule.waitForText("Iniciar sesión")
        signIn(BetaAccounts.PSYCHOLOGIST_EMAIL)

        // Inicio con datos reales del usuario.
        composeRule.waitForText("Hola, Mariana")
        composeRule.assertVisible("Mis pacientes")
        composeRule.assertVisible("Pacientes recientes")

        // Pacientes: búsqueda sin acentos, expediente y regreso conservando la búsqueda.
        openTab("Pacientes")
        composeRule.waitForText("Ana Lucía Cavazos Ibarra")
        composeRule.typeInto("Buscar paciente", "trevino")
        composeRule.waitForTextGone("Ana Lucía Cavazos Ibarra")
        composeRule.tap("Diego Alejandro Treviño Salinas")
        composeRule.waitForText("Expediente")
        composeRule.assertVisible("CRNL-000002 · 22 años · Masculino")
        pressBack()
        composeRule.waitForText("Diego Alejandro Treviño Salinas")
        assertFalse("la búsqueda se conserva", composeRule.hasTextNow("Ana Lucía Cavazos Ibarra"))
        composeRule.typeInto("Buscar paciente", "")
        composeRule.waitForText("Ana Lucía Cavazos Ibarra")

        // Alta de un paciente único.
        composeRule.tap("Nuevo paciente")
        fillNewPatientUpToConfirmation()
        composeRule.waitForText("Sin coincidencias")
        composeRule.tap("Continuar")
        composeRule.waitForText("Nombre completo")
        composeRule.assertVisible("Camila Ríos Soto")
        composeRule.tap("Guardar paciente")

        composeRule.waitForText("Paciente registrado con folio CRNL-000009")
        composeRule.waitForText("Expediente")
        composeRule.assertVisible("CRNL-000009 · 36 años · Femenino")
        assertEquals(9, scalar("SELECT COUNT(*) FROM patients"))

        // Edición y persistencia.
        composeRule.tap("Editar")
        composeRule.waitForText("Editar paciente")
        composeRule.typeInto("Municipio (opcional)", "Guadalupe")
        composeRule.tap("Guardar cambios")
        composeRule.waitForText("Cambios guardados")
        composeRule.assertVisible("Guadalupe")
        assertEquals(
            "Guadalupe",
            database.openHelper.writableDatabase.query("SELECT municipality FROM patients WHERE patientNumber = 'CRNL-000009'").use {
                it.moveToFirst()
                it.getString(0)
            },
        )

        // El listado y el Inicio reflejan el alta sin recargar.
        pressBack()
        composeRule.waitForText("Ana Lucía Cavazos Ibarra")
        composeRule.scrollListTo("Camila Ríos Soto")
        composeRule.assertVisible("Camila Ríos Soto")
        openTab("Inicio")
        composeRule.assertVisible("Camila Ríos Soto")

        // Cerrar sesión.
        openTab("Perfil")
        composeRule.waitForText("Acerca de")
        composeRule.assertVisible(BetaAccounts.PSYCHOLOGIST_EMAIL)
        composeRule.tap("Cerrar sesión")
        composeRule.waitForText("Iniciar sesión")
        assertNull(runBlocking { container.authRepository.currentUser.first() })

        assertEquals(
            listOf("LOGIN", "PATIENT_VIEWED", "PATIENT_CREATED", "PATIENT_VIEWED", "PATIENT_UPDATED", "LOGOUT"),
            auditedActions(),
        )
    }

    @Test
    fun `un alta con coincidencias permite revisar al candidato sin perder el formulario y crear de todos modos`() {
        signIn(BetaAccounts.PSYCHOLOGIST_EMAIL)
        composeRule.waitForText("Hola, Mariana")
        openTab("Pacientes")
        composeRule.tap("Nuevo paciente")

        fillNewPatientUpToConfirmation(
            firstName = "Camila",
            paternal = "Ríos",
            email = "ANA.CAVAZOS@example.org",
        )

        composeRule.waitForText("Posible paciente existente")
        composeRule.assertVisible("Ana Lucía Cavazos Ibarra")
        composeRule.assertVisible("Mismo correo")
        assertEquals("aún no se guardó nada", 8, scalar("SELECT COUNT(*) FROM patients"))

        // Revisar al candidato y volver: el formulario sigue intacto y en el mismo paso.
        composeRule.tap("Ver expediente")
        composeRule.waitForText("Expediente")
        composeRule.assertVisible("CRNL-000001 · 28 años · Femenino")
        pressBack()
        composeRule.waitForText("Posible paciente existente")
        composeRule.assertVisible("Paso 5 de 6")

        // Crear de todos modos exige confirmar.
        composeRule.tap("Crear de todos modos")
        composeRule.waitForText("¿Registrar como paciente nuevo?")
        assertEquals(8, scalar("SELECT COUNT(*) FROM patients"))
        composeRule.tap("Registrar como nuevo")
        composeRule.waitForText("Paso 6 de 6")
        composeRule.assertVisible("Camila Ríos Soto")
        composeRule.tap("Guardar paciente")

        composeRule.waitForText("Paciente registrado con folio CRNL-000009")
        assertEquals(9, scalar("SELECT COUNT(*) FROM patients"))
        assertEquals("el correo repetido convive con el original", 2, scalar("SELECT COUNT(*) FROM patient_contacts WHERE contactValue = 'ana.cavazos@example.org'"))
    }

    @Test
    fun `editar un paciente con datos de otro advierte y guardar de todos modos lo escribe`() {
        signIn(BetaAccounts.PSYCHOLOGIST_EMAIL)
        composeRule.waitForText("Hola, Mariana")
        openTab("Pacientes")
        composeRule.tap("Ana Lucía Cavazos Ibarra")
        composeRule.waitForText("Expediente")
        composeRule.tap("Editar")
        composeRule.waitForText("Editar paciente")

        composeRule.typeInto("Correo electrónico (opcional)", "luis.mireles@example.org")
        composeRule.tap("Guardar cambios")

        composeRule.waitForText("Posible paciente existente")
        composeRule.assertVisible("Luis Fernando Mireles Cortés")
        assertEquals("no se guardó todavía", 0, scalar("SELECT COUNT(*) FROM audit_entries WHERE action = 'PATIENT_UPDATED'"))

        composeRule.tap("Guardar de todos modos")
        composeRule.waitForText("Cambios guardados")
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_entries WHERE action = 'PATIENT_UPDATED'"))
        assertEquals("CRNL-000001", runBlocking { container.patientRepository.getPatient("b0000000-0000-4000-8000-000000000001")!!.patientNumber })
    }

    @Test
    fun `la busqueda encuentra por folio, contacto, fecha y apellido y mis pacientes filtra por asignacion`() {
        signIn(BetaAccounts.PSYCHOLOGIST_EMAIL)
        composeRule.waitForText("Hola, Mariana")
        openTab("Pacientes")
        composeRule.waitForText("Ana Lucía Cavazos Ibarra")

        listOf(
            "CRNL-000006" to "Luis Fernando Mireles Cortés",
            "8100000103" to "Fernanda Guerra Domínguez",
            "luis.mireles@example.org" to "Luis Fernando Mireles Cortés",
            "05/10/1992" to "Luis Fernando Mireles Cortés",
            "sepulveda" to "María del Carmen Sepúlveda Arriaga",
        ).forEach { (query, expected) ->
            composeRule.typeInto("Buscar paciente", query)
            composeRule.waitForText(expected)
            composeRule.waitForTextGone("Ana Lucía Cavazos Ibarra")
        }

        composeRule.typeInto("Buscar paciente", "zzzz")
        composeRule.waitForText("Sin resultados")

        composeRule.typeInto("Buscar paciente", "")
        composeRule.tap("Mis pacientes")
        composeRule.waitForText("Ana Lucía Cavazos Ibarra")
        composeRule.waitForTextGone("Diego Alejandro Treviño Salinas")
    }

    @Test
    fun `una solicitud de cuenta queda pendiente y esa cuenta no puede entrar`() {
        composeRule.waitForText("Iniciar sesión")
        composeRule.tap("Solicitar cuenta")
        composeRule.typeInto("Nombre(s)", "Camila")
        composeRule.typeInto("Apellido paterno", "Ríos")
        composeRule.typeInto("Correo electrónico", "camila.rios@example.org")
        composeRule.typeInto("Contraseña", "clave-de-prueba-1")
        composeRule.typeInto("Confirmar contraseña", "clave-de-prueba-1")
        composeRule.tap("Medicina General")
        composeRule.tap("Coordinador de área")
        composeRule.tap("Enviar solicitud")

        composeRule.waitForText("Solicitud enviada")
        assertEquals("PENDING_APPROVAL", database.openHelper.writableDatabase.query("SELECT status FROM demo_users WHERE email = 'camila.rios@example.org'").use {
            it.moveToFirst()
            it.getString(0)
        })
        assertNull(runBlocking { container.authRepository.currentUser.first() })

        composeRule.tap("Volver al acceso")
        composeRule.waitForText("Iniciar sesión")
        signIn("camila.rios@example.org", "clave-de-prueba-1")
        composeRule.waitForText("Tu solicitud de cuenta está pendiente de aprobación.")
        assertFalse(composeRule.hasTextNow("Hola, Camila"))
        assertEquals(listOf("USER_REQUESTED"), auditedActions())
    }

    @Test
    fun `un correo repetido en la solicitud se rechaza junto al campo`() {
        composeRule.tap("Solicitar cuenta")
        composeRule.typeInto("Nombre(s)", "Otra")
        composeRule.typeInto("Apellido paterno", "Persona")
        composeRule.typeInto("Correo electrónico", BetaAccounts.PSYCHOLOGIST_EMAIL)
        composeRule.typeInto("Contraseña", "clave-de-prueba-1")
        composeRule.typeInto("Confirmar contraseña", "clave-de-prueba-1")
        composeRule.tap("Psicología")
        composeRule.tap("Coordinador de área")
        composeRule.tap("Enviar solicitud")

        composeRule.waitForText("Ya existe una cuenta o solicitud con este correo")
        assertEquals(10, scalar("SELECT COUNT(*) FROM demo_users"))
    }

    @Test
    fun `cada estado de cuenta que no puede entrar muestra su motivo`() {
        composeRule.waitForText("Iniciar sesión")
        listOf(
            BetaAccounts.PENDING_EMAIL to "Tu solicitud de cuenta está pendiente de aprobación.",
            BetaAccounts.SUSPENDED_EMAIL to "Tu cuenta está suspendida. Comunícate con un administrador.",
            BetaAccounts.REJECTED_EMAIL to "Tu solicitud de cuenta fue rechazada.",
            BetaAccounts.INACTIVE_EMAIL to "Tu cuenta está inactiva. Comunícate con un administrador.",
        ).forEach { (email, message) ->
            signIn(email)
            composeRule.waitForText(message)
        }
        signIn(BetaAccounts.PSYCHOLOGIST_EMAIL, "incorrecta")
        composeRule.waitForText("Correo o contraseña incorrectos.")
        assertNull(runBlocking { container.authRepository.currentUser.first() })
    }

    @Test
    fun `administracion ve el conteo de pacientes activos y crece con cada alta`() {
        signIn(BetaAccounts.ADMIN_EMAIL)
        composeRule.waitForText("Pacientes activos")
        composeRule.waitForText("7")

        composeRule.tap("Nuevo paciente")
        fillNewPatientUpToConfirmation()
        composeRule.waitForText("Sin coincidencias")
        composeRule.tap("Continuar")
        composeRule.tap("Guardar paciente")
        composeRule.waitForText("Paciente registrado con folio CRNL-000009")

        pressBack()
        openTab("Inicio")
        composeRule.waitForText("8")
    }
}

/** Un teléfono estándar en vertical; las pruebas de pantallas pequeñas y de fuente grande se hacen en el emulador. */
private const val PHONE_SCREEN = "w411dp-h891dp"
