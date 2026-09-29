package mx.crnl.clinica.beta.feature.patients.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.StatusChip
import mx.crnl.clinica.beta.core.ui.component.StatusTone
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.model.DuplicateCandidate

/** Paciente ya registrado que podría ser la misma persona, con las razones de la coincidencia. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuplicateCandidateCard(candidate: DuplicateCandidate, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val patient = candidate.patient
    ClinicalCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = patient.fullName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${patient.patientNumber} · ${DateTimeFormats.birthDate(patient.birthDate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                candidate.reasons.forEach { reason ->
                    StatusChip(label = stringResource(reason.labelRes()), tone = StatusTone.Warning, singleLine = false)
                }
            }
            // Sin sangría horizontal: el texto se alinea con el resto de la tarjeta y el área táctil sigue en 48 dp.
            TextButton(onClick = onOpen, contentPadding = PaddingValues()) { Text(stringResource(R.string.duplicates_open)) }
        }
    }
}
