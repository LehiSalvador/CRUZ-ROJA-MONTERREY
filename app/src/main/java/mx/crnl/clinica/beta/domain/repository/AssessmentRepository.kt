package mx.crnl.clinica.beta.domain.repository

import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AssessmentDetail
import mx.crnl.clinica.beta.domain.model.AssessmentSummary
import mx.crnl.clinica.beta.domain.model.SupervisedPreview
import mx.crnl.clinica.beta.domain.model.UserAccount

/**
 * Historial estructural de evaluaciones, de solo lectura. No hay reactivos, respuestas ni cálculo: solo lo que ya
 * está registrado, y únicamente de las áreas que [UserAccount] puede leer (por su rol o por un acceso temporal).
 */
interface AssessmentRepository {
    /** Aplicaciones del paciente que [viewer] puede leer, la más reciente primero. */
    fun observePatientAssessments(patientId: String, viewer: UserAccount): Flow<List<AssessmentSummary>>

    /** La aplicación; emite nulo si no existe o si [viewer] no puede leer su área. */
    fun observeAssessment(assessmentId: String, viewer: UserAccount): Flow<AssessmentDetail?>

    /**
     * Autoriza y registra la apertura de la vista previa del modo supervisado. No crea aplicaciones ni resultados:
     * solo lo permite quien puede abrirla por su rol.
     */
    suspend fun openSupervisedPreview(assessmentId: String, actorUserId: String): OperationResult<SupervisedPreview>
}
