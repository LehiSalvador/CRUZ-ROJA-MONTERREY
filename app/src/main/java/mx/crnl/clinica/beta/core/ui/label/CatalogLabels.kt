package mx.crnl.clinica.beta.core.ui.label

import androidx.annotation.StringRes
import mx.crnl.clinica.beta.R
import mx.crnl.clinica.beta.core.ui.component.StatusTone
import mx.crnl.clinica.beta.domain.access.AccountAction
import mx.crnl.clinica.beta.domain.model.AccessGrantStatus
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.AdministrationMode
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AssessmentInstrument
import mx.crnl.clinica.beta.domain.model.AssessmentStatus
import mx.crnl.clinica.beta.domain.model.AuditAction
import mx.crnl.clinica.beta.domain.model.AuditCategory
import mx.crnl.clinica.beta.domain.model.AuditPeriod
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.DuplicateReason
import mx.crnl.clinica.beta.domain.model.EncounterStatus
import mx.crnl.clinica.beta.domain.model.EncounterType
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.RequestStatus
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.domain.model.UserRole

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

@StringRes
fun Sex.labelRes(): Int = when (this) {
    Sex.FEMALE -> R.string.sex_female
    Sex.MALE -> R.string.sex_male
    Sex.OTHER -> R.string.sex_other
}

@StringRes
fun UserRole.labelRes(): Int = when (this) {
    UserRole.PROFESSIONAL -> R.string.role_professional
    UserRole.AREA_COORDINATOR -> R.string.role_area_coordinator
    UserRole.CLINICAL_ADMIN -> R.string.role_clinical_admin
    UserRole.SYSTEM_ADMIN -> R.string.role_system_admin
}

@StringRes
fun AccountStatus.labelRes(): Int = when (this) {
    AccountStatus.ACTIVE -> R.string.account_status_active
    AccountStatus.PENDING_APPROVAL -> R.string.account_status_pending_approval
    AccountStatus.SUSPENDED -> R.string.account_status_suspended
    AccountStatus.REJECTED -> R.string.account_status_rejected
    AccountStatus.INACTIVE -> R.string.account_status_inactive
}

fun AccountStatus.tone(): StatusTone = when (this) {
    AccountStatus.ACTIVE -> StatusTone.Success
    AccountStatus.PENDING_APPROVAL -> StatusTone.Warning
    AccountStatus.SUSPENDED, AccountStatus.REJECTED -> StatusTone.Danger
    AccountStatus.INACTIVE -> StatusTone.Neutral
}

@StringRes
fun EncounterType.labelRes(): Int = when (this) {
    EncounterType.INITIAL -> R.string.encounter_type_initial
    EncounterType.FOLLOW_UP -> R.string.encounter_type_follow_up
    EncounterType.INTERVENTION -> R.string.encounter_type_intervention
    EncounterType.ASSESSMENT -> R.string.encounter_type_assessment
    EncounterType.CLOSURE -> R.string.encounter_type_closure
    EncounterType.OTHER -> R.string.encounter_type_other
}

@StringRes
fun EncounterStatus.labelRes(): Int = when (this) {
    EncounterStatus.DRAFT -> R.string.encounter_status_draft
    EncounterStatus.COMPLETED -> R.string.encounter_status_completed
}

@StringRes
fun AssessmentStatus.labelRes(): Int = when (this) {
    AssessmentStatus.STARTED -> R.string.assessment_status_started
    AssessmentStatus.COMPLETED -> R.string.assessment_status_completed
    AssessmentStatus.CANCELLED -> R.string.assessment_status_cancelled
    AssessmentStatus.INVALIDATED -> R.string.assessment_status_invalidated
}

@StringRes
fun DuplicateReason.labelRes(): Int = when (this) {
    DuplicateReason.SAME_EMAIL -> R.string.duplicate_reason_email
    DuplicateReason.SAME_PHONE -> R.string.duplicate_reason_phone
    DuplicateReason.SAME_NAME_AND_BIRTH_DATE -> R.string.duplicate_reason_name
    DuplicateReason.SIMILAR_NAME_AND_BIRTH_DATE -> R.string.duplicate_reason_similar_name
}

@StringRes
fun RequestStatus.labelRes(): Int = when (this) {
    RequestStatus.PENDING -> R.string.request_status_pending
    RequestStatus.APPROVED -> R.string.request_status_approved
    RequestStatus.REJECTED -> R.string.request_status_rejected
}

fun RequestStatus.tone(): StatusTone = when (this) {
    RequestStatus.PENDING -> StatusTone.Warning
    RequestStatus.APPROVED -> StatusTone.Success
    RequestStatus.REJECTED -> StatusTone.Danger
}

@StringRes
fun AccessGrantStatus.labelRes(): Int = when (this) {
    AccessGrantStatus.ACTIVE -> R.string.grant_status_active
    AccessGrantStatus.REVOKED -> R.string.grant_status_revoked
    AccessGrantStatus.EXPIRED -> R.string.grant_status_expired
}

fun AccessGrantStatus.tone(): StatusTone = when (this) {
    AccessGrantStatus.ACTIVE -> StatusTone.Success
    AccessGrantStatus.REVOKED -> StatusTone.Danger
    AccessGrantStatus.EXPIRED -> StatusTone.Neutral
}

@StringRes
fun AccountAction.labelRes(): Int = when (this) {
    AccountAction.APPROVE -> R.string.account_action_approve
    AccountAction.REJECT -> R.string.account_action_reject
    AccountAction.SUSPEND -> R.string.account_action_suspend
    AccountAction.REACTIVATE -> R.string.account_action_reactivate
}

/** Nombre genérico del instrumento: los marcadores ficticios nunca se presentan como un instrumento clínico real. */
@StringRes
fun AssessmentInstrument.labelRes(): Int = when (this) {
    AssessmentInstrument.PLACEHOLDER_A -> R.string.instrument_a
    AssessmentInstrument.PLACEHOLDER_B -> R.string.instrument_b
    AssessmentInstrument.UNRECOGNIZED -> R.string.instrument_unrecognized
}

@StringRes
fun AdministrationMode.labelRes(): Int = when (this) {
    AdministrationMode.PROFESSIONAL_CAPTURE -> R.string.mode_professional_capture
    AdministrationMode.SUPERVISED_PATIENT -> R.string.mode_supervised
}

@StringRes
fun AuditCategory.labelRes(): Int = when (this) {
    AuditCategory.SESSIONS -> R.string.audit_category_sessions
    AuditCategory.PATIENTS -> R.string.audit_category_patients
    AuditCategory.CARE -> R.string.audit_category_care
    AuditCategory.REQUESTS -> R.string.audit_category_requests
    AuditCategory.SYSTEM -> R.string.audit_category_system
}

@StringRes
fun AuditPeriod.labelRes(): Int = when (this) {
    AuditPeriod.ALL -> R.string.audit_period_all
    AuditPeriod.TODAY -> R.string.audit_period_today
    AuditPeriod.LAST_7_DAYS -> R.string.audit_period_week
}

/** Nombre legible de una acción auditada; una que esta versión no conozca se muestra como «Otra acción». */
@StringRes
fun AuditAction?.labelRes(): Int = when (this) {
    null -> R.string.audit_action_other
    AuditAction.LOGIN -> R.string.audit_action_login
    AuditAction.LOGOUT -> R.string.audit_action_logout
    AuditAction.USER_REQUESTED -> R.string.audit_action_user_requested
    AuditAction.PATIENT_CREATED -> R.string.audit_action_patient_created
    AuditAction.PATIENT_VIEWED -> R.string.audit_action_patient_viewed
    AuditAction.PATIENT_UPDATED -> R.string.audit_action_patient_updated
    AuditAction.ASSIGNMENT_CREATED -> R.string.audit_action_assignment_created
    AuditAction.APPOINTMENT_CREATED -> R.string.audit_action_appointment_created
    AuditAction.APPOINTMENT_UPDATED -> R.string.audit_action_appointment_updated
    AuditAction.APPOINTMENT_SCHEDULED -> R.string.audit_action_appointment_scheduled
    AuditAction.APPOINTMENT_CONFIRMED -> R.string.audit_action_appointment_confirmed
    AuditAction.APPOINTMENT_RESCHEDULED -> R.string.audit_action_appointment_rescheduled
    AuditAction.APPOINTMENT_COMPLETED -> R.string.audit_action_appointment_completed
    AuditAction.APPOINTMENT_NO_SHOW -> R.string.audit_action_appointment_no_show
    AuditAction.APPOINTMENT_CANCELLED -> R.string.audit_action_appointment_cancelled
    AuditAction.APPOINTMENT_WHATSAPP_OPENED -> R.string.audit_action_whatsapp_opened
    AuditAction.ENCOUNTER_CREATED -> R.string.audit_action_encounter_created
    AuditAction.USER_APPROVED -> R.string.audit_action_user_approved
    AuditAction.USER_REJECTED -> R.string.audit_action_user_rejected
    AuditAction.USER_SUSPENDED -> R.string.audit_action_user_suspended
    AuditAction.USER_REACTIVATED -> R.string.audit_action_user_reactivated
    AuditAction.INTERAREA_REQUEST_CREATED -> R.string.audit_action_access_requested
    AuditAction.INTERAREA_REQUEST_APPROVED -> R.string.audit_action_access_approved
    AuditAction.INTERAREA_REQUEST_REJECTED -> R.string.audit_action_access_rejected
    AuditAction.ACCESS_GRANT_REVOKED -> R.string.audit_action_grant_revoked
    AuditAction.OVERRIDE_REQUEST_CREATED -> R.string.audit_action_change_requested
    AuditAction.OVERRIDE_REQUEST_APPROVED -> R.string.audit_action_change_approved
    AuditAction.OVERRIDE_REQUEST_REJECTED -> R.string.audit_action_change_rejected
    AuditAction.SUPERVISED_PREVIEW_OPENED -> R.string.audit_action_supervised_opened
    AuditAction.BETA_DATA_RESET -> R.string.audit_action_beta_reset
}
