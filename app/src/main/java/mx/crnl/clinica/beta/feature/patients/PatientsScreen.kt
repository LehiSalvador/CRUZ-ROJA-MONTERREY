package mx.crnl.clinica.beta.feature.patients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalFilterChip
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.EmptyState
import mx.crnl.clinica.beta.core.ui.component.StatusChip
import mx.crnl.clinica.beta.core.ui.component.UiStateContent
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.label.tone
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.ui.theme.isCompactLandscape

@Composable
fun PatientsRoute(
    factory: ViewModelProvider.Factory,
    onOpenPatient: (String) -> Unit,
    onNewPatient: () -> Unit,
) {
    val viewModel: PatientsViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val scope by viewModel.scope.collectAsStateWithLifecycle()
    val canFilterByAssignment by viewModel.canFilterByAssignment.collectAsStateWithLifecycle()
    PatientsScreen(
        state = state,
        query = query,
        scope = scope,
        canFilterByAssignment = canFilterByAssignment,
        onQueryChange = viewModel::onQueryChange,
        onScopeChange = viewModel::onScopeChange,
        onOpenPatient = onOpenPatient,
        onNewPatient = onNewPatient,
        onRetry = viewModel::retry,
    )
}

@Composable
fun PatientsScreen(
    state: UiState<PatientListContent>,
    query: String,
    scope: PatientsScope,
    canFilterByAssignment: Boolean,
    onQueryChange: (String) -> Unit,
    onScopeChange: (PatientsScope) -> Unit,
    onOpenPatient: (String) -> Unit,
    onNewPatient: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.patients_title)) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (canFilterByAssignment && isCompactLandscape()) {
                    // Con poco alto, búsqueda y filtro comparten renglón para dejar más filas del listado a la vista.
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        SearchField(query = query, onQueryChange = onQueryChange, modifier = Modifier.weight(1f))
                        ScopeFilter(scope = scope, onScopeChange = onScopeChange)
                    }
                } else {
                    SearchField(query = query, onQueryChange = onQueryChange)
                    if (canFilterByAssignment) {
                        ScopeFilter(scope = scope, onScopeChange = onScopeChange)
                    }
                }
            }
            // El botón flotante vive dentro de la columna de contenido, no en el borde de la pantalla: así queda
            // alineado con las tarjetas en tabletas y en horizontal, junto a los márgenes de sistema y al riel.
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
                    UiStateContent(
                        state = state,
                        onRetry = onRetry,
                        empty = { NoPatients(query = query, scope = scope) },
                    ) { content ->
                        // Cada búsqueda o filtro tiene su propia posición: una nueva empieza por arriba, y volver desde un
                        // expediente conserva la del listado que se dejó. La clave son los criterios de los datos que llegaron,
                        // no los del campo de texto, para no arrastrar la posición de la lista anterior mientras se actualiza.
                        val listState = key(content.query, content.scope) { rememberLazyListState() }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = LIST_END_PADDING),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            items(content.patients, key = { it.patientId }) { patient ->
                                PatientRow(patient, onClick = { onOpenPatient(patient.patientId) })
                            }
                        }
                    }
                    // Igual que el botón principal de Inicio.
                    ExtendedFloatingActionButton(
                        onClick = onNewPatient,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.md),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(stringResource(R.string.patients_new))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.patients_search_label)) },
        placeholder = { Text(stringResource(R.string.patients_search_placeholder)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.patients_search_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun ScopeFilter(scope: PatientsScope, onScopeChange: (PatientsScope) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ClinicalFilterChip(
            selected = scope == PatientsScope.ALL,
            onClick = { onScopeChange(PatientsScope.ALL) },
            label = stringResource(R.string.patients_filter_all),
        )
        ClinicalFilterChip(
            selected = scope == PatientsScope.MINE,
            onClick = { onScopeChange(PatientsScope.MINE) },
            label = stringResource(R.string.patients_filter_mine),
        )
    }
}

@Composable
private fun NoPatients(query: String, scope: PatientsScope) {
    val trimmed = query.trim()
    when {
        trimmed.isNotEmpty() -> EmptyState(
            icon = Icons.Filled.Search,
            title = stringResource(R.string.patients_no_results_title),
            message = stringResource(R.string.patients_no_results_message, trimmed),
        )
        scope == PatientsScope.MINE -> EmptyState(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.patients_no_mine_title),
            message = stringResource(R.string.patients_no_mine_message),
        )
        else -> EmptyState(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.patients_empty_title),
            message = stringResource(R.string.patients_empty_message),
        )
    }
}

@Composable
private fun PatientRow(item: PatientListItem, onClick: () -> Unit) {
    ClinicalCard(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = item.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                StatusChip(label = stringResource(item.status.labelRes()), tone = item.status.tone())
            }
            Text(
                text = "${item.patientNumber} · " +
                    pluralStringResource(R.plurals.patient_age_years, item.ageYears, item.ageYears) +
                    " · " + stringResource(item.sex.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (item.areas.isEmpty()) {
                    stringResource(R.string.patients_no_assignment)
                } else {
                    item.areas.map { stringResource(it.labelRes()) }.joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val LIST_END_PADDING = 96.dp
