package mx.crnl.clinica.beta.feature.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.StatusChip
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.label.tone
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentSummary

/** Cita de una lista: fecha, hora, estado, paciente con folio, área, profesional y modalidad. En el expediente el paciente ya está a la vista, por eso puede omitirse. */
@Composable
fun AppointmentCard(
    item: AppointmentSummary,
    modifier: Modifier = Modifier,
    showPatient: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    ClinicalCard(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.padding(bottom = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = "${DateTimeFormats.day(item.start)} · ${DateTimeFormats.timeRange(item.start, item.end)}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                StatusChip(label = stringResource(item.status.labelRes()), tone = item.status.tone())
            }
            if (showPatient) {
                Text(text = item.patientName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = item.patientNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${stringResource(item.area.labelRes())} · ${item.professionalName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val modality = stringResource(item.modality.labelRes())
            Text(
                text = if (item.modality == AppointmentModality.IN_PERSON && item.location != null) {
                    "$modality · ${item.location}"
                } else {
                    modality
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
