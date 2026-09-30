package mx.crnl.clinica.beta.domain.model

import java.time.Instant

/**
 * Instrumento de una aplicación, reducido a lo que esta Beta conoce: dos marcadores ficticios. Ningún código se
 * traduce a un instrumento clínico real; cualquier otro queda como [UNRECOGNIZED].
 */
enum class AssessmentInstrument {
    PLACEHOLDER_A,
    PLACEHOLDER_B,
    UNRECOGNIZED,
    ;

    companion object {
        fun fromCode(code: String): AssessmentInstrument = when (code) {
            "DEV_PLACEHOLDER_A" -> PLACEHOLDER_A
            "DEV_PLACEHOLDER_B" -> PLACEHOLDER_B
            else -> UNRECOGNIZED
        }
    }
}

/**
 * Una aplicación de un instrumento tal como la lista el historial. [rawScore] es el valor ficticio ya registrado (o
 * nulo si no hay resultado): no se calcula, no se clasifica y no se interpreta.
 */
data class AssessmentSummary(
    val assessmentId: String,
    val area: ClinicalArea?,
    val professionalName: String,
    val status: AssessmentStatus,
    val startedAt: Instant,
    val completedAt: Instant?,
    val instrument: AssessmentInstrument,
    val instrumentVersion: String,
    val mode: AdministrationMode,
    val hasResult: Boolean,
    val rawScore: Double?,
)

data class AssessmentDetail(
    val summary: AssessmentSummary,
    val patientId: String,
    val patientName: String,
    val patientNumber: String,
    val encounterId: String?,
    val scoringVersion: String?,
    /** El modo supervisado solo se abre por rol; una lectura por acceso temporal no lo habilita. */
    val canOpenSupervisedPreview: Boolean,
)

/** Punto de la evolución simple de un mismo instrumento: solo fecha y valor ficticio registrado. */
data class AssessmentTrendPoint(val at: Instant, val rawScore: Double)

/** Confirmación de que la vista previa del modo supervisado se abrió; no lleva datos del paciente. */
data object SupervisedPreview
