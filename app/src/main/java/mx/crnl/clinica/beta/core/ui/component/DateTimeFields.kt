package mx.crnl.clinica.beta.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import mx.crnl.clinica.beta.R

private const val DATE_DIGITS = 8
private const val TIME_DIGITS = 4

/**
 * Día como dd/mm/aaaa: el estado guarda solo los dígitos y la máscara los presenta. El icono abre un selector de
 * calendario; teclear sigue siendo la vía rápida.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateInputField(
    digits: String,
    onDigitsChange: (String) -> Unit,
    onPicked: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    /** Días fuera de este rango no se pueden elegir en el selector (teclear sigue validándose en el formulario). */
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    ClinicalTextField(
        value = digits,
        onValueChange = { text -> onDigitsChange(text.filter(Char::isDigit).take(DATE_DIGITS)) },
        label = label,
        modifier = modifier,
        error = error,
        enabled = enabled,
        placeholder = stringResource(R.string.field_date_hint),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        visualTransformation = DateMaskTransformation,
        trailingIcon = {
            IconButton(onClick = { picking = true }, enabled = enabled) {
                Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.action_pick_date))
            }
        },
    )
    if (picking) {
        val selectable = remember(minDate, maxDate) { RangeSelectableDates(minDate, maxDate) }
        val initial = digitsToPickerMillis(digits)?.takeIf { selectable.isSelectableDate(it) }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial, selectableDates = selectable)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onPicked(pickerMillisToDate(millis))
                        }
                        picking = false
                    },
                ) { Text(stringResource(R.string.action_accept)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Hora de 24 h como hh:mm, con la misma mecánica que [DateInputField]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeInputField(
    digits: String,
    onDigitsChange: (String) -> Unit,
    onPicked: (LocalTime) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    ClinicalTextField(
        value = digits,
        onValueChange = { text -> onDigitsChange(text.filter(Char::isDigit).take(TIME_DIGITS)) },
        label = label,
        modifier = modifier,
        error = error,
        enabled = enabled,
        placeholder = stringResource(R.string.field_time_hint),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        visualTransformation = TimeMaskTransformation,
        trailingIcon = {
            IconButton(onClick = { picking = true }, enabled = enabled) {
                Icon(ClockIcon, contentDescription = stringResource(R.string.action_pick_time))
            }
        },
    )
    if (picking) {
        val initial = digitsToTime(digits)
        val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPicked(LocalTime.of(pickerState.hour, pickerState.minute))
                        picking = false
                    },
                ) { Text(stringResource(R.string.action_accept)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Column { TimePicker(state = pickerState) }
                }
            },
        )
    }
}

/** El selector de Material entrega el día como medianoche UTC: se lee en UTC para no correr la fecha con la zona horaria. */
internal fun pickerMillisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

internal fun digitsToPickerMillis(digits: String): Long? = runCatching {
    require(digits.length == DATE_DIGITS)
    LocalDate.of(digits.substring(4).toInt(), digits.substring(2, 4).toInt(), digits.substring(0, 2).toInt())
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}.getOrNull()

private fun digitsToTime(digits: String): LocalTime = runCatching {
    require(digits.length == TIME_DIGITS)
    LocalTime.of(digits.substring(0, 2).toInt(), digits.substring(2).toInt())
}.getOrDefault(LocalTime.of(9, 0))

private object TimeMaskTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(TIME_DIGITS)
        val masked = buildString {
            digits.forEachIndexed { index, digit ->
                if (index == 2) append(':')
                append(digit)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = (if (offset <= 2) offset else offset + 1).coerceAtMost(masked.length)

            override fun transformedToOriginal(offset: Int): Int = (if (offset <= 2) offset else offset - 1).coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(masked), mapping)
    }
}

// El paquete de iconos básicos no trae un reloj: este es el trazo estándar «schedule» de Material.
private val ClockIcon: ImageVector = ImageVector.Builder(
    name = "Schedule",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = addPathNodes(
        "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z" +
            "M12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z",
    ),
    fill = SolidColor(Color.Black),
).build()

/** El selector de Material entrega medianoche UTC: los límites se comparan como días, no como instantes de la zona local. */
@OptIn(ExperimentalMaterial3Api::class)
private class RangeSelectableDates(private val min: LocalDate?, private val max: LocalDate?) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val day = pickerMillisToDate(utcTimeMillis)
        return (min == null || day >= min) && (max == null || day <= max)
    }
}
