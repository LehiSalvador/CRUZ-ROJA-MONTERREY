package mx.crnl.clinica.beta.domain.clinical

import mx.crnl.clinica.beta.domain.model.AssessmentInstrument
import mx.crnl.clinica.beta.domain.model.AssessmentStatus
import mx.crnl.clinica.beta.domain.model.AssessmentSummary
import mx.crnl.clinica.beta.domain.model.AssessmentTrendPoint

/**
 * Evolución simple de un mismo instrumento y versión: solo fecha y valor ficticio ya registrado, en orden cronológico.
 * No compara instrumentos distintos, no calcula cambios ni porcentajes y no interpreta: si no hay una serie
 * comparable suficiente devuelve nada.
 */
object AssessmentTrend {
    const val MIN_POINTS = 3

    /** Series comparables por instrumento y versión que cumplen el mínimo de puntos. */
    fun series(assessments: List<AssessmentSummary>): Map<Pair<AssessmentInstrument, String>, List<AssessmentTrendPoint>> =
        assessments
            .filter { it.status == AssessmentStatus.COMPLETED && it.rawScore != null && it.instrument != AssessmentInstrument.UNRECOGNIZED }
            .groupBy { it.instrument to it.instrumentVersion }
            .mapValues { (_, group) ->
                group
                    .sortedBy { it.completedAt ?: it.startedAt }
                    .map { AssessmentTrendPoint(it.completedAt ?: it.startedAt, requireNotNull(it.rawScore)) }
            }
            .filterValues { it.size >= MIN_POINTS }
}
