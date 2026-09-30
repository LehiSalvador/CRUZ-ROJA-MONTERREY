package mx.crnl.clinica.beta.feature.admin

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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ChipFilterRow
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.EmptyState
import mx.crnl.clinica.beta.core.ui.component.NotAuthorizedState
import mx.crnl.clinica.beta.core.ui.component.RequestCard
import mx.crnl.clinica.beta.core.ui.component.UiStateContent
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.label.tone
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.domain.model.ManagedAccount

@Composable
fun UserDirectoryRoute(factory: ViewModelProvider.Factory, onNavigateUp: () -> Unit, onOpenUser: (userId: String) -> Unit) {
    val viewModel: UserDirectoryViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    UserDirectoryScreen(
        state = state,
        onFilterChange = viewModel::selectFilter,
        onOpenUser = onOpenUser,
        onNavigateUp = onNavigateUp,
        onRetry = viewModel::retry,
    )
}

@Composable
fun UserDirectoryScreen(
    state: UiState<UserDirectoryContent>,
    onFilterChange: (UserFilter) -> Unit,
    onOpenUser: (userId: String) -> Unit,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.users_title), onNavigateUp = onNavigateUp) }) { padding ->
        UiStateContent(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.padding(padding),
            empty = { NotAuthorizedState(onBack = onNavigateUp) },
        ) { content ->
            if (!content.allowed) {
                NotAuthorizedState(onBack = onNavigateUp)
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    UserList(content, onFilterChange, onOpenUser)
                }
            }
        }
    }
}

private fun UserFilter.labelRes(): Int = when (this) {
    UserFilter.PENDING -> R.string.users_filter_pending
    UserFilter.ACTIVE -> R.string.users_filter_active
    UserFilter.SUSPENDED_OR_REJECTED -> R.string.users_filter_suspended
    UserFilter.INACTIVE -> R.string.users_filter_inactive
}

// Los filtros van dentro de la lista: con fuentes grandes ocuparían media pantalla fija y taparían las cuentas.
@Composable
private fun UserList(content: UserDirectoryContent, onFilterChange: (UserFilter) -> Unit, onOpenUser: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "filters") {
            ChipFilterRow(
                options = UserFilter.entries,
                selected = content.filter,
                onSelect = onFilterChange,
                optionLabel = { filter -> stringResource(filter.labelRes(), content.counts[filter] ?: 0) },
                modifier = Modifier.padding(vertical = Spacing.sm),
            )
        }
        if (content.users.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.users_empty_title) + ". " + stringResource(R.string.users_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(content.users, key = { it.user.userId }) { account -> UserCard(account, onClick = { onOpenUser(account.user.userId) }) }
    }
}

@Composable
private fun UserCard(account: ManagedAccount, onClick: () -> Unit) {
    val user = account.user
    RequestCard(
        title = user.fullName,
        lines = listOfNotNull(
            user.email,
            listOfNotNull(stringResource(user.role.labelRes()), user.area?.let { stringResource(it.labelRes()) }).joinToString(" · "),
            user.professionalLicense?.let { stringResource(R.string.assign_license, it) },
        ),
        statusLabel = stringResource(user.status.labelRes()),
        statusTone = user.status.tone(),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}
