package mx.crnl.clinica.beta.feature.appointments.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ChoiceChipGroup
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTextField
import mx.crnl.clinica.beta.core.ui.component.DateInputField
import mx.crnl.clinica.beta.core.ui.component.TimeInputField
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.appointment.AppointmentField
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.ScheduleConflict
import mx.crnl.clinica.beta.feature.appointments.appointmentIssueText
import mx.crnl.clinica.beta.feature.appointments.messageRes
import mx.crnl.clinica.beta.feature.appointments.titleRes

class ScheduleCallbacks(
    val onDateChange: (String) -> Unit,
    val onTimeChange: (String) -> Unit,
    val onDurationChange: (Int) -> Unit,
    val onDatePicked: (LocalDate) -> Unit,
    val onTimePicked: (LocalTime) -> Unit,
)

class LogisticsCallbacks(
    val onModalityChange: (AppointmentModality) -> Unit,
    val onLocationChange: (String) -> Unit,
    val onMeetingUrlChange: (String) -> Unit,
    val onNotesChange: (String) -> Unit,
)

/** Fecha, hora y duración; el fin de la cita es el inicio más la duración elegida. */
@Composable
fun ScheduleFields(state: ScheduleFormState, callbacks: ScheduleCallbacks, enabled: Boolean = true, minDate: LocalDate? = null) {
    val issues = state.issues
    DateInputField(
        digits = state.dateDigits,
        onDigitsChange = callbacks.onDateChange,
        onPicked = callbacks.onDatePicked,
        label = stringResource(R.string.field_appointment_date),
        error = issues[AppointmentField.DATE]?.let { appointmentIssueText(AppointmentField.DATE, it) },
        enabled = enabled,
        minDate = minDate,
    )
    TimeInputField(
        digits = state.timeDigits,
        onDigitsChange = callbacks.onTimeChange,
        onPicked = callbacks.onTimePicked,
        label = stringResource(R.string.field_appointment_time),
        error = issues[AppointmentField.TIME]?.let { appointmentIssueText(AppointmentField.TIME, it) },
        enabled = enabled,
    )
    val durations = (StandardDurationMinutes + state.durationMinutes).distinct().sorted()
    ChoiceChipGroup(
        label = stringResource(R.string.field_appointment_duration),
        options = durations,
        selected = state.durationMinutes,
        onSelected = callbacks.onDurationChange,
        optionLabel = { minutes -> stringResource(R.string.duration_minutes, minutes) },
        error = issues[AppointmentField.DURATION]?.let { appointmentIssueText(AppointmentField.DURATION, it) },
    )
}

/** Modalidad, lugar o enlace y notas administrativas. No hay campo para motivos ni contenido clínico. */
@Composable
fun LogisticsFields(state: LogisticsFormState, callbacks: LogisticsCallbacks, enabled: Boolean = true) {
    val issues = state.issues
    ChoiceChipGroup(
        label = stringResource(R.string.field_appointment_modality),
        options = AppointmentModality.entries,
        selected = state.modality,
        onSelected = callbacks.onModalityChange,
        optionLabel = { stringResource(it.labelRes()) },
        error = issues[AppointmentField.MODALITY]?.let { appointmentIssueText(AppointmentField.MODALITY, it) },
    )
    if (state.modality == AppointmentModality.ONLINE) {
        ClinicalTextField(
            value = state.meetingUrl,
            onValueChange = callbacks.onMeetingUrlChange,
            label = stringResource(R.string.field_appointment_link),
            error = issues[AppointmentField.MEETING_URL]?.let { appointmentIssueText(AppointmentField.MEETING_URL, it) },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )
    } else {
        ClinicalTextField(
            value = state.location,
            onValueChange = callbacks.onLocationChange,
            label = stringResource(R.string.field_appointment_location),
            error = issues[AppointmentField.LOCATION]?.let { appointmentIssueText(AppointmentField.LOCATION, it) },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
    }
    ClinicalTextField(
        value = state.notes,
        onValueChange = callbacks.onNotesChange,
        label = stringResource(R.string.field_appointment_notes),
        helper = stringResource(R.string.field_appointment_notes_helper),
        error = issues[AppointmentField.NOTES]?.let { appointmentIssueText(AppointmentField.NOTES, it) },
        enabled = enabled,
        singleLine = false,
        minLines = 2,
    )
}

/** Con quién choca el horario elegido; si esa cita es visible se puede abrir para revisarla. */
@Composable
fun ConflictsCard(conflicts: List<ScheduleConflict>, onOpenAppointment: ((String) -> Unit)?) {
    if (conflicts.isEmpty()) return
    ClinicalCard(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = stringResource(R.string.conflict_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            conflicts.forEach { conflict ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(text = stringResource(conflict.kind.titleRes()), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = listOfNotNull(
                            "${DateTimeFormats.day(conflict.start)} · ${DateTimeFormats.timeRange(conflict.start, conflict.end)}",
                            conflict.patientName,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val id = conflict.appointmentId
                    if (id != null && onOpenAppointment != null) {
                        TextButton(onClick = { onOpenAppointment(id) }) { Text(stringResource(R.string.conflict_open)) }
                    }
                }
            }
        }
    }
}

/** Un rechazo de guardado o un fallo inesperado, en lenguaje llano y junto al botón que lo provocó. */
@Composable
fun WriteErrorText(write: WriteState, modifier: Modifier = Modifier) {
    val message = when (write) {
        is WriteState.Failed -> stringResource(write.error.messageRes())
        is WriteState.Unavailable -> stringResource(R.string.error_save_failed)
        else -> return
    }
    Row(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}
