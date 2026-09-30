package mx.crnl.clinica.beta.feature.assessments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.EmptyState
import mx.crnl.clinica.beta.core.ui.component.InfoBanner
import mx.crnl.clinica.beta.core.ui.component.LabeledValue
import mx.crnl.clinica.beta.core.ui.component.PrimaryButton
import mx.crnl.clinica.beta.core.ui.component.SecondaryButton
import mx.crnl.clinica.beta.core.ui.component.SectionHeader
import mx.crnl.clinica.beta.core.ui.component.StatusChip
import mx.crnl.clinica.beta.core.ui.component.UiStateContent
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.model.AssessmentDetail
import mx.crnl.clinica.beta.domain.model.AssessmentInstrument
import mx.crnl.clinica.beta.domain.model.AssessmentStatus

/** Destinos que el detalle de una evaluación pide a la navegación. */
class AssessmentDetailActions(
    val onNavigateUp: () -> Unit,
    val onOpenEncounter: (encounterId: String) -> Unit,
    val onOpenSupervised: (assessmentId: String) -> Unit,
)

@Composable
fun AssessmentDetailRoute(factory: ViewModelProvider.Factory, actions: AssessmentDetailActions) {
    val viewModel: AssessmentDetailViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    AssessmentDetailScreen(state = state, actions = actions, onRetry = viewModel::retry)
}

@Composable
fun AssessmentDetailScreen(state: UiState<AssessmentDetail>, actions: AssessmentDetailActions, onRetry: () -> Unit) {
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.assessment_detail_title), onNavigateUp = actions.onNavigateUp) }) { padding ->
        UiStateContent(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.padding(padding),
            empty = {
                EmptyState(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.assessment_not_found_title),
                    message = stringResource(R.string.assessment_not_found_message),
                )
            },
        ) { detail ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Summary(detail)
                    ResultCard(detail)
                    InfoBanner(text = stringResource(R.string.assessment_beta_note))
                    if (detail.encounterId != null) {
                        SecondaryButton(
                            text = stringResource(R.string.assessment_open_encounter),
                            onClick = { actions.onOpenEncounter(detail.encounterId) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (detail.canOpenSupervisedPreview) {
                        PrimaryButton(
                            text = stringResource(R.string.assessment_open_supervised),
                            onClick = { actions.onOpenSupervised(detail.summary.assessmentId) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(detail: AssessmentDetail) {
    val summary = detail.summary
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
            Text(
                text = stringResource(summary.instrument.labelRes()),
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            StatusChip(label = stringResource(summary.status.labelRes()), tone = statusTone(summary.status), singleLine = false)
        }
        Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LabeledValue(stringResource(R.string.assessment_field_patient), detail.patientName + " · " + detail.patientNumber)
            summary.area?.let { LabeledValue(stringResource(R.string.access_field_area), stringResource(it.labelRes())) }
            if (summary.instrument != AssessmentInstrument.UNRECOGNIZED) {
                LabeledValue(stringResource(R.string.assessment_field_version), summary.instrumentVersion)
            }
            LabeledValue(stringResource(R.string.assessment_field_mode), stringResource(summary.mode.labelRes()))
            LabeledValue(stringResource(R.string.assessment_field_professional), summary.professionalName)
            LabeledValue(stringResource(R.string.assessment_field_started), DateTimeFormats.dateTime(summary.startedAt))
            LabeledValue(
                stringResource(R.string.assessment_field_completed),
                summary.completedAt?.let { DateTimeFormats.dateTime(it) } ?: stringResource(R.string.detail_no_data),
            )
        }
    }
}

@Composable
private fun ResultCard(detail: AssessmentDetail) {
    val summary = detail.summary
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.assessment_result_title))
        Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            when {
                summary.status == AssessmentStatus.CANCELLED ->
                    Text(text = stringResource(R.string.assessment_cancelled_no_result), style = MaterialTheme.typography.bodyMedium)
                summary.rawScore == null ->
                    Text(text = stringResource(R.string.assessment_no_result), style = MaterialTheme.typography.bodyMedium)
                else -> {
                    LabeledValue(stringResource(R.string.assessment_field_score), formatRecordedScore(summary.rawScore))
                    LabeledValue(stringResource(R.string.assessment_field_classification), stringResource(R.string.assessment_unclassified))
                    detail.scoringVersion?.let { LabeledValue(stringResource(R.string.assessment_field_scoring), it) }
                }
            }
        }
    }
}
