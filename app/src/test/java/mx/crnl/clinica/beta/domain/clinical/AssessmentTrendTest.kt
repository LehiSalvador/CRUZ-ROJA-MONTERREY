package mx.crnl.clinica.beta.domain.clinical

import java.time.Instant
import mx.crnl.clinica.beta.domain.model.AdministrationMode
import mx.crnl.clinica.beta.domain.model.AssessmentInstrument
import mx.crnl.clinica.beta.domain.model.AssessmentStatus
import mx.crnl.clinica.beta.domain.model.AssessmentSummary
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssessmentTrendTest {
    private fun assessment(
        id: String,
        day: Long,
        score: Double? = 10.0,
        instrument: AssessmentInstrument = AssessmentInstrument.PLACEHOLDER_A,
        version: String = "0",
        status: AssessmentStatus = AssessmentStatus.COMPLETED,
    ) = AssessmentSummary(
        assessmentId = id,
        area = ClinicalArea.PSYCHOLOGY,
        professionalName = "Nombre",
        status = status,
        startedAt = Instant.EPOCH.plusSeconds(day * 86_400),
        completedAt = Instant.EPOCH.plusSeconds(day * 86_400 + 1_200),
        instrument = instrument,
        instrumentVersion = version,
        mode = AdministrationMode.PROFESSIONAL_CAPTURE,
        hasResult = score != null,
        rawScore = score,
    )

    @Test
    fun `sin tres registros comparables del mismo instrumento no hay serie`() {
        val two = listOf(assessment("a", 1), assessment("b", 2))

        assertTrue(AssessmentTrend.series(two).isEmpty())
    }

    @Test
    fun `tres registros del mismo instrumento y version forman una serie cronologica`() {
        val series = AssessmentTrend.series(listOf(assessment("c", 30, 12.0), assessment("a", 1, 18.0), assessment("b", 15, 15.0)))

        val points = series.getValue(AssessmentInstrument.PLACEHOLDER_A to "0")
        assertEquals(listOf(18.0, 15.0, 12.0), points.map { it.rawScore })
    }

    @Test
    fun `los instrumentos distintos nunca se mezclan en una misma serie`() {
        val mixed = listOf(
            assessment("a", 1, instrument = AssessmentInstrument.PLACEHOLDER_A),
            assessment("b", 2, instrument = AssessmentInstrument.PLACEHOLDER_B),
            assessment("c", 3, instrument = AssessmentInstrument.PLACEHOLDER_A),
            assessment("d", 4, instrument = AssessmentInstrument.PLACEHOLDER_B),
        )

        assertTrue("dos y dos no alcanzan el minimo", AssessmentTrend.series(mixed).isEmpty())
    }

    @Test
    fun `las versiones distintas de un instrumento no son comparables`() {
        val versions = listOf(assessment("a", 1, version = "0"), assessment("b", 2, version = "1"), assessment("c", 3, version = "0"))

        assertTrue(AssessmentTrend.series(versions).isEmpty())
    }

    @Test
    fun `las canceladas, las sin resultado y los instrumentos no reconocidos no cuentan`() {
        val list = listOf(
            assessment("a", 1),
            assessment("b", 2, status = AssessmentStatus.CANCELLED),
            assessment("c", 3, score = null),
            assessment("d", 4, instrument = AssessmentInstrument.UNRECOGNIZED),
            assessment("e", 5),
        )

        assertTrue(AssessmentTrend.series(list).isEmpty())
    }
}
