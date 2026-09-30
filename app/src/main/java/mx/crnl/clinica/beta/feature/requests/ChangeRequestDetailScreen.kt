package mx.crnl.clinica.beta.feature.requests

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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTextField
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.ConfirmActionDialog
import mx.crnl.clinica.beta.core.ui.component.EmptyState
import mx.crnl.clinica.beta.core.ui.component.InfoBanner
import mx.crnl.clinica.beta.core.ui.component.LabeledValue
import mx.crnl.clinica.beta.core.ui.component.PrimaryButton
import mx.crnl.clinica.beta.core.ui.component.SecondaryButton
import mx.crnl.clinica.beta.core.ui.component.SectionHeader
import mx.crnl.clinica.beta.core.ui.component.StatusChip
import mx.crnl.clinica.beta.core.ui.component.UiStateContent
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.label.tone
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.model.RequestStatus

@Composable
fun ChangeRequestDetailRoute(
    factory: ViewModelProvider.Factory,
    onNavigateUp: () -> Unit,
    onOpenPatient: (patientId: String, areaCode: String) -> Unit,
) {
    val viewModel: ChangeRequestDetailViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val review by viewModel.review.collectAsStateWithLifecycle()
    val rejectReason by viewModel.rejectReason.collectAsStateWithLifecycle()
    ChangeRequestDetailScreen(
        state = state,
        review = review,
        rejectReason = rejectReason,
        onRejectReasonChange = viewModel::onRejectReasonChange,
        onApprove = viewModel::onApprove,
        onReject = viewModel::onReject,
        onOpenPatient = onOpenPatient,
        onNavigateUp = onNavigateUp,
        onRetry = viewModel::retry,
    )
}

@Composable
fun ChangeRequestDetailScreen(
    state: UiState<ChangeRequestContent>,
    review: ReviewState,
    rejectReason: String,
    onRejectReasonChange: (String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onOpenPatient: (patientId: String, areaCode: String) -> Unit,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
) {
    var confirmingApproval by rememberSaveable { mutableStateOf(false) }
    var rejecting by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.change_detail_title), onNavigateUp = onNavigateUp) }) { padding ->
        UiStateContent(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.padding(padding),
            empty = {
                EmptyState(
                    icon = Icons.Filled.Email,
                    title = stringResource(R.string.request_not_found_title),
                    message = stringResource(R.string.request_not_found_message),
                )
            },
        ) { content ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    ChangeSummary(content)
                    val request = content.request
                    if (request.status == RequestStatus.PENDING && request.upcomingAppointmentCount > 0) {
                        InfoBanner(
                            title = pluralStringResource(R.plurals.change_upcoming_title, request.upcomingAppointmentCount, request.upcomingAppointmentCount),
                            text = stringResource(R.string.change_upcoming_message),
                            warning = true,
                        )
                    }
                    if (content.canReview) {
                        ReviewControls(
                            review = review,
                            rejecting = rejecting,
                            rejectReason = rejectReason,
                            onRejectReasonChange = onRejectReasonChange,
                            onAskApproval = { confirmingApproval = true },
                            onStartReject = { rejecting = true },
                            onCancelReject = { rejecting = false },
                            onConfirmReject = onReject,
                        )
                    } else if (request.status == RequestStatus.PENDING && content.isOwn) {
                        InfoBanner(text = stringResource(R.string.change_pending_own_note))
                    }
                    ReviewFeedback(review)
                    if (request.status == RequestStatus.APPROVED) {
                        SecondaryButton(
                            text = stringResource(R.string.change_open_record),
                            onClick = { onOpenPatient(request.patientId, request.area.name) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (confirmingApproval) {
        val request = (state as? UiState.Content)?.data?.request
        ConfirmActionDialog(
            title = stringResource(R.string.change_confirm_title),
            message = if (request == null) {
                ""
            } else {
                stringResource(R.string.change_confirm_message, request.currentProfessionalName, request.requestedProfessionalName)
            },
            confirmLabel = stringResource(R.string.change_confirm_action),
            onConfirm = {
                confirmingApproval = false
                onApprove()
            },
            onDismiss = { confirmingApproval = false },
        )
    }
}

@Composable
private fun ChangeSummary(content: ChangeRequestContent) {
    val request = content.request
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
            Text(
                text = request.patientName,
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            StatusChip(label = stringResource(request.status.labelRes()), tone = request.status.tone(), singleLine = false)
        }
        Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LabeledValue(stringResource(R.string.access_field_patient_number), request.patientNumber)
            LabeledValue(stringResource(R.string.access_field_area), stringResource(request.area.labelRes()))
            LabeledValue(stringResource(R.string.change_field_current), request.currentProfessionalName)
            LabeledValue(stringResource(R.string.change_field_requested), request.requestedProfessionalName)
            LabeledValue(stringResource(R.string.access_field_requester), request.requesterName)
            LabeledValue(stringResource(R.string.access_field_requested_at), DateTimeFormats.dateTime(request.requestedAt))
            LabeledValue(stringResource(R.string.access_field_reason), request.reason)
            request.reviewerName?.let { reviewer ->
                LabeledValue(
                    stringResource(R.string.access_field_reviewed_by),
                    reviewer + (request.reviewedAt?.let { " · " + DateTimeFormats.dateTime(it) }.orEmpty()),
                )
            }
            request.resolutionReason?.let { LabeledValue(stringResource(R.string.change_field_resolution), it) }
        }
    }
}

@Composable
private fun ReviewControls(
    review: ReviewState,
    rejecting: Boolean,
    rejectReason: String,
    onRejectReasonChange: (String) -> Unit,
    onAskApproval: () -> Unit,
    onStartReject: () -> Unit,
    onCancelReject: () -> Unit,
    onConfirmReject: () -> Unit,
) {
    val working = review is ReviewState.Working
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.change_review_title))
        Column(modifier = Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (!rejecting) {
                Text(
                    text = stringResource(R.string.change_review_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryButton(
                    text = stringResource(R.string.change_action_approve),
                    onClick = onAskApproval,
                    modifier = Modifier.fillMaxWidth(),
                    loading = working,
                )
                SecondaryButton(
                    text = stringResource(R.string.change_action_reject),
                    onClick = onStartReject,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !working,
                )
            } else {
                // La razón se pide en la propia pantalla, no en un diálogo: un campo de texto dentro de un diálogo se pierde con el teclado.
                ClinicalTextField(
                    value = rejectReason,
                    onValueChange = onRejectReasonChange,
                    label = stringResource(R.string.change_reject_reason),
                    helper = stringResource(R.string.change_reject_reason_helper),
                    singleLine = false,
                    minLines = 2,
                )
                PrimaryButton(
                    text = stringResource(R.string.change_reject_confirm),
                    onClick = onConfirmReject,
                    modifier = Modifier.fillMaxWidth(),
                    loading = working,
                )
                SecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onCancelReject,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !working,
                )
            }
        }
    }
}
