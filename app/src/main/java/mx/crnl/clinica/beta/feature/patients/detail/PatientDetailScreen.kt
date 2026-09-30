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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import mx.crnl.clinica.beta.core.ui.component.InfoBanner
import mx.crnl.clinica.beta.core.ui.component.LabeledValue
import mx.crnl.clinica.beta.core.ui.component.PrimaryButton
import mx.crnl.clinica.beta.core.ui.component.SecondaryButton
import mx.crnl.clinica.beta.core.ui.component.SectionHeader
import mx.crnl.clinica.beta.core.ui.component.StatusChip
import mx.crnl.clinica.beta.core.ui.component.StatusTone
import mx.crnl.clinica.beta.core.ui.component.UiStateContent
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.label.tone
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.model.AreaActivity
import mx.crnl.clinica.beta.domain.model.AssignmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ContactType
import mx.crnl.clinica.beta.domain.model.EncounterSummary
import mx.crnl.clinica.beta.domain.model.OPEN_APPOINTMENT_STATUSES
import mx.crnl.clinica.beta.domain.model.PatientAssignment
import mx.crnl.clinica.beta.domain.model.PatientDetail
import mx.crnl.clinica.beta.feature.appointments.AppointmentCard
import mx.crnl.clinica.beta.feature.assessments.AssessmentsSection

/** Secciones del expediente. «Resumen» reúne todo; cada área muestra su historial longitudinal si la persona puede verlo. */
private enum class DetailTab(@StringRes val labelRes: Int, val area: ClinicalArea? = null) {
    SUMMARY(R.string.detail_tab_summary),
    PSYCHOLOGY(R.string.area_psychology, ClinicalArea.PSYCHOLOGY),
    NUTRITION(R.string.area_nutrition, ClinicalArea.NUTRITION),
    GENERAL_MEDICINE(R.string.area_general_medicine, ClinicalArea.GENERAL_MEDICINE),
    APPOINTMENTS(R.string.detail_tab_appointments),
}

private const val SUMMARY_ITEM_LIMIT = 3
private const val RECENT_APPOINTMENT_LIMIT = 3

/** Lo que el expediente puede pedir a la navegación; cada destino lo resuelve la shell. */
class PatientDetailActions(
    val onNavigateUp: () -> Unit,
    val onEdit: (patientId: String) -> Unit,
    val onNewAppointment: (patientId: String, area: ClinicalArea?) -> Unit,
    val onAssignProfessional: (patientId: String, area: ClinicalArea) -> Unit,
    val onRegisterEncounter: (patientId: String, area: ClinicalArea) -> Unit,
    val onOpenAppointment: (appointmentId: String) -> Unit,
    val onOpenEncounter: (encounterId: String) -> Unit,
    val onRequestAccess: (patientId: String, area: ClinicalArea) -> Unit,
    val onOpenAccessRequest: (requestId: String) -> Unit,
    val onRequestProfessionalChange: (patientId: String, area: ClinicalArea) -> Unit,
    val onOpenChangeRequest: (requestId: String) -> Unit,
    val onOpenAssessment: (assessmentId: String) -> Unit,
)

@Composable
fun PatientDetailRoute(factory: ViewModelProvider.Factory, actions: PatientDetailActions) {
    val viewModel: PatientDetailViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    PatientDetailScreen(
        state = state,
        selectedTab = selectedTab,
        onSelectTab = viewModel::selectTab,
        actions = actions,
        onRetry = viewModel::retry,
    )
}

@Composable
fun PatientDetailScreen(
    state: UiState<PatientDetailContent>,
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    actions: PatientDetailActions,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    // Al desplazarse el encabezado fuera de pantalla, la barra sigue diciendo de quién es el expediente.
    val patient = (state as? UiState.Content)?.data?.detail?.patient
    val identityInBar by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    Scaffold(
        topBar = {
            // La barra lleva solo el título corto y, al desplazarse, el folio: el nombre completo (largo) vive en el encabezado
            // y en una barra angosta con fuente grande se cortaría o taparía la acción de editar.
            ClinicalTopBar(
                title = stringResource(R.string.detail_title),
                subtitle = if (identityInBar && patient != null) patient.patientNumber else null,
                onNavigateUp = actions.onNavigateUp,
                actions = {
                    if (state is UiState.Content) {
                        IconButton(onClick = { actions.onEdit(state.data.detail.patient.patientId) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.detail_edit))
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
                DetailBody(content, selectedTab, onSelectTab, actions, listState)
            }
        }
    }
}

@Composable
private fun DetailBody(
    content: PatientDetailContent,
    selectedTabName: String,
    onSelectTab: (String) -> Unit,
    actions: PatientDetailActions,
    listState: LazyListState,
) {
    val selectedTab = DetailTab.entries.indexOfFirst { it.name == selectedTabName }.coerceAtLeast(0)
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
                        onClick = { onSelectTab(tab.name) },
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
                    tab == DetailTab.SUMMARY -> SummaryTab(content, actions)
                    tab.area != null -> AreaTab(tab.area, content, actions)
                    else -> AppointmentsTab(content, actions)
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

// ---------------------------------------------------------------- Resumen

@Composable
private fun SummaryTab(content: PatientDetailContent, actions: PatientDetailActions) {
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

    SectionHeader(title = stringResource(R.string.summary_areas_title))
    val activityAreas = ClinicalArea.entries.filter { area -> area in detail.viewableAreas || detail.restrictedAreas.any { it.area == area } }
    if (activityAreas.isEmpty()) {
        Text(text = stringResource(R.string.detail_no_assignments), style = MaterialTheme.typography.bodyMedium)
    }
    activityAreas.forEach { area ->
        if (area in detail.viewableAreas) {
            AreaSummaryCard(area, content, actions)
        } else {
            RestrictedAreaCard(area, detail.restrictedAreas.firstOrNull { it.area == area }, content, actions)
        }
    }

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
            AppointmentCard(
                next,
                modifier = Modifier.padding(top = Spacing.sm),
                showPatient = false,
                onClick = { actions.onOpenAppointment(next.appointmentId) },
            )
        }
        detail.lastAppointment(content.now)?.let { last ->
            Text(
                text = stringResource(R.string.summary_last_appointment, DateTimeFormats.day(last.start), stringResource(last.area.labelRes())),
                modifier = Modifier.padding(top = Spacing.md),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (content.canSchedule) {
            val area = ClinicalArea.entries.singleOrNull { content.capabilities(it).canSchedule }
            SecondaryButton(
                text = stringResource(R.string.appointments_new),
                onClick = { actions.onNewAppointment(patient.patientId, area) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            )
        }
    }

    EncountersCard(detail.encounters.take(SUMMARY_ITEM_LIMIT), actions, titleRes = R.string.summary_recent_attention)
    // Las evaluaciones se leen en la sección de su área; solo las que no se pueden atribuir a un área aparecen aquí.
    val unattributed = detail.assessments.filter { it.area == null }
    if (unattributed.isNotEmpty()) AssessmentsSection(unattributed, actions.onOpenAssessment)
}

/** Un área que la persona puede ver: quién atiende, la última atención y, si toca, asignar profesional. */
@Composable
private fun AreaSummaryCard(area: ClinicalArea, content: PatientDetailContent, actions: PatientDetailActions) {
    val detail = content.detail
    val assignment = detail.activeAssignment(area)
    val capabilities = content.capabilities(area)
    val encounters = detail.encounters.filter { it.area == area }
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(area.labelRes()))
        Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (assignment != null) {
                LabeledValue(
                    label = stringResource(R.string.area_professional_label),
                    value = assignment.professionalName + " · " + stringResource(R.string.detail_assigned_since, DateTimeFormats.date(assignment.since)),
                )
            } else {
                Text(text = stringResource(R.string.area_no_professional), style = MaterialTheme.typography.bodyMedium)
            }
            encounters.firstOrNull()?.let { last ->
                LabeledValue(
                    label = stringResource(R.string.summary_last_attention),
                    value = DateTimeFormats.date(last.eventAt) + " · " + stringResource(last.type.labelRes()),
                )
            }
            if (encounters.isNotEmpty()) {
                Text(
                    text = pluralStringResource(R.plurals.attention_count, encounters.size, encounters.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (capabilities.canAssign) {
                PrimaryButton(
                    text = stringResource(R.string.assign_action),
                    onClick = { actions.onAssignProfessional(detail.patient.patientId, area) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** De un área ajena solo se sabe que hay atención: nunca quién, de qué tipo ni con qué resultado. */
@Composable
private fun RestrictedAreaCard(area: ClinicalArea, activity: AreaActivity?, content: PatientDetailContent, actions: PatientDetailActions) {
    val detail = content.detail
    val pendingRequestId = detail.pendingAccessRequests[area]
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(area.labelRes()))
        Text(
            text = stringResource(R.string.restricted_area_message),
            modifier = Modifier.padding(top = Spacing.sm),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.restricted_area_note),
            modifier = Modifier.padding(top = Spacing.xs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (activity != null && activity.encounterCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.attention_count, activity.encounterCount, activity.encounterCount) +
                    (activity.lastActivityAt?.let { " · " + stringResource(R.string.restricted_area_last, DateTimeFormats.date(it)) }.orEmpty()),
                modifier = Modifier.padding(top = Spacing.xs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (pendingRequestId != null) {
            SecondaryButton(
                text = stringResource(R.string.access_pending_action),
                onClick = { actions.onOpenAccessRequest(pendingRequestId) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            )
        } else if (content.capabilities(area).canRequestAccess) {
            SecondaryButton(
                text = stringResource(R.string.access_request_action),
                onClick = { actions.onRequestAccess(detail.patient.patientId, area) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            )
        }
    }
}

// ---------------------------------------------------------------- Áreas

@Composable
private fun AreaTab(area: ClinicalArea, content: PatientDetailContent, actions: PatientDetailActions) {
    val detail = content.detail
    if (area !in detail.viewableAreas) {
        val activity = detail.restrictedAreas.firstOrNull { it.area == area }
        if (activity != null) {
            RestrictedAreaCard(area, activity, content, actions)
        } else {
            EmptyState(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.area_timeline_empty),
                message = stringResource(R.string.area_restricted_empty_message),
            )
        }
        return
    }
    val capabilities = content.capabilities(area)
    val assignment = detail.activeAssignment(area)
    val appointments = detail.appointments.filter { it.area == area }
    val encounters = detail.encounters.filter { it.area == area }
    val assessments = detail.assessments.filter { it.area == area }
    val history = detail.assignmentHistory.filter { it.area == area }

    detail.grantedAreas[area]?.let { expiresAt ->
        InfoBanner(
            title = stringResource(R.string.grant_banner_title),
            text = stringResource(R.string.grant_banner_valid_until, DateTimeFormats.dateTime(expiresAt)),
        )
    }

    AssignmentCard(
        area = area,
        assignment = assignment,
        canAssign = capabilities.canAssign,
        onAssign = { actions.onAssignProfessional(detail.patient.patientId, area) },
        canRequestChange = capabilities.canRequestProfessionalChange,
        pendingChangeRequestId = detail.pendingChangeRequests[area],
        onRequestChange = { actions.onRequestProfessionalChange(detail.patient.patientId, area) },
        onOpenChangeRequest = actions.onOpenChangeRequest,
    )

    if (capabilities.canSchedule || capabilities.canRegisterEncounter) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (capabilities.canSchedule) {
                SecondaryButton(
                    text = stringResource(R.string.appointments_new),
                    onClick = { actions.onNewAppointment(detail.patient.patientId, area) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (capabilities.canRegisterEncounter) {
                PrimaryButton(
                    text = stringResource(R.string.encounter_register),
                    onClick = { actions.onRegisterEncounter(detail.patient.patientId, area) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    val next = appointments.filter { it.status in OPEN_APPOINTMENT_STATUSES && it.end >= content.now }.minByOrNull { it.start }
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.detail_section_next_appointment))
        if (next == null) {
            Text(
                text = stringResource(R.string.detail_no_next_appointment),
                modifier = Modifier.padding(top = Spacing.sm),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            AppointmentCard(next, modifier = Modifier.padding(top = Spacing.sm), showPatient = false, onClick = { actions.onOpenAppointment(next.appointmentId) })
        }
    }
    val recent = appointments.filter { it.appointmentId != next?.appointmentId }.sortedByDescending { it.start }.take(RECENT_APPOINTMENT_LIMIT)
    if (recent.isNotEmpty()) {
        SectionHeader(title = stringResource(R.string.area_recent_appointments))
        recent.forEach { appointment ->
            AppointmentCard(appointment, showPatient = false, onClick = { actions.onOpenAppointment(appointment.appointmentId) })
        }
    }

    EncountersCard(encounters, actions, titleRes = R.string.area_timeline_title, emptyRes = R.string.area_timeline_empty)

    if (area == ClinicalArea.PSYCHOLOGY || assessments.isNotEmpty()) AssessmentsSection(assessments, actions.onOpenAssessment)
    if (history.size > 1 || history.any { it.status == AssignmentStatus.ENDED }) AssignmentHistoryCard(history)
}

@Composable
private fun AssignmentCard(
    area: ClinicalArea,
    assignment: PatientAssignment?,
    canAssign: Boolean,
    onAssign: () -> Unit,
    canRequestChange: Boolean,
    pendingChangeRequestId: String?,
    onRequestChange: () -> Unit,
    onOpenChangeRequest: (requestId: String) -> Unit,
) {
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.area_professional_label))
        if (assignment == null) {
            Column(modifier = Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(text = stringResource(R.string.area_no_professional), style = MaterialTheme.typography.bodyMedium)
                if (canAssign) {
                    PrimaryButton(text = stringResource(R.string.assign_action), onClick = onAssign, modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(modifier = Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = assignment.professionalName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    StatusChip(label = stringResource(R.string.assignment_status_active), tone = StatusTone.Success)
                }
                Text(
                    text = stringResource(R.string.detail_assigned_since, DateTimeFormats.date(assignment.since)) + " · " +
                        stringResource(R.string.assignment_assigned_by, assignment.assignedByName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // El profesional vigente no se edita: cambiarlo es una solicitud formal que otra persona resuelve.
                if (pendingChangeRequestId != null) {
                    SecondaryButton(
                        text = stringResource(R.string.change_pending_action),
                        onClick = { onOpenChangeRequest(pendingChangeRequestId) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (canRequestChange) {
                    SecondaryButton(
                        text = stringResource(R.string.change_request_action),
                        onClick = onRequestChange,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssignmentHistoryCard(history: List<PatientAssignment>) {
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.assignment_history_title))
        Column(modifier = Modifier.padding(top = Spacing.md)) {
            history.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = item.professionalName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        StatusChip(
                            label = stringResource(if (item.status == AssignmentStatus.ACTIVE) R.string.assignment_status_active else R.string.assignment_status_ended),
                            tone = if (item.status == AssignmentStatus.ACTIVE) StatusTone.Success else StatusTone.Neutral,
                        )
                    }
                    Text(
                        text = DateTimeFormats.date(item.since) + (item.until?.let { " – " + DateTimeFormats.date(it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.assignment_assigned_by, item.assignedByName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    item.reason?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Citas

@Composable
private fun AppointmentsTab(content: PatientDetailContent, actions: PatientDetailActions) {
    val detail = content.detail
    if (content.canSchedule) {
        val area = ClinicalArea.entries.singleOrNull { content.capabilities(it).canSchedule }
        SecondaryButton(
            text = stringResource(R.string.appointments_new),
            onClick = { actions.onNewAppointment(detail.patient.patientId, area) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
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
        upcoming.sortedBy { it.start }.forEach { AppointmentCard(it, showPatient = false, onClick = { actions.onOpenAppointment(it.appointmentId) }) }
    }
    if (history.isNotEmpty()) {
        SectionHeader(title = stringResource(R.string.appointments_section_history))
        history.sortedByDescending { it.start }.forEach { AppointmentCard(it, showPatient = false, onClick = { actions.onOpenAppointment(it.appointmentId) }) }
    }
}

// ---------------------------------------------------------------- Atención y evaluaciones

/** Línea de atención: fecha del evento, tipo, profesional y, si aplica, la cita de origen. Solo lo que existe hoy: no hay contenido clínico. */
@Composable
private fun EncountersCard(
    encounters: List<EncounterSummary>,
    actions: PatientDetailActions,
    @StringRes titleRes: Int,
    @StringRes emptyRes: Int = R.string.area_timeline_empty,
) {
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(titleRes))
        Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (encounters.isEmpty()) {
                Text(text = stringResource(emptyRes), style = MaterialTheme.typography.bodyMedium)
            }
            encounters.forEach { encounter -> EncounterRow(encounter, onClick = { actions.onOpenEncounter(encounter.encounterId) }) }
        }
    }
}

@Composable
private fun EncounterRow(encounter: EncounterSummary, onClick: () -> Unit) {
    ClinicalCard(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(encounter.type.labelRes()) + " · " + stringResource(encounter.area.labelRes()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Text(
            text = DateTimeFormats.day(encounter.eventAt) + " · " + DateTimeFormats.time(encounter.eventAt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = listOfNotNull(
                encounter.professionalName,
                if (encounter.appointmentId != null) stringResource(R.string.encounter_from_appointment) else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
