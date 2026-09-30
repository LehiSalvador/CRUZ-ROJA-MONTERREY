package mx.crnl.clinica.beta.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ChipFilterRow
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.EmptyState
import mx.crnl.clinica.beta.core.ui.component.NotAuthorizedState
import mx.crnl.clinica.beta.core.ui.component.StatusChip
import mx.crnl.clinica.beta.core.ui.component.StatusTone
import mx.crnl.clinica.beta.core.ui.component.UiStateContent
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.model.AuditCategory
import mx.crnl.clinica.beta.domain.model.AuditPeriod
import mx.crnl.clinica.beta.domain.model.AuditRecord

@Composable
fun AuditRoute(factory: ViewModelProvider.Factory, onNavigateUp: () -> Unit) {
    val viewModel: AuditViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuditScreen(
        state = state,
        onCategoryChange = viewModel::selectCategory,
        onPeriodChange = viewModel::selectPeriod,
        onNavigateUp = onNavigateUp,
        onRetry = viewModel::retry,
    )
}

@Composable
fun AuditScreen(
    state: UiState<AuditContent>,
    onCategoryChange: (AuditCategory?) -> Unit,
    onPeriodChange: (AuditPeriod) -> Unit,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.audit_title), onNavigateUp = onNavigateUp) }) { padding ->
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
                    Entries(content, onCategoryChange, onPeriodChange)
                }
            }
        }
    }
}

@Composable
private fun Filters(content: AuditContent, onCategoryChange: (AuditCategory?) -> Unit, onPeriodChange: (AuditPeriod) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        ChipFilterRow(
            options = listOf<AuditCategory?>(null) + AuditCategory.entries,
            selected = content.filter.category,
            onSelect = onCategoryChange,
            optionLabel = { category -> stringResource(category?.labelRes() ?: R.string.audit_category_all) },
        )
        ChipFilterRow(
            options = AuditPeriod.entries,
            selected = content.filter.period,
            onSelect = onPeriodChange,
            optionLabel = { period -> stringResource(period.labelRes()) },
        )
    }
}

// Los filtros van dentro de la lista: con fuentes grandes ocuparían media pantalla fija y taparían las entradas.
@Composable
private fun Entries(content: AuditContent, onCategoryChange: (AuditCategory?) -> Unit, onPeriodChange: (AuditPeriod) -> Unit) {
    LazyColumn(
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "filters") { Filters(content, onCategoryChange, onPeriodChange) }
        if (content.entries.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.audit_empty_title) + ". " + stringResource(R.string.audit_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(content.entries, key = { it.auditId }) { entry -> AuditRow(entry) }
    }
}

@Composable
private fun AuditRow(entry: AuditRecord) {
    ClinicalCard(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
            Text(
                text = stringResource(entry.action.labelRes()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            if (!entry.success) StatusChip(label = stringResource(R.string.audit_result_failed), tone = StatusTone.Danger, singleLine = false)
        }
        Text(
            text = DateTimeFormats.dateTime(entry.occurredAt),
            modifier = Modifier.padding(top = Spacing.xs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = listOfNotNull(
                entry.actorName ?: stringResource(R.string.audit_actor_system),
                stringResource(entityLabelRes(entry.entityType)),
                entry.area?.let { stringResource(it.labelRes()) },
            ).joinToString(" · "),
            modifier = Modifier.padding(top = Spacing.xs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun entityLabelRes(entityType: String): Int = when (entityType) {
    "USER" -> R.string.audit_entity_user
    "PATIENT" -> R.string.audit_entity_patient
    "APPOINTMENT" -> R.string.audit_entity_appointment
    "ASSIGNMENT" -> R.string.audit_entity_assignment
    "ENCOUNTER" -> R.string.audit_entity_encounter
    "ACCESS_REQUEST" -> R.string.audit_entity_access_request
    "ACCESS_GRANT" -> R.string.audit_entity_access_grant
    "OVERRIDE_REQUEST" -> R.string.audit_entity_change_request
    "ASSESSMENT" -> R.string.audit_entity_assessment
    "BETA_DATA", "DEMO_SEED" -> R.string.audit_entity_beta_data
    else -> R.string.audit_entity_other
}
