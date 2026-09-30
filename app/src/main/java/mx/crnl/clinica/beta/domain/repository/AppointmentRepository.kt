package mx.crnl.clinica.beta.domain.repository

import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.domain.appointment.AppointmentAction
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AppointmentAdminUpdate
import mx.crnl.clinica.beta.domain.model.AppointmentDetail
import mx.crnl.clinica.beta.domain.model.AppointmentDraft
import mx.crnl.clinica.beta.domain.model.AppointmentReschedule
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.UserAccount

/**
 * Agenda de citas. Las lecturas se acotan a lo que [viewer] puede ver; las escrituras identifican a quien actúa por su
 * identificador y el repositorio comprueba la política de la Beta contra la cuenta vigente (no contra lo que la
 * interfaz haya ocultado). Una cita nunca se borra: cancelar cambia su estado.
 */
interface AppointmentRepository {
    /** Citas visibles para [viewer] según su rol, de la más antigua a la más reciente. */
    fun observeAppointments(viewer: UserAccount): Flow<List<AppointmentSummary>>

    /** Citas de un paciente en las áreas que [viewer] puede ver. */
    fun observePatientAppointments(patientId: String, viewer: UserAccount): Flow<List<AppointmentSummary>>

    /** La cita completa; emite nulo si no existe o si [viewer] no puede verla. */
    fun observeAppointment(appointmentId: String, viewer: UserAccount): Flow<AppointmentDetail?>

    suspend fun getAppointment(appointmentId: String, viewer: UserAccount): AppointmentDetail?

    /** La próxima cita abierta del paciente que [viewer] puede ver, o nulo. */
    suspend fun getNextAppointment(patientId: String, viewer: UserAccount): AppointmentSummary?

    suspend fun createAppointment(draft: AppointmentDraft, actorUserId: String): OperationResult<AppointmentDetail>

    /** Corrige modalidad, ubicación, enlace y notas; no mueve la cita ni cambia su estado. */
    suspend fun updateAdministrativeData(
        appointmentId: String,
        update: AppointmentAdminUpdate,
        actorUserId: String,
    ): OperationResult<AppointmentDetail>

    /**
     * Cambia fecha y hora conservando la misma cita y dejándola en estado reprogramada. [expectedStatus] es el estado
     * que la persona veía: si ya cambió, no se aplica.
     */
    suspend fun reschedule(
        appointmentId: String,
        schedule: AppointmentReschedule,
        expectedStatus: AppointmentStatus,
        actorUserId: String,
    ): OperationResult<AppointmentDetail>

    /**
     * Aplica programar, confirmar, realizada, no asistió o cancelar. Reprogramar tiene su propia operación. La razón
     * administrativa (opcional) solo se admite al cancelar.
     */
    suspend fun applyAction(
        appointmentId: String,
        action: AppointmentAction,
        expectedStatus: AppointmentStatus,
        reason: String?,
        actorUserId: String,
    ): OperationResult<AppointmentDetail>

    /** Deja constancia de que se abrió la aplicación externa de mensajería; no guarda teléfono ni texto. */
    suspend fun recordWhatsAppOpened(appointmentId: String, actorUserId: String): OperationResult<Unit>
}
