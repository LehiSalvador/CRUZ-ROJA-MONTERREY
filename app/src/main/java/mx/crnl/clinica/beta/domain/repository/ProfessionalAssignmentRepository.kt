package mx.crnl.clinica.beta.domain.repository

import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.PatientAssignment
import mx.crnl.clinica.beta.domain.model.ProfessionalOption
import mx.crnl.clinica.beta.domain.model.UserAccount

/**
 * Asignación de profesionales. Es un historial: nunca se sobrescribe. En esta versión solo existe la asignación
 * inicial; cambiar de profesional (cerrar la vigente y abrir otra) pertenece a un módulo posterior.
 */
interface ProfessionalAssignmentRepository {
    /** Asignaciones vigentes del paciente en las áreas que [viewer] puede ver. */
    fun observeActiveAssignments(patientId: String, viewer: UserAccount): Flow<List<PatientAssignment>>

    /** Historial (vigente y cerradas) de un área, la más reciente primero; vacío si [viewer] no ve el área. */
    fun observeHistory(patientId: String, area: ClinicalArea, viewer: UserAccount): Flow<List<PatientAssignment>>

    /** La asignación vigente del paciente en el área; nula si no hay o si [viewer] no ve el área. */
    suspend fun getActiveAssignment(patientId: String, area: ClinicalArea, viewer: UserAccount): PatientAssignment?

    /** Cuentas activas con rol profesional del área; vacío si [viewer] no puede asignar en ella. */
    suspend fun listAssignableProfessionals(area: ClinicalArea, viewer: UserAccount): List<ProfessionalOption>

    /** Crea la asignación inicial y deja constancia de quién asignó; rechaza si ya hay una vigente. */
    suspend fun createInitialAssignment(
        patientId: String,
        area: ClinicalArea,
        professionalId: String,
        reason: String?,
        actorUserId: String,
    ): OperationResult<PatientAssignment>
}
