package mx.crnl.clinica.beta.feature.patients.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.EmptyState
import mx.crnl.clinica.beta.core.ui.component.LabeledValue
import mx.crnl.clinica.beta.core.ui.component.SectionHeader
import mx.crnl.clinica.beta.core.ui.component.StatusChip
import mx.crnl.clinica.beta.core.ui.component.UiStateContent
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.label.tone
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.model.AssessmentSummary
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ContactType
import mx.crnl.clinica.beta.domain.model.EncounterSummary
import mx.crnl.clinica.beta.domain.model.PatientDetail
import mx.crnl.clinica.beta.feature.appointments.AppointmentCard

/** Secciones del expediente. Solo «Resumen» reúne todo; las demás muestran de solo lectura lo ya registrado. */
private enum class DetailTab(@StringRes val labelRes: Int, val area: ClinicalArea? = null) {
    SUMMARY(R.string.detail_tab_summary),
    PSYCHOLOGY(R.string.area_psychology, ClinicalArea.PSYCHOLOGY),
    NUTRITION(R.string.area_nutrition, ClinicalArea.NUTRITION),
    MEDICINE(R.string.area_general_medicine, ClinicalArea.GENERAL_MEDICINE),
    APPOINTMENTS(R.string.detail_tab_appointments),
    DOCUMENTS(R.string.detail_tab_documents),
}

private const val SUMMARY_ITEM_LIMIT = 3

@Composable
fun PatientDetailRoute(
    factory: ViewModelProvider.Factory,
    onNavigateUp: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val viewModel: PatientDetailViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    PatientDetailScreen(state = state, onNavigateUp = onNavigateUp, onEdit = onEdit, onRetry = viewModel::retry)
}

@Composable
fun PatientDetailScreen(
    state: UiState<PatientDetailContent>,
    onNavigateUp: () -> Unit,
    onEdit: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    // Al desplazarse el encabezado fuera de pantalla, la barra sigue diciendo de quién es el expediente.
    val patient = (state as? UiState.Content)?.data?.detail?.patient
    val identityInBar by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    Scaffold(
        topBar = {
            ClinicalTopBar(
                title = if (identityInBar && patient != null) patient.fullName else stringResource(R.string.detail_title),
                subtitle = if (identityInBar && patient != null) patient.patientNumber else null,
                onNavigateUp = onNavigateUp,
                actions = {
                    if (state is UiState.Content) {
                        TextButton(onClick = { onEdit(state.data.detail.patient.patientId) }) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
                            Text(stringResource(R.string.detail_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        UiStateContent(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.padding(padding),
            empty = {
                EmptyState(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.detail_not_found_title),
                    message = stringResource(R.string.detail_not_found_message),
                )
            },
        ) { content ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                DetailBody(content, listState)
            }
        }
    }
}

@Composable
private fun DetailBody(content: PatientDetailContent, listState: LazyListState) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val detail = content.detail
    // El nombre y los datos básicos se desplazan con el contenido para dejar altura a lo que se lee (en pantallas
    // pequeñas o con fuentes grandes una cabecera fija ocupaba casi la mitad); solo las pestañas quedan fijas.
    LazyColumn(state = listState, modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
        item(key = "header") { PatientHeader(detail, content) }
        stickyHeader(key = "tabs") {
            PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = Spacing.md) {
                DetailTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(text = stringResource(tab.labelRes), maxLines = 1) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "content") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                val tab = DetailTab.entries[selectedTab]
                when {
                    tab == DetailTab.SUMMARY -> SummaryTab(content)
                    tab.area != null -> AreaTab(tab.area, detail)
                    tab == DetailTab.APPOINTMENTS -> AppointmentsTab(detail, content)
                    else -> EmptyState(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.detail_documents_empty_title),
                        message = stringResource(R.string.detail_documents_empty_message),
                    )
                }
            }
        }
    }
}

@Composable
private fun PatientHeader(detail: PatientDetail, content: PatientDetailContent) {
    val patient = detail.patient
    val age = patient.ageOn(content.today)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = patient.fullName,
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
            StatusChip(label = stringResource(patient.status.labelRes()), tone = patient.status.tone())
        }
        Text(
            text = "${patient.patientNumber} · " + pluralStringResource(R.plurals.patient_age_years, age, age) +
                " · " + stringResource(patient.sex.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryTab(content: PatientDetailContent) {
    val detail = content.detail
    val patient = detail.patient
    val noData = stringResource(R.string.detail_no_data)

    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.detail_section_general))
        Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LabeledValue(stringResource(R.string.detail_birth_date), DateTimeFormats.birthDate(patient.birthDate))
            LabeledValue(stringResource(R.string.detail_birth_place), patient.birthPlace ?: noData)
            LabeledValue(stringResource(R.string.detail_municipality), patient.municipality ?: noData)
            LabeledValue(stringResource(R.string.detail_population), stringResource(patient.populationType.labelRes()))
        }
    }

    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.detail_section_contact))
        Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (detail.contacts.isEmpty()) {
                Text(text = stringResource(R.string.detail_no_contacts), style = MaterialTheme.typography.bodyMedium)
            }
            detail.contacts.forEach { contact ->
                val label = stringResource(if (contact.type == ContactType.PHONE) R.string.detail_phone else R.string.detail_email)
                LabeledValue(label = label, value = contact.value)
            }
        }
    }

    AssignmentsCard(detail)

    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.detail_section_next_appointment))
        val next = detail.nextAppointment(content.now)
        if (next == null) {
            Text(
                text = stringResource(R.string.detail_no_next_appointment),
                modifier = Modifier.padding(top = Spacing.sm),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            AppointmentCard(next, modifier = Modifier.padding(top = Spacing.sm), showPatient = false)
        }
    }

    EncountersCard(detail.encounters.take(SUMMARY_ITEM_LIMIT))
    AssessmentsCard(detail.assessments.take(SUMMARY_ITEM_LIMIT))
}

@Composable
private fun AreaTab(area: ClinicalArea, detail: PatientDetail) {
    val assignments = detail.assignments.filter { it.area == area }
    val encounters = detail.encounters.filter { it.area == area }
    val assessments = detail.assessments.filter { it.area == area }
    if (assignments.isEmpty() && encounters.isEmpty() && assessments.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.detail_area_empty_title, stringResource(area.labelRes())),
            message = stringResource(R.string.detail_area_empty_message),
        )
        return
    }
    if (assignments.isNotEmpty()) AssignmentsCard(detail.copy(assignments = assignments))
    if (encounters.isNotEmpty()) EncountersCard(encounters)
    if (assessments.isNotEmpty()) AssessmentsCard(assessments)
}

@Composable
private fun AppointmentsTab(detail: PatientDetail, content: PatientDetailContent) {
    if (detail.appointments.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.DateRange,
            title = stringResource(R.string.detail_appointments_empty_title),
            message = stringResource(R.string.detail_appointments_empty_message),
        )
        return
    }
    val (history, upcoming) = detail.appointments.partition { it.end < content.now }
    if (upcoming.isNotEmpty()) {
        SectionHeader(title = stringResource(R.string.appointments_section_upcoming))
        upcoming.sortedBy { it.start }.forEach { AppointmentCard(it, showPatient = false) }
    }
    if (history.isNotEmpty()) {
        SectionHeader(title = stringResource(R.string.appointments_section_history))
        history.sortedByDescending { it.start }.forEach { AppointmentCard(it, showPatient = false) }
    }
}

@Composable
private fun AssignmentsCard(detail: PatientDetail) {
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.detail_section_assignments))
        Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (detail.assignments.isEmpty()) {
                Text(text = stringResource(R.string.detail_no_assignments), style = MaterialTheme.typography.bodyMedium)
            }
            detail.assignments.forEach { assignment ->
                LabeledValue(
                    label = stringResource(assignment.area.labelRes()),
                    value = assignment.professionalName + " · " +
                        stringResource(R.string.detail_assigned_since, DateTimeFormats.date(assignment.since)),
                )
            }
        }
    }
}

@Composable
private fun EncountersCard(encounters: List<EncounterSummary>) {
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.detail_section_encounters))
        Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (encounters.isEmpty()) {
                Text(text = stringResource(R.string.detail_no_encounters), style = MaterialTheme.typography.bodyMedium)
            }
            encounters.forEach { encounter ->
                LabeledValue(
                    label = stringResource(encounter.type.labelRes()) + " · " + stringResource(encounter.area.labelRes()),
                    value = DateTimeFormats.date(encounter.eventAt) + " · " + encounter.professionalName +
                        " · " + stringResource(encounter.status.labelRes()),
                )
            }
        }
    }
}

@Composable
private fun AssessmentsCard(assessments: List<AssessmentSummary>) {
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.detail_section_assessments))
        Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (assessments.isEmpty()) {
                Text(text = stringResource(R.string.detail_no_assessments), style = MaterialTheme.typography.bodyMedium)
            }
            assessments.forEach { assessment ->
                val heading = listOfNotNull(
                    stringResource(R.string.assessment_title),
                    assessment.area?.let { stringResource(it.labelRes()) },
                ).joinToString(" · ")
                val result = if (assessment.hasResult) {
                    stringResource(R.string.assessment_result_recorded) + " · " +
                        (assessment.classificationLabel ?: stringResource(R.string.assessment_unclassified))
                } else {
                    null
                }
                LabeledValue(
                    label = heading,
                    value = listOfNotNull(
                        DateTimeFormats.date(assessment.startedAt),
                        assessment.professionalName,
                        stringResource(assessment.status.labelRes()),
                        result,
                    ).joinToString(" · "),
                )
            }
        }
    }
}
