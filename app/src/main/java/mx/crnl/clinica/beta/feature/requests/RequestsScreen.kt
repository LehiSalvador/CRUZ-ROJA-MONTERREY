package mx.crnl.clinica.beta.feature.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.EmptyState
import mx.crnl.clinica.beta.core.ui.component.RequestCard
import mx.crnl.clinica.beta.core.ui.component.SectionHeader
import mx.crnl.clinica.beta.core.ui.component.UiStateContent
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.label.tone
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.model.AccessGrantStatus
import mx.crnl.clinica.beta.domain.model.AccessRequest
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ManagedAccount
import mx.crnl.clinica.beta.domain.model.ProfessionalChangeRequest
import mx.crnl.clinica.beta.domain.model.RequestStatus
import mx.crnl.clinica.beta.domain.request.RequestCategory

/** Destinos que la bandeja pide a la navegación. */
class RequestsActions(
    val onOpenAccount: (userId: String) -> Unit,
    val onOpenAccess: (requestId: String) -> Unit,
    val onOpenChange: (requestId: String) -> Unit,
)

@Composable
fun RequestsRoute(factory: ViewModelProvider.Factory, actions: RequestsActions) {
    val viewModel: RequestsViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selectedCategory.collectAsStateWithLifecycle()
    RequestsScreen(
        state = state,
        selectedCategory = selected,
        onSelectCategory = viewModel::selectCategory,
        actions = actions,
        onRetry = viewModel::retry,
    )
}

@Composable
fun RequestsScreen(
    state: UiState<RequestsContent>,
    selectedCategory: String,
    onSelectCategory: (RequestCategory) -> Unit,
    actions: RequestsActions,
    onRetry: () -> Unit,
) {
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.requests_title)) }) { padding ->
        UiStateContent(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.padding(padding),
            empty = { NoCategories() },
        ) { content ->
            if (content.categories.isEmpty()) {
                NoCategories()
            } else {
                val category = content.categories.firstOrNull { it.name == selectedCategory } ?: content.categories.first()
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
                        if (content.categories.size > 1) {
                            CategoryTabs(content, category, onSelectCategory)
                        }
                        CategoryList(content, category, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoCategories() {
    EmptyState(
        icon = Icons.Filled.Email,
        title = stringResource(R.string.requests_none_title),
        message = stringResource(R.string.requests_none_message),
    )
}

@Composable
private fun CategoryTabs(content: RequestsContent, selected: RequestCategory, onSelect: (RequestCategory) -> Unit) {
    PrimaryScrollableTabRow(selectedTabIndex = content.categories.indexOf(selected), edgePadding = Spacing.md) {
        content.categories.forEach { category ->
            val pending = content.pendingCount(category)
            val name = stringResource(category.labelRes())
            Tab(
                selected = category == selected,
                onClick = { onSelect(category) },
                text = { Text(text = if (pending > 0) "$name · $pending" else name, maxLines = 1) },
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun RequestCategory.labelRes(): Int = when (this) {
    RequestCategory.ACCOUNTS -> R.string.requests_category_accounts
    RequestCategory.ACCESS -> R.string.requests_category_access
    RequestCategory.CHANGES -> R.string.requests_category_changes
}

@Composable
private fun CategoryList(content: RequestsContent, category: RequestCategory, actions: RequestsActions) {
    when (category) {
        RequestCategory.ACCOUNTS -> RequestList(
            items = content.accounts,
            isPending = { it.user.status == AccountStatus.PENDING_APPROVAL },
            key = { it.user.userId },
            emptyTitle = R.string.requests_accounts_empty_title,
            emptyMessage = R.string.requests_accounts_empty_message,
        ) { account -> AccountCard(account, onClick = { actions.onOpenAccount(account.user.userId) }) }
        RequestCategory.ACCESS -> RequestList(
            items = content.access,
            isPending = { it.status == RequestStatus.PENDING },
            key = { it.requestId },
            emptyTitle = R.string.requests_access_empty_title,
            emptyMessage = R.string.requests_access_empty_message,
        ) { request -> AccessCard(request, content, onClick = { actions.onOpenAccess(request.requestId) }) }
        RequestCategory.CHANGES -> RequestList(
            items = content.changes,
            isPending = { it.status == RequestStatus.PENDING },
            key = { it.requestId },
            emptyTitle = R.string.requests_changes_empty_title,
            emptyMessage = R.string.requests_changes_empty_message,
        ) { request -> ChangeCard(request, onClick = { actions.onOpenChange(request.requestId) }) }
    }
}

@Composable
private fun <T> RequestList(
    items: List<T>,
    isPending: (T) -> Boolean,
    key: (T) -> String,
    emptyTitle: Int,
    emptyMessage: Int,
    card: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Email,
            title = stringResource(emptyTitle),
            message = stringResource(emptyMessage),
        )
        return
    }
    val pending = items.filter(isPending)
    val history = items.filterNot(isPending)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (pending.isNotEmpty()) {
            item(key = "pending-header") {
                SectionHeader(
                    title = pluralStringResource(R.plurals.requests_pending_header, pending.size, pending.size),
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
            }
            items(pending, key = key) { card(it) }
        }
        if (history.isNotEmpty()) {
            item(key = "history-header") {
                SectionHeader(
                    title = stringResource(R.string.requests_history_header),
                    modifier = Modifier.padding(top = if (pending.isEmpty()) 0.dp else Spacing.md, bottom = Spacing.xs),
                )
            }
            items(history, key = key) { card(it) }
        }
    }
}

@Composable
private fun AccountCard(account: ManagedAccount, onClick: () -> Unit) {
    val user = account.user
    RequestCard(
        title = user.fullName,
        lines = listOf(
            listOfNotNull(stringResource(user.role.labelRes()), user.area?.let { stringResource(it.labelRes()) }).joinToString(" · "),
            stringResource(R.string.requests_requested_on, DateTimeFormats.date(account.requestedAt)),
        ),
        statusLabel = stringResource(user.status.labelRes()),
        statusTone = user.status.tone(),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AccessCard(request: AccessRequest, content: RequestsContent, onClick: () -> Unit) {
    val grantStatus = request.grant?.statusAt(content.now)
    RequestCard(
        title = request.patientName,
        lines = buildList {
            add(stringResource(R.string.requests_access_line, stringResource(request.ownerArea.labelRes())))
            add(stringResource(R.string.requests_requested_by, request.requesterName, DateTimeFormats.date(request.requestedAt)))
            if (grantStatus == AccessGrantStatus.ACTIVE) {
                request.grant?.let { add(stringResource(R.string.requests_valid_until, DateTimeFormats.dateTime(it.expiresAt))) }
            }
        },
        statusLabel = stringResource(grantStatus?.labelRes() ?: request.status.labelRes()),
        statusTone = grantStatus?.tone() ?: request.status.tone(),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChangeCard(request: ProfessionalChangeRequest, onClick: () -> Unit) {
    RequestCard(
        title = request.patientName,
        lines = listOf(
            stringResource(R.string.requests_change_line, stringResource(request.area.labelRes()), request.currentProfessionalName, request.requestedProfessionalName),
            stringResource(R.string.requests_requested_by, request.requesterName, DateTimeFormats.date(request.requestedAt)),
        ),
        statusLabel = stringResource(request.status.labelRes()),
        statusTone = request.status.tone(),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}
