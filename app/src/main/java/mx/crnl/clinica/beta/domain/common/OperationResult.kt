package mx.crnl.clinica.beta.domain.common

import mx.crnl.clinica.beta.domain.appointment.AppointmentAction
import mx.crnl.clinica.beta.domain.appointment.AppointmentField
import mx.crnl.clinica.beta.domain.appointment.AppointmentIssue
import mx.crnl.clinica.beta.domain.clinical.EncounterField
import mx.crnl.clinica.beta.domain.clinical.EncounterIssue
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.RequestStatus
import mx.crnl.clinica.beta.domain.model.ScheduleConflict

/** Resultado de una escritura: lo esperado que puede salir mal se devuelve como dato, no como excepción. */
sealed interface OperationResult<out T> {
    data class Success<out T>(val value: T) : OperationResult<T>

    data class Failure(val error: OperationError) : OperationResult<Nothing>
}

enum class EntityKind { PATIENT, PROFESSIONAL, APPOINTMENT, ENCOUNTER, USER, ACCESS_REQUEST, ACCESS_GRANT, CHANGE_REQUEST, ASSESSMENT }

sealed interface OperationError {
    /** La política de la Beta no permite la operación a esta persona (se valida en el repositorio, no solo en la interfaz). */
    data object NotAuthorized : OperationError

    data class NotFound(val entity: EntityKind) : OperationError

    data class InvalidAppointment(val issues: Map<AppointmentField, AppointmentIssue>) : OperationError

    data class InvalidEncounter(val issues: Map<EncounterField, EncounterIssue>) : OperationError

    data object PatientNotActive : OperationError

    /** El profesional ya no está activo, no es profesional o no es del área. */
    data object ProfessionalNotAvailable : OperationError

    data object NoActiveAssignment : OperationError

    /** El profesional indicado no es el que tiene asignado el paciente en el área. */
    data object ProfessionalNotAssigned : OperationError

    data object AssignmentAlreadyActive : OperationError

    data class ScheduleConflicts(val conflicts: List<ScheduleConflict>) : OperationError

    data class InvalidTransition(val from: AppointmentStatus, val action: AppointmentAction) : OperationError

    /** El estado que la persona veía ya no es el vigente: alguien más cambió la cita. */
    data class StatusChanged(val current: AppointmentStatus) : OperationError

    /** La cita ya terminó su ciclo y sus datos no se editan. */
    data class NotEditable(val status: AppointmentStatus) : OperationError

    /** La cita vinculada no pertenece al mismo paciente, área o profesional del encuentro. */
    data object AppointmentMismatch : OperationError

    data object DuplicateEncounter : OperationError

    /** La cuenta ya no está en el estado que la persona veía: otra persona la resolvió antes. */
    data class AccountStatusChanged(val current: AccountStatus) : OperationError

    /** La solicitud ya no está pendiente: otra persona la resolvió antes. */
    data class RequestAlreadyResolved(val current: RequestStatus) : OperationError

    /** El motivo es obligatorio y debe tener una extensión razonable. */
    data object InvalidReason : OperationError

    /** Ya hay una solicitud igual sin resolver. */
    data object DuplicatePendingRequest : OperationError

    /** La persona ya puede leer esa área por su rol: no hace falta pedir acceso. */
    data object AreaAlreadyReadable : OperationError

    /** El paciente no tiene atención registrada en esa área: no hay nada que pedir. */
    data object NoAreaActivity : OperationError

    data object InvalidGrantDuration : OperationError

    /** La concesión ya no está activa (vencida o revocada). */
    data object GrantNotActive : OperationError

    /** El profesional propuesto es el mismo que ya atiende al paciente en el área. */
    data object SameProfessional : OperationError

    /** La asignación vigente ya no es la que se veía al pedir el cambio: alguien la modificó. */
    data object AssignmentChanged : OperationError
}
