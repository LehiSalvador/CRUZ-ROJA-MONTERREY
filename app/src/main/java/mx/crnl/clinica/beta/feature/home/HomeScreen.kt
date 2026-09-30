package mx.crnl.clinica.beta.feature.home

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.ClinicalCard
import mx.crnl.clinica.beta.core.ui.component.ClinicalTopBar
import mx.crnl.clinica.beta.core.ui.component.PrimaryButton
import mx.crnl.clinica.beta.core.ui.component.SecondaryButton
import mx.crnl.clinica.beta.core.ui.component.SectionHeader
import mx.crnl.clinica.beta.core.ui.component.UiStateContent
import mx.crnl.clinica.beta.core.ui.label.labelRes
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.ui.theme.ContentMaxWidth
import mx.crnl.clinica.beta.core.ui.theme.Spacing
import mx.crnl.clinica.beta.domain.access.BetaClinicalAccessPolicy
import mx.crnl.clinica.beta.domain.home.HomeSummary
import mx.crnl.clinica.beta.domain.home.PatientMetric
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.feature.appointments.AppointmentCard

class HomeActions(
    val onNewPatient: () -> Unit,
    val onSearchPatients: () -> Unit,
    val onOpenPatients: () -> Unit,
    val onOpenPatient: (String) -> Unit,
    val onOpenAppointments: () -> Unit,
    val onOpenAppointment: (String) -> Unit,
    val onNewAppointment: () -> Unit,
    val onOpenRequests: () -> Unit,
    val onOpenAdministration: () -> Unit,
)

@Composable
fun HomeRoute(factory: ViewModelProvider.Factory, actions: HomeActions) {
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state = state, actions = actions, onRetry = viewModel::retry)
}

@Composable
fun HomeScreen(state: UiState<HomeContent>, actions: HomeActions, onRetry: () -> Unit) {
    Scaffold(topBar = { ClinicalTopBar(title = stringResource(R.string.nav_home)) }) { padding ->
        UiStateContent(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.padding(padding),
            empty = {},
        ) { content ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                HomeContentView(content, actions)
            }
        }
    }
}

@Composable
private fun HomeContentView(content: HomeContent, actions: HomeActions) {
    val user = content.user
    val summary = content.summary
    Column(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.home_greeting, user.firstName),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = listOfNotNull(stringResource(user.role.labelRes()), user.area?.let { stringResource(it.labelRes()) })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // El administrador del sistema no recibe contenido clínico: su Inicio es solo administrativo.
        if (user.role == UserRole.SYSTEM_ADMIN) {
            SystemAdminHome(summary, actions)
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton(
                text = stringResource(R.string.home_action_new_patient),
                onClick = actions.onNewPatient,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = stringResource(R.string.home_action_search),
                onClick = actions.onSearchPatients,
                modifier = Modifier.weight(1f),
            )
        }

        if (BetaClinicalAccessPolicy.manageableAreas(user).isNotEmpty()) {
            SecondaryButton(
                text = stringResource(R.string.home_action_new_appointment),
                onClick = actions.onNewAppointment,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MetricCard(
                label = stringResource(summary.patientMetric.metricLabelRes()),
                count = summary.patientCount,
                onClick = actions.onOpenPatients,
            )
            MetricCard(
                label = stringResource(R.string.home_metric_today),
                count = summary.todayAppointmentCount,
                onClick = actions.onOpenAppointments,
            )
            MetricCard(
                label = stringResource(R.string.home_metric_appointments),
                count = summary.upcomingAppointmentCount,
                onClick = actions.onOpenAppointments,
            )
            // Quien revisa cuentas (coordinación, administración clínica) las ve contadas aparte de las solicitudes.
            if (user.role == UserRole.AREA_COORDINATOR || user.role == UserRole.CLINICAL_ADMIN) {
                MetricCard(
                    label = stringResource(R.string.home_metric_accounts),
                    count = summary.pendingAccountCount,
                    onClick = actions.onOpenRequests,
                )
            }
            MetricCard(
                label = stringResource(R.string.home_metric_requests),
                count = summary.pendingAccessCount + summary.pendingChangeCount,
                onClick = actions.onOpenRequests,
            )
        }

        SectionHeader(title = stringResource(R.string.home_recent_patients))
        if (summary.recentPatients.isEmpty()) {
            EmptyLine(stringResource(R.string.home_recent_empty))
        } else {
            ClinicalCard(modifier = Modifier.fillMaxWidth()) {
                summary.recentPatients.forEachIndexed { index, patient ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    RecentPatientRow(patient, content, onClick = { actions.onOpenPatient(patient.patientId) })
                }
            }
        }

        SectionHeader(title = stringResource(R.string.home_upcoming_appointments))
        if (summary.upcomingAppointments.isEmpty()) {
            EmptyLine(stringResource(R.string.home_upcoming_empty))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                summary.upcomingAppointments.forEach { appointment ->
                    AppointmentCard(appointment, onClick = { actions.onOpenAppointment(appointment.appointmentId) })
                }
            }
        }
    }
}

/** Inicio del administrador del sistema: cuentas por atender y acceso a las herramientas técnicas, sin datos clínicos. */
@Composable
private fun SystemAdminHome(summary: HomeSummary, actions: HomeActions) {
    MetricCard(
        label = stringResource(R.string.home_metric_accounts),
        count = summary.pendingAccountCount,
        onClick = actions.onOpenRequests,
    )
    ClinicalCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.home_admin_title))
        Text(
            text = stringResource(R.string.home_admin_message),
            modifier = Modifier.padding(top = Spacing.sm),
            style = MaterialTheme.typography.bodyMedium,
        )
        SecondaryButton(
            text = stringResource(R.string.home_admin_action),
            onClick = actions.onOpenAdministration,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        )
    }
}

@Composable
private fun MetricCard(label: String, count: Int, onClick: () -> Unit) {
    ClinicalCard(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RecentPatientRow(patient: Patient, content: HomeContent, onClick: () -> Unit) {
    val age = patient.ageOn(content.today)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text = patient.fullName, style = MaterialTheme.typography.titleSmall)
        Text(
            text = "${patient.patientNumber} · " + pluralStringResource(R.plurals.patient_age_years, age, age),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun PatientMetric.metricLabelRes(): Int = when (this) {
    PatientMetric.ASSIGNED_TO_USER -> R.string.home_metric_assigned
    PatientMetric.AREA_PATIENTS -> R.string.home_metric_area
    PatientMetric.ALL_ACTIVE_PATIENTS -> R.string.home_metric_all
}
