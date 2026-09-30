package mx.crnl.clinica.beta.feature.appointments

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.domain.appointment.AppointmentField
import mx.crnl.clinica.beta.domain.appointment.AppointmentIssue
import mx.crnl.clinica.beta.domain.appointment.ConflictKind
import mx.crnl.clinica.beta.domain.clinical.EncounterField
import mx.crnl.clinica.beta.domain.clinical.EncounterIssue
import mx.crnl.clinica.beta.domain.common.OperationError

/** Texto junto a un campo de cita; nunca se muestra una excepción ni un código interno. */
@Composable
fun appointmentIssueText(field: AppointmentField, issue: AppointmentIssue): String = stringResource(appointmentIssueRes(field, issue))

@StringRes
fun appointmentIssueRes(field: AppointmentField, issue: AppointmentIssue): Int = when (issue) {
    AppointmentIssue.REQUIRED -> when (field) {
        AppointmentField.PATIENT -> R.string.issue_choose_patient
        AppointmentField.AREA -> R.string.issue_choose_area
        AppointmentField.DATE -> R.string.issue_date_required
        AppointmentField.TIME -> R.string.issue_time_required
        AppointmentField.LOCATION -> R.string.issue_location_required
        else -> R.string.issue_required
    }
    AppointmentIssue.INVALID -> when (field) {
        AppointmentField.DATE -> R.string.issue_invalid_date
        AppointmentField.TIME -> R.string.issue_invalid_time
        AppointmentField.MEETING_URL -> R.string.issue_invalid_url
        else -> R.string.issue_invalid
    }
    AppointmentIssue.TOO_LONG -> if (field == AppointmentField.DURATION) R.string.issue_duration_long else R.string.issue_too_long
    AppointmentIssue.TOO_SHORT -> R.string.issue_duration_short
    AppointmentIssue.IN_THE_PAST -> R.string.issue_in_the_past
}

@Composable
fun encounterIssueText(field: EncounterField, issue: EncounterIssue): String = stringResource(encounterIssueRes(field, issue))

@StringRes
fun encounterIssueRes(field: EncounterField, issue: EncounterIssue): Int = when (issue) {
    EncounterIssue.REQUIRED -> when (field) {
        EncounterField.TYPE -> R.string.issue_choose_encounter_type
        EncounterField.DATE -> R.string.issue_date_required
        EncounterField.TIME -> R.string.issue_time_required
        EncounterField.APPOINTMENT -> R.string.issue_required
    }
    EncounterIssue.INVALID -> if (field == EncounterField.TIME) R.string.issue_invalid_time else R.string.issue_invalid_date
    EncounterIssue.IN_THE_FUTURE -> R.string.issue_encounter_future
    EncounterIssue.TOO_OLD -> R.string.issue_encounter_old
}

/** Mensaje de un rechazo de escritura para mostrar en pantalla, sin datos de la base ni de la pila. */
@StringRes
fun OperationError.messageRes(): Int = when (this) {
    OperationError.NotAuthorized -> R.string.error_not_authorized
    is OperationError.NotFound -> R.string.error_not_found
    is OperationError.InvalidAppointment -> R.string.error_invalid_data
    is OperationError.InvalidEncounter -> R.string.error_invalid_data
    OperationError.PatientNotActive -> R.string.error_patient_not_active
    OperationError.ProfessionalNotAvailable -> R.string.error_professional_unavailable
    OperationError.NoActiveAssignment -> R.string.error_no_assignment
    OperationError.ProfessionalNotAssigned -> R.string.error_professional_not_assigned
    OperationError.AssignmentAlreadyActive -> R.string.error_assignment_already
    is OperationError.ScheduleConflicts -> R.string.error_schedule_conflict
    is OperationError.InvalidTransition -> R.string.error_invalid_transition
    is OperationError.StatusChanged -> R.string.error_status_changed
    is OperationError.NotEditable -> R.string.error_not_editable
    OperationError.AppointmentMismatch -> R.string.error_appointment_mismatch
    OperationError.DuplicateEncounter -> R.string.error_duplicate_encounter
}

@StringRes
fun ConflictKind.titleRes(): Int = when (this) {
    ConflictKind.PROFESSIONAL -> R.string.conflict_professional
    ConflictKind.PATIENT -> R.string.conflict_patient
}
