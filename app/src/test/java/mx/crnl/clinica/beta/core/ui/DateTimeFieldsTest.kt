package mx.crnl.clinica.beta.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.LocalTime
import mx.crnl.clinica.beta.core.ui.component.DateInputField
import mx.crnl.clinica.beta.core.ui.component.digitsToPickerMillis
import mx.crnl.clinica.beta.core.ui.component.pickerMillisToDate
import mx.crnl.clinica.beta.core.ui.component.TimeInputField
import mx.crnl.clinica.beta.core.ui.theme.ClinicalTheme
import mx.crnl.clinica.beta.testing.tap
import mx.crnl.clinica.beta.testing.typeInto
import mx.crnl.clinica.beta.testing.waitForText
import mx.crnl.clinica.beta.testing.waitForTextGone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class DateTimeFieldsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var dateDigits by mutableStateOf("")
    private var timeDigits by mutableStateOf("")
    private var pickedDate: LocalDate? = null
    private var pickedTime: LocalTime? = null

    private fun show() {
        composeRule.setContent {
            ClinicalTheme {
                DateInputField(
                    digits = dateDigits,
                    onDigitsChange = { dateDigits = it },
                    onPicked = { pickedDate = it },
                    label = "Fecha",
                )
                TimeInputField(
                    digits = timeDigits,
                    onDigitsChange = { timeDigits = it },
                    onPicked = { pickedTime = it },
                    label = "Hora",
                )
            }
        }
    }

    @Test
    fun `teclear guarda solo digitos y la mascara los presenta como fecha y hora`() {
        show()

        composeRule.typeInto("Fecha", "29/09/2026abc")
        composeRule.typeInto("Hora", "16:45")

        assertEquals("29092026", dateDigits)
        assertEquals("1645", timeDigits)
        composeRule.onNodeWithText("29/09/2026").assertExists()
        composeRule.onNodeWithText("16:45").assertExists()
    }

    @Test
    fun `nunca se aceptan mas digitos de los que caben`() {
        show()

        composeRule.typeInto("Fecha", "2909202699999")
        composeRule.typeInto("Hora", "164599")

        assertEquals("29092026", dateDigits)
        assertEquals("1645", timeDigits)
    }

    @Test
    fun `el selector de fecha se abre desde el icono y se cierra al aceptar`() {
        dateDigits = "29092026"
        show()

        composeRule.onNodeWithContentDescription("Elegir fecha").performClick()
        composeRule.waitForText("Aceptar")
        composeRule.tap("Aceptar")
        composeRule.waitForTextGone("Aceptar")
    }

    @Test
    fun `el dia del selector se convierte sin corrimiento de zona horaria`() {
        // El selector trabaja con medianoche UTC; en Monterrey (UTC-6) esa hora cae el día anterior si se usa la zona local.
        val millis = requireNotNull(digitsToPickerMillis("29092026"))

        assertEquals(LocalDate.of(2026, 9, 29), pickerMillisToDate(millis))
        assertEquals(LocalDate.of(2026, 1, 1), pickerMillisToDate(requireNotNull(digitsToPickerMillis("01012026"))))
        assertNull(digitsToPickerMillis("3102"))
        assertNull(digitsToPickerMillis("31022026"))
        assertNull(digitsToPickerMillis(""))
    }

    @Test
    fun `cancelar el selector no cambia nada`() {
        show()

        composeRule.onNodeWithContentDescription("Elegir fecha").performClick()
        composeRule.waitForText("Cancelar")
        composeRule.tap("Cancelar")
        composeRule.onNodeWithContentDescription("Elegir hora").performClick()
        composeRule.waitForText("Cancelar")
        composeRule.tap("Cancelar")

        assertNull(pickedDate)
        assertNull(pickedTime)
        assertEquals("", dateDigits)
    }

    @Test
    fun `el selector de hora parte de la hora escrita, o de las nueve si esta vacia`() {
        timeDigits = "1645"
        show()
        composeRule.onNodeWithContentDescription("Elegir hora").performClick()
        composeRule.waitForText("Aceptar")
        composeRule.tap("Aceptar")
        assertEquals(LocalTime.of(16, 45), pickedTime)
    }

    @Test
    fun `sin hora escrita el selector propone las nueve`() {
        show()
        composeRule.onNodeWithContentDescription("Elegir hora").performClick()
        composeRule.waitForText("Aceptar")
        composeRule.tap("Aceptar")

        assertEquals(LocalTime.of(9, 0), pickedTime)
    }
}
