package mx.crnl.clinica.beta.feature.assessments

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
import java.text.NumberFormat
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.SectionHeader
import mx.crnl.clinica.beta.core.ui.component.StatusChip
import mx.crnl.clinica.beta.core.ui.component.StatusTone
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.ClinicTime
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.clinical.AssessmentTrend
import mx.crnl.clinica.beta.domain.model.AssessmentStatus
import mx.crnl.clinica.beta.domain.model.AssessmentSummary

/** El valor ficticio ya registrado, sin decimales innecesarios. No se calcula ni se interpreta. */
fun formatRecordedScore(score: Double): String =
    NumberFormat.getNumberInstance(ClinicTime.locale).apply { maximumFractionDigits = 2 }.format(score)

/** Estado neutro del resultado de una aplicación, en una línea. Nunca incluye una clasificación. */
@Composable
fun assessmentResultLine(assessment: AssessmentSummary): String = when {
    assessment.status == AssessmentStatus.CANCELLED -> stringResource(R.string.assessment_cancelled_no_result)
    assessment.rawScore != null ->
        stringResource(R.string.assessment_score_recorded, formatRecordedScore(assessment.rawScore)) + " · " +
            stringResource(R.string.assessment_unclassified)
    else -> stringResource(R.string.assessment_no_result)
}

/** Historial de evaluaciones de un área, de solo lectura; cada aplicación abre su detalle. */
@Composable
fun AssessmentsSection(assessments: List<AssessmentSummary>, onOpen: (assessmentId: String) -> Unit) {
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.assessments_section))
        Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (assessments.isEmpty()) {
                Text(text = stringResource(R.string.assessments_empty), style = MaterialTheme.typography.bodyMedium)
            }
            assessments.forEach { assessment -> AssessmentRow(assessment, onClick = { onOpen(assessment.assessmentId) }) }
        }
    }
    val series = AssessmentTrend.series(assessments)
    if (series.isNotEmpty()) {
        ClinicalCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = stringResource(R.string.assessments_trend_title))
            Text(
                text = stringResource(R.string.assessments_trend_note),
                modifier = Modifier.padding(top = Spacing.xs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            series.forEach { (key, points) ->
                Column(modifier = Modifier.padding(top = Spacing.md).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = stringResource(key.first.labelRes()) + " · " + stringResource(R.string.assessment_version, key.second),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    points.forEach { point ->
                        Text(
                            text = DateTimeFormats.date(point.at) + " — " + formatRecordedScore(point.rawScore),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssessmentRow(assessment: AssessmentSummary, onClick: () -> Unit) {
    ClinicalCard(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
            Text(
                text = stringResource(assessment.instrument.labelRes()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            StatusChip(label = stringResource(assessment.status.labelRes()), tone = statusTone(assessment.status), singleLine = false)
        }
        Text(
            text = DateTimeFormats.date(assessment.completedAt ?: assessment.startedAt) + " · " +
                stringResource(assessment.mode.labelRes()),
            modifier = Modifier.padding(top = Spacing.xs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = assessment.professionalName + " · " + assessmentResultLine(assessment),
            modifier = Modifier.padding(top = Spacing.xs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun statusTone(status: AssessmentStatus): StatusTone = when (status) {
    AssessmentStatus.COMPLETED -> StatusTone.Success
    AssessmentStatus.STARTED -> StatusTone.Info
    AssessmentStatus.CANCELLED, AssessmentStatus.INVALIDATED -> StatusTone.Neutral
}
