package mx.crnl.clinica.beta.core.ui.label

import androidx.annotation.StringRes
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.StatusTone
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.PopulationType

@StringRes
fun PatientStatus.labelRes(): Int = when (this) {
    PatientStatus.ACTIVE -> R.string.patient_status_active
    PatientStatus.INACTIVE -> R.string.patient_status_inactive
    PatientStatus.ARCHIVED -> R.string.patient_status_archived
    PatientStatus.BLOCKED -> R.string.patient_status_blocked
}

fun PatientStatus.tone(): StatusTone = when (this) {
    PatientStatus.ACTIVE -> StatusTone.Success
    PatientStatus.INACTIVE, PatientStatus.ARCHIVED -> StatusTone.Neutral
    PatientStatus.BLOCKED -> StatusTone.Danger
}

@StringRes
fun PopulationType.labelRes(): Int = when (this) {
    PopulationType.STUDENT -> R.string.population_student
    PopulationType.STUDENT_FAMILY -> R.string.population_student_family
    PopulationType.EMPLOYEE -> R.string.population_employee
    PopulationType.EMPLOYEE_FAMILY -> R.string.population_employee_family
    PopulationType.VOLUNTEER_YOUTH -> R.string.population_volunteer_youth
    PopulationType.TRAINING_ALUMNI -> R.string.population_training_alumni
    PopulationType.CORPORATE_AGREEMENT -> R.string.population_corporate_agreement
    PopulationType.GENERAL_PUBLIC -> R.string.population_general_public
    PopulationType.OTHER -> R.string.population_other
}

@StringRes
fun AppointmentStatus.labelRes(): Int = when (this) {
    AppointmentStatus.PENDING -> R.string.appointment_status_pending
    AppointmentStatus.SCHEDULED -> R.string.appointment_status_scheduled
    AppointmentStatus.CONFIRMED -> R.string.appointment_status_confirmed
    AppointmentStatus.RESCHEDULED -> R.string.appointment_status_rescheduled
    AppointmentStatus.COMPLETED -> R.string.appointment_status_completed
    AppointmentStatus.NO_SHOW -> R.string.appointment_status_no_show
    AppointmentStatus.CANCELLED -> R.string.appointment_status_cancelled
}

fun AppointmentStatus.tone(): StatusTone = when (this) {
    AppointmentStatus.PENDING, AppointmentStatus.RESCHEDULED -> StatusTone.Warning
    AppointmentStatus.SCHEDULED -> StatusTone.Info
    AppointmentStatus.CONFIRMED -> StatusTone.Success
    AppointmentStatus.COMPLETED -> StatusTone.Neutral
    AppointmentStatus.NO_SHOW, AppointmentStatus.CANCELLED -> StatusTone.Danger
}

@StringRes
fun AppointmentModality.labelRes(): Int = when (this) {
    AppointmentModality.IN_PERSON -> R.string.modality_in_person
    AppointmentModality.ONLINE -> R.string.modality_online
}

@StringRes
fun ClinicalArea.labelRes(): Int = when (this) {
    ClinicalArea.PSYCHOLOGY -> R.string.area_psychology
    ClinicalArea.NUTRITION -> R.string.area_nutrition
    ClinicalArea.GENERAL_MEDICINE -> R.string.area_general_medicine
}
