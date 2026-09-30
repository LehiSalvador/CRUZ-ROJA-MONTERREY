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
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.ConfirmActionDialog
import mx.crnl.clinica.beta.core.ui.component.EmptyState
import mx.crnl.clinica.beta.core.ui.component.LabeledValue
import mx.crnl.clinica.beta.core.ui.component.PrimaryButton
import mx.crnl.clinica.beta.core.ui.component.SecondaryButton
import mx.crnl.clinica.beta.core.ui.component.StatusChip
import mx.crnl.clinica.beta.core.ui.component.UiStateContent
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.label.tone
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.access.AccountAction

@Composable
fun AccountDetailRoute(factory: ViewModelProvider.Factory, onNavigateUp: () -> Unit) {
    val viewModel: AccountDetailViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val review by viewModel.review.collectAsStateWithLifecycle()
    AccountDetailScreen(
        state = state,
        review = review,
        onApply = viewModel::onApply,
        onNavigateUp = onNavigateUp,
        onRetry = viewModel::retry,
    )
}

@Composable
fun AccountDetailScreen(
    state: UiState<AccountDetailContent>,
    review: ReviewState,
    onApply: (AccountAction) -> Unit,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.account_detail_title), onNavigateUp = onNavigateUp) }) { padding ->
        // Tras resolver, la cuenta puede dejar de ser visible para quien la resolvió (p. ej. coordinación); se confirma igual.
        if (review is ReviewState.Done && state !is UiState.Content) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    ReviewFeedback(review)
                    SecondaryButton(text = stringResource(R.string.action_go_back), onClick = onNavigateUp, modifier = Modifier.fillMaxWidth())
                }
            }
            return@Scaffold
        }
        UiStateContent(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.padding(padding),
            empty = {
                EmptyState(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.account_not_found_title),
                    message = stringResource(R.string.account_not_found_message),
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
                    AccountCard(content)
                    AccountActions(content, review, onApply = onApply, onAskConfirmation = { confirming = it.name })
                    ReviewFeedback(review)
                }
            }
        }
    }

    val pending = confirming?.let { name -> AccountAction.entries.firstOrNull { it.name == name } }
    if (pending != null) {
        ConfirmActionDialog(
            title = stringResource(pending.confirmTitleRes()),
            message = stringResource(pending.confirmMessageRes()),
            confirmLabel = stringResource(pending.labelRes()),
            destructive = true,
            onConfirm = {
                confirming = null
                onApply(pending)
            },
            onDismiss = { confirming = null },
        )
    }
}

private fun AccountAction.confirmTitleRes(): Int = when (this) {
    AccountAction.REJECT -> R.string.account_confirm_reject_title
    AccountAction.SUSPEND -> R.string.account_confirm_suspend_title
    else -> R.string.account_confirm_generic_title
}

private fun AccountAction.confirmMessageRes(): Int = when (this) {
    AccountAction.REJECT -> R.string.account_confirm_reject_message
    AccountAction.SUSPEND -> R.string.account_confirm_suspend_message
    else -> R.string.account_confirm_generic_message
}

@Composable
private fun AccountCard(content: AccountDetailContent) {
    val user = content.user
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
            Text(
                text = user.fullName,
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            StatusChip(label = stringResource(user.status.labelRes()), tone = user.status.tone(), singleLine = false)
        }
        Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LabeledValue(stringResource(R.string.profile_email), user.email)
            LabeledValue(stringResource(R.string.account_requested_role), stringResource(user.role.labelRes()))
            LabeledValue(
                stringResource(R.string.profile_area),
                user.area?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.profile_area_none),
            )
            user.professionalLicense?.let { LabeledValue(stringResource(R.string.profile_license), it) }
            LabeledValue(stringResource(R.string.account_requested_on), DateTimeFormats.dateTime(content.requestedAt))
        }
    }
}

@Composable
private fun AccountActions(
    content: AccountDetailContent,
    review: ReviewState,
    onApply: (AccountAction) -> Unit,
    onAskConfirmation: (AccountAction) -> Unit,
) {
    val working = review is ReviewState.Working
    val done = review is ReviewState.Done
    if (content.actions.isEmpty()) {
        if (!done) {
            Text(
                text = stringResource(R.string.account_read_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (AccountAction.APPROVE in content.actions) {
            Text(
                text = stringResource(R.string.account_approve_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                text = stringResource(R.string.account_action_approve_full),
                onClick = { onApply(AccountAction.APPROVE) },
                modifier = Modifier.fillMaxWidth(),
                loading = working,
            )
        }
        if (AccountAction.REJECT in content.actions) {
            SecondaryButton(
                text = stringResource(R.string.account_action_reject_full),
                onClick = { onAskConfirmation(AccountAction.REJECT) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !working,
            )
        }
        if (AccountAction.REACTIVATE in content.actions) {
            PrimaryButton(
                text = stringResource(R.string.account_action_reactivate_full),
                onClick = { onApply(AccountAction.REACTIVATE) },
                modifier = Modifier.fillMaxWidth(),
                loading = working,
            )
        }
        if (AccountAction.SUSPEND in content.actions) {
            SecondaryButton(
                text = stringResource(R.string.account_action_suspend_full),
                onClick = { onAskConfirmation(AccountAction.SUSPEND) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !working,
            )
        }
    }
}
