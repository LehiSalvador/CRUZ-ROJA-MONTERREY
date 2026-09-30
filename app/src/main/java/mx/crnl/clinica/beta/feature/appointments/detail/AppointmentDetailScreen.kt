package mx.crnl.clinica.beta.feature.appointments.detail

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.platform.ExternalLinkLauncher
import mx.crnl.clinica.beta.core.platform.rememberExternalLinkLauncher
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTextField
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.EmptyState
import mx.crnl.clinica.beta.core.ui.component.LabeledValue
import mx.crnl.clinica.beta.core.ui.component.PrimaryButton
import mx.crnl.clinica.beta.core.ui.component.SecondaryButton
import mx.crnl.clinica.beta.core.ui.component.SectionHeader
import mx.crnl.clinica.beta.core.ui.component.StatusChip
import mx.crnl.clinica.beta.core.ui.component.UiStateContent
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.label.tone
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.theme.isCompactHeight
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.core.util.DateTimeFormats
import mx.crnl.clinica.beta.domain.appointment.AppointmentAction
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.EncounterSummary
import mx.crnl.clinica.beta.feature.appointments.messageRes

class AppointmentDetailActions(
    val onNavigateUp: () -> Unit,
    val onOpenPatient: (patientId: String) -> Unit,
    val onEdit: (appointmentId: String) -> Unit,
    val onReschedule: (appointmentId: String) -> Unit,
    val onRegisterEncounter: (patientId: String, area: ClinicalArea, appointmentId: String) -> Unit,
    val onOpenEncounter: (encounterId: String) -> Unit,
)

@Composable
fun AppointmentDetailRoute(factory: ViewModelProvider.Factory, actions: AppointmentDetailActions) {
    val viewModel: AppointmentDetailViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    AppointmentDetailScreen(
        state = state,
        working = working,
        notice = notice,
        actions = actions,
        linkLauncher = rememberExternalLinkLauncher(),
        onAction = viewModel::onAction,
        onContactOpened = viewModel::onContactOpened,
        onContactUnavailable = viewModel::onContactUnavailable,
        onDismissNotice = viewModel::dismissNotice,
        onRetry = viewModel::retry,
    )
}

@Composable
fun AppointmentDetailScreen(
    state: UiState<AppointmentDetailContent>,
    working: Boolean,
    notice: DetailNotice?,
    actions: AppointmentDetailActions,
    linkLauncher: ExternalLinkLauncher,
    onAction: (AppointmentAction, String?) -> Unit,
    onContactOpened: () -> Unit,
    onContactUnavailable: () -> Unit,
    onDismissNotice: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.appointment_detail_title), onNavigateUp = actions.onNavigateUp) }) { padding ->
        UiStateContent(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.padding(padding),
            empty = {
                EmptyState(
                    icon = Icons.Filled.DateRange,
                    title = stringResource(R.string.appointment_not_found_title),
                    message = stringResource(R.string.appointment_not_found_message),
                )
            },
        ) { content ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                DetailBody(content, working, notice, actions, linkLauncher, onAction, onContactOpened, onContactUnavailable, onDismissNotice)
            }
        }
    }
}

@Composable
private fun DetailBody(
    content: AppointmentDetailContent,
    working: Boolean,
    notice: DetailNotice?,
    actions: AppointmentDetailActions,
    linkLauncher: ExternalLinkLauncher,
    onAction: (AppointmentAction, String?) -> Unit,
    onContactOpened: () -> Unit,
    onContactUnavailable: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    val detail = content.detail
    val summary = detail.summary
    // Las acciones definitivas piden confirmación; la clave se guarda para sobrevivir a la rotación.
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingAction = pending?.let { name -> AppointmentAction.entries.firstOrNull { it.name == name } }

    Column(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ClinicalCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusChip(label = stringResource(summary.status.labelRes()), tone = summary.status.tone(), singleLine = false)
                Text(
                    text = DateTimeFormats.day(summary.start) + " · " + DateTimeFormats.timeRange(summary.start, summary.end),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                )
                // En pantallas de poca altura el botón aparte quitaría espacio a las acciones: la identidad del paciente es la que abre el expediente.
                val compact = isCompactHeight()
                val openPatientLabel = stringResource(R.string.appointment_open_patient)
                Column(
                    modifier = Modifier
                        .then(if (compact) Modifier.clickable(onClickLabel = openPatientLabel) { actions.onOpenPatient(summary.patientId) } else Modifier)
                        .semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(text = summary.patientName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = summary.patientNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!compact) {
                    TextButton(onClick = { actions.onOpenPatient(summary.patientId) }) {
                        Text(stringResource(R.string.appointment_open_patient))
                    }
                }
            }
        }

        NoticeCard(notice, onDismissNotice)

        // Cancelar pide una razón opcional: la confirmación va en pantalla, en lugar de las acciones, y no en una ventana modal.
        if (pendingAction == AppointmentAction.CANCEL) {
            CancelConfirmationCard(
                onConfirm = { reason ->
                    pending = null
                    onAction(AppointmentAction.CANCEL, reason)
                },
                onDismiss = { pending = null },
                enabled = !working,
            )
        } else {
            ActionsCard(content, working, actions, linkLauncher, onAction = { action ->
                when (action) {
                    AppointmentAction.RESCHEDULE -> actions.onReschedule(detail.appointmentId)
                    AppointmentAction.COMPLETE, AppointmentAction.MARK_NO_SHOW, AppointmentAction.CANCEL -> pending = action.name
                    else -> onAction(action, null)
                }
            }, onContactOpened, onContactUnavailable)
        }

        ClinicalCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = stringResource(R.string.appointment_section_details))
            Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                LabeledValue(stringResource(R.string.appointment_area), stringResource(summary.area.labelRes()))
                LabeledValue(stringResource(R.string.appointment_professional), summary.professionalName)
                LabeledValue(stringResource(R.string.appointment_modality), stringResource(summary.modality.labelRes()))
                if (summary.modality == AppointmentModality.IN_PERSON) {
                    LabeledValue(stringResource(R.string.appointment_location), summary.location ?: stringResource(R.string.detail_no_data))
                } else {
                    LabeledValue(stringResource(R.string.appointment_link), detail.meetingUrl ?: stringResource(R.string.detail_no_data))
                }
                detail.administrativeNotes?.let { LabeledValue(stringResource(R.string.appointment_notes), it) }
            }
        }

        ClinicalCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = stringResource(R.string.appointment_section_attention))
            Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (detail.encounters.isEmpty()) {
                    Text(text = stringResource(R.string.appointment_no_encounter), style = MaterialTheme.typography.bodyMedium)
                }
                detail.encounters.forEach { encounter -> LinkedEncounter(encounter, onClick = { actions.onOpenEncounter(encounter.encounterId) }) }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.appointment_created_by, detail.createdByName, DateTimeFormats.date(detail.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.appointment_updated_at, DateTimeFormats.date(detail.updatedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingAction?.takeIf { it != AppointmentAction.CANCEL }?.let { action ->
        ConfirmActionDialog(
            action = action,
            onConfirm = {
                pending = null
                onAction(action, null)
            },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun NoticeCard(notice: DetailNotice?, onDismiss: () -> Unit) {
    if (notice == null) return
    val message = when (notice) {
        DetailNotice.WhatsAppUnavailable -> stringResource(R.string.appointment_whatsapp_unavailable)
        is DetailNotice.Failed -> stringResource(notice.error.messageRes())
        is DetailNotice.Unavailable -> stringResource(R.string.error_save_failed)
    }
    ClinicalCard(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(text = message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        }
    }
}

@Composable
private fun ActionsCard(
    content: AppointmentDetailContent,
    working: Boolean,
    actions: AppointmentDetailActions,
    linkLauncher: ExternalLinkLauncher,
    onAction: (AppointmentAction) -> Unit,
    onContactOpened: () -> Unit,
    onContactUnavailable: () -> Unit,
) {
    val hasActions = content.actions.isNotEmpty() || content.canEdit || content.contactLink != null || content.canRegisterEncounter
    if (!hasActions) return
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.appointment_section_actions))
        Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (content.canRegisterEncounter) {
                PrimaryButton(
                    text = stringResource(R.string.encounter_register),
                    onClick = { actions.onRegisterEncounter(content.detail.summary.patientId, content.detail.summary.area, content.detail.appointmentId) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !working,
                )
            }
            // Solo una acción principal: confirmar; programar y el resto de lo que hace avanzar la cita son secundarias.
            content.actions.filterNot { it in DEFINITIVE_ACTIONS }.forEach { action ->
                if (action == AppointmentAction.CONFIRM) {
                    PrimaryButton(
                        text = stringResource(action.labelRes()),
                        onClick = { onAction(action) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !working,
                    )
                } else {
                    SecondaryButton(
                        text = stringResource(action.labelRes()),
                        onClick = { onAction(action) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !working,
                    )
                }
            }
            if (content.canEdit) {
                SecondaryButton(
                    text = stringResource(R.string.appointment_action_edit),
                    onClick = { actions.onEdit(content.detail.appointmentId) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !working,
                )
            }
            content.contactLink?.let { link ->
                SecondaryButton(
                    text = stringResource(R.string.appointment_action_contact),
                    onClick = { if (linkLauncher.open(link)) onContactOpened() else onContactUnavailable() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !working,
                )
            }
            // Lo definitivo va aparte, al final y separado: no queda junto a una acción de uso frecuente.
            val definitive = content.actions.filter { it in DEFINITIVE_ACTIONS }
            if (definitive.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
                definitive.forEach { action ->
                    DangerButton(text = stringResource(action.labelRes()), onClick = { onAction(action) }, enabled = !working)
                }
            }
        }
    }
}

private val DEFINITIVE_ACTIONS = setOf(AppointmentAction.MARK_NO_SHOW, AppointmentAction.CANCEL)

/** Acción que termina la cita sin remedio en esta versión: texto en color de error, sin relleno, para que no compita con la principal. */
@Composable
private fun DangerButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) { Text(text) }
}

@Composable
private fun LinkedEncounter(encounter: EncounterSummary, onClick: () -> Unit) {
    ClinicalCard(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, onClick = onClick) {
        Text(text = stringResource(encounter.type.labelRes()), style = MaterialTheme.typography.titleSmall)
        Text(
            text = DateTimeFormats.day(encounter.eventAt) + " · " + encounter.professionalName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConfirmActionDialog(action: AppointmentAction, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(action.confirmTitleRes())) },
        text = { Text(text = stringResource(action.confirmMessageRes())) },
        // La salida segura es la principal; la acción definitiva va como texto en color de error.
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.action_go_back)) } },
        dismissButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(action.confirmLabelRes())) }
        },
    )
}

private const val MAX_REASON_LENGTH = 200

private fun AppointmentAction.labelRes(): Int = when (this) {
    AppointmentAction.SCHEDULE -> R.string.appointment_action_schedule
    AppointmentAction.CONFIRM -> R.string.appointment_action_confirm
    AppointmentAction.RESCHEDULE -> R.string.appointment_action_reschedule
    AppointmentAction.COMPLETE -> R.string.appointment_action_complete
    AppointmentAction.MARK_NO_SHOW -> R.string.appointment_action_no_show
    AppointmentAction.CANCEL -> R.string.appointment_action_cancel
}

private fun AppointmentAction.confirmTitleRes(): Int = when (this) {
    AppointmentAction.COMPLETE -> R.string.appointment_confirm_complete_title
    AppointmentAction.MARK_NO_SHOW -> R.string.appointment_confirm_no_show_title
    else -> R.string.appointment_confirm_cancel_title
}

private fun AppointmentAction.confirmMessageRes(): Int = when (this) {
    AppointmentAction.COMPLETE -> R.string.appointment_confirm_complete_message
    AppointmentAction.MARK_NO_SHOW -> R.string.appointment_confirm_no_show_message
    else -> R.string.appointment_confirm_cancel_message
}

private fun AppointmentAction.confirmLabelRes(): Int = when (this) {
    AppointmentAction.COMPLETE -> R.string.appointment_confirm_complete_yes
    AppointmentAction.MARK_NO_SHOW -> R.string.appointment_confirm_no_show_yes
    else -> R.string.appointment_confirm_cancel_yes
}

/**
 * Confirma la cancelación con una razón administrativa opcional. La cita no se borra: queda en el historial. La salida
 * segura (volver) es la acción principal; cancelar va como texto en color de error.
 */
@Composable
private fun CancelConfirmationCard(onConfirm: (String?) -> Unit, onDismiss: () -> Unit, enabled: Boolean) {
    var reason by rememberSaveable { mutableStateOf("") }
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = stringResource(R.string.appointment_confirm_cancel_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = stringResource(R.string.appointment_confirm_cancel_message), style = MaterialTheme.typography.bodyMedium)
            ClinicalTextField(
                value = reason,
                onValueChange = { reason = it.take(MAX_REASON_LENGTH) },
                label = stringResource(R.string.appointment_cancel_reason),
                enabled = enabled,
            )
            PrimaryButton(text = stringResource(R.string.action_go_back), onClick = onDismiss, modifier = Modifier.fillMaxWidth(), enabled = enabled)
            DangerButton(
                text = stringResource(R.string.appointment_confirm_cancel_yes),
                onClick = { onConfirm(reason.takeIf { it.isNotBlank() }) },
                enabled = enabled,
            )
        }
    }
}
