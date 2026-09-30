package mx.crnl.clinica.beta.domain.model

// Catálogos alineados con el Master Handoff y el Handoff 2. Los códigos son estables (se persisten
// por nombre); los valores todavía sin catálogo oficial de Cruz Roja quedan como provisionales.

enum class ClinicalArea { PSYCHOLOGY, NUTRITION, GENERAL_MEDICINE }

enum class UserRole { PROFESSIONAL, AREA_COORDINATOR, CLINICAL_ADMIN, SYSTEM_ADMIN }

enum class AccountStatus { PENDING_APPROVAL, ACTIVE, SUSPENDED, REJECTED, INACTIVE }

enum class Sex { FEMALE, MALE, OTHER }

enum class PopulationType {
    STUDENT,
    STUDENT_FAMILY,
    EMPLOYEE,
    EMPLOYEE_FAMILY,
    VOLUNTEER_YOUTH,
    TRAINING_ALUMNI,
    CORPORATE_AGREEMENT,
    GENERAL_PUBLIC,
    OTHER,
}

enum class PatientStatus { ACTIVE, INACTIVE, ARCHIVED, BLOCKED }

enum class ContactType { PHONE, EMAIL }

enum class RecordStatus { ACTIVE, INACTIVE }

enum class AssignmentStatus { ACTIVE, ENDED }

enum class AppointmentStatus { PENDING, SCHEDULED, CONFIRMED, RESCHEDULED, COMPLETED, NO_SHOW, CANCELLED }

enum class AppointmentModality { IN_PERSON, ONLINE }

enum class EncounterType { INITIAL, FOLLOW_UP, INTERVENTION, ASSESSMENT, CLOSURE, OTHER }

enum class EncounterStatus { DRAFT, COMPLETED }

enum class AssessmentStatus { STARTED, COMPLETED, CANCELLED, INVALIDATED }

enum class AdministrationMode { PROFESSIONAL_CAPTURE, SUPERVISED_PATIENT }

/** Acciones que la aplicación registra en la bitácora de auditoría; se persisten por nombre. */
enum class AuditAction {
    LOGIN,
    LOGOUT,
    USER_REQUESTED,
    PATIENT_CREATED,
    PATIENT_VIEWED,
    PATIENT_UPDATED,
    ASSIGNMENT_CREATED,
    APPOINTMENT_CREATED,
    APPOINTMENT_UPDATED,
    APPOINTMENT_SCHEDULED,
    APPOINTMENT_CONFIRMED,
    APPOINTMENT_RESCHEDULED,
    APPOINTMENT_COMPLETED,
    APPOINTMENT_NO_SHOW,
    APPOINTMENT_CANCELLED,
    APPOINTMENT_WHATSAPP_OPENED,
    ENCOUNTER_CREATED,
}
