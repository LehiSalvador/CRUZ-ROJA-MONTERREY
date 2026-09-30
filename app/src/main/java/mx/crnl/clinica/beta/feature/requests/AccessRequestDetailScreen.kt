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
import mx.crnl.clinica.beta.core.ui.component.ChipFilterRow
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
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
import mx.crnl.clinica.beta.domain.access.InterareaAccessPolicy
import mx.crnl.clinica.beta.domain.model.AccessGrantStatus
import mx.crnl.clinica.beta.domain.model.RequestStatus

@Composable
fun AccessRequestDetailRoute(
    factory: ViewModelProvider.Factory,
    onNavigateUp: () -> Unit,
    onOpenPatient: (patientId: String, areaCode: String) -> Unit,
) {
    val viewModel: AccessRequestDetailViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val review by viewModel.review.collectAsStateWithLifecycle()
    val duration by viewModel.durationDays.collectAsStateWithLifecycle()
    AccessRequestDetailScreen(
        state = state,
        review = review,
        durationDays = duration,
        onDurationSelected = viewModel::onDurationSelected,
        onApprove = viewModel::onApprove,
        onReject = viewModel::onReject,
        onRevoke = viewModel::onRevoke,
        onOpenPatient = onOpenPatient,
        onNavigateUp = onNavigateUp,
        onRetry = viewModel::retry,
    )
}

@Composable
fun AccessRequestDetailScreen(
    state: UiState<AccessRequestContent>,
    review: ReviewState,
    durationDays: Int,
    onDurationSelected: (Int) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onRevoke: () -> Unit,
    onOpenPatient: (patientId: String, areaCode: String) -> Unit,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
) {
    var confirmingRevoke by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.access_detail_title), onNavigateUp = onNavigateUp) }) { padding ->
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
                    RequestSummary(content)
                    GrantSummary(content)
                    ReviewActions(
                        content = content,
                        review = review,
                        durationDays = durationDays,
                        onDurationSelected = onDurationSelected,
                        onApprove = onApprove,
                        onReject = onReject,
                        onAskRevoke = { confirmingRevoke = true },
                    )
                    ReviewFeedback(review)
                    if (content.grantStatus == AccessGrantStatus.ACTIVE && content.isOwn) {
                        SecondaryButton(
                            text = stringResource(R.string.access_open_record),
                            onClick = { onOpenPatient(content.request.patientId, content.request.ownerArea.name) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (confirmingRevoke) {
        ConfirmActionDialog(
            title = stringResource(R.string.access_revoke_title),
            message = stringResource(R.string.access_revoke_message),
            confirmLabel = stringResource(R.string.access_revoke_confirm),
            destructive = true,
            onConfirm = {
                confirmingRevoke = false
                onRevoke()
            },
            onDismiss = { confirmingRevoke = false },
        )
    }
}

@Composable
private fun RequestSummary(content: AccessRequestContent) {
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
            LabeledValue(stringResource(R.string.access_field_area), stringResource(request.ownerArea.labelRes()))
            LabeledValue(stringResource(R.string.access_field_scope), stringResource(R.string.access_scope_read))
            LabeledValue(
                stringResource(R.string.access_field_requester),
                listOfNotNull(request.requesterName, request.requesterArea?.let { stringResource(it.labelRes()) }).joinToString(" · "),
            )
            LabeledValue(stringResource(R.string.access_field_requested_at), DateTimeFormats.dateTime(request.requestedAt))
            LabeledValue(stringResource(R.string.access_field_reason), request.reason)
            request.reviewerName?.let { reviewer ->
                LabeledValue(
                    stringResource(R.string.access_field_reviewed_by),
                    reviewer + (request.reviewedAt?.let { " · " + DateTimeFormats.dateTime(it) }.orEmpty()),
                )
            }
        }
    }
}

@Composable
private fun GrantSummary(content: AccessRequestContent) {
    val grant = content.request.grant ?: return
    val status = content.grantStatus ?: return
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(title = stringResource(R.string.access_grant_title), modifier = Modifier.weight(1f))
            StatusChip(label = stringResource(status.labelRes()), tone = status.tone())
        }
        Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LabeledValue(stringResource(R.string.access_grant_from), DateTimeFormats.dateTime(grant.validFrom))
            LabeledValue(
                stringResource(if (status == AccessGrantStatus.EXPIRED) R.string.access_grant_expired_at else R.string.access_grant_until),
                DateTimeFormats.dateTime(grant.expiresAt),
            )
            grant.revokedAt?.let { LabeledValue(stringResource(R.string.access_grant_revoked_at), DateTimeFormats.dateTime(it)) }
            Text(
                text = stringResource(R.string.access_grant_read_only_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewActions(
    content: AccessRequestContent,
    review: ReviewState,
    durationDays: Int,
    onDurationSelected: (Int) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onAskRevoke: () -> Unit,
) {
    val working = review is ReviewState.Working
    if (content.canReview) {
        ClinicalCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = stringResource(R.string.access_review_title))
            Column(modifier = Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(R.string.access_duration_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChipFilterRow(
                    options = InterareaAccessPolicy.DURATION_OPTIONS_DAYS,
                    selected = durationDays,
                    onSelect = onDurationSelected,
                    optionLabel = { days -> pluralStringResource(R.plurals.access_duration_days, days, days) },
                )
                Text(
                    text = stringResource(R.string.access_duration_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryButton(
                    text = stringResource(R.string.access_action_approve),
                    onClick = onApprove,
                    modifier = Modifier.fillMaxWidth(),
                    loading = working,
                )
                SecondaryButton(
                    text = stringResource(R.string.access_action_reject),
                    onClick = onReject,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !working,
                )
            }
        }
    } else if (content.request.status == RequestStatus.PENDING && content.isOwn) {
        InfoBanner(text = stringResource(R.string.access_pending_own_note))
    }
    if (content.canRevoke) {
        SecondaryButton(
            text = stringResource(R.string.access_action_revoke),
            onClick = onAskRevoke,
            modifier = Modifier.fillMaxWidth(),
            enabled = !working,
        )
    }
}
