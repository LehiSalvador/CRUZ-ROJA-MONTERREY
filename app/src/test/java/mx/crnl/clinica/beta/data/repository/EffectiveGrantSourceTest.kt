package mx.crnl.clinica.beta.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.domain.model.AccessRequestDraft
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.testing.RepositoryTest
import mx.crnl.clinica.beta.testing.SeedIds
import mx.crnl.clinica.beta.testing.observeAround
import mx.crnl.clinica.beta.testing.success
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** La vigencia se reevalua sola: la lectura se retira al vencer la concesion, sin volver a abrir la pantalla. */
@RunWith(AndroidJUnit4::class)
class EffectiveGrantSourceTest : RepositoryTest() {
    private suspend fun approvedGrantId(): String {
        val request = accessRequests.create(AccessRequestDraft(SeedIds.FERNANDA, ClinicalArea.NUTRITION, "Necesito consultar el seguimiento nutricional"), SeedIds.MARIANA).success()
        return requireNotNull(accessRequests.approve(request.requestId, 7, SeedIds.HECTOR).success().grant).grantId
    }

    @Test
    fun `una concesion que vence mientras se lee retira el detalle sin intervencion`() = runTest {
        val grantId = approvedGrantId()
        // Con el reloj real: empezó hace un segundo y vence en medio segundo.
        val realNow = System.currentTimeMillis()
        db.openHelper.writableDatabase.execSQL(
            "UPDATE access_grants SET validFrom = ?, expiresAt = ? WHERE accessGrantId = ?",
            arrayOf<Any>(realNow - 1_000, realNow + 600, grantId),
        )
        val reader = LocalPatientRepository(db, audit, Clock.systemUTC(), newId)

        val seen = reader.observePatientDetail(SeedIds.FERNANDA, account(SeedIds.MARIANA)).observeAround(
            change = {},
            until = { it?.grantedAreas?.isEmpty() == true },
        )

        assertTrue("al empezar la concesión vigente abre el área", seen.first()!!.grantedAreas.isNotEmpty())
        assertTrue(seen.first()!!.encounters.any { it.area == ClinicalArea.NUTRITION })
        assertTrue("al vencer se retira sola", seen.last()!!.grantedAreas.isEmpty())
        assertTrue(seen.last()!!.encounters.none { it.area == ClinicalArea.NUTRITION })
    }

    @Test
    fun `una concesion que aun no empieza no rige y empieza a regir sola`() = runTest {
        val grantId = approvedGrantId()
        val realNow = System.currentTimeMillis()
        db.openHelper.writableDatabase.execSQL(
            "UPDATE access_grants SET validFrom = ?, expiresAt = ? WHERE accessGrantId = ?",
            arrayOf<Any>(realNow + 500, realNow + 60_000, grantId),
        )
        val source = EffectiveGrantSource(db.accessGrantDao(), Clock.systemUTC())
        val mariana = account(SeedIds.MARIANA)

        // Se recoge en el despachador real: los retardos de la vigencia son de reloj y no de tiempo virtual.
        val seen = source.observe(mariana).flowOn(Dispatchers.Default).observeAround(change = {}, until = { it.isNotEmpty() })

        assertTrue(seen.first().isEmpty())
        assertEquals(grantId, seen.last().single().grantId)
    }

    @Test
    fun `sin concesiones el flujo emite una lista vacia y termina de evaluar`() = runTest {
        val source = EffectiveGrantSource(db.accessGrantDao(), Clock.systemUTC())

        assertTrue(source.observe(account(SeedIds.MARIANA)).first().isEmpty())
        assertTrue(source.current(account(SeedIds.MARIANA)).isEmpty())
        assertTrue(source.readableAreas(account(SeedIds.MARIANA), SeedIds.FERNANDA).isEmpty())
    }

    @Test
    fun `la lectura puntual coincide con lo que emite el flujo`() = runTest {
        approvedGrantId()
        val source = EffectiveGrantSource(db.accessGrantDao(), Clock.systemUTC())
        val mariana = account(SeedIds.MARIANA)

        assertEquals(setOf(ClinicalArea.NUTRITION), source.readableAreas(mariana, SeedIds.FERNANDA))
        assertTrue("otro paciente no", source.readableAreas(mariana, SeedIds.LUIS).isEmpty())
        assertTrue("otra persona no", source.readableAreas(account(SeedIds.RODRIGO), SeedIds.FERNANDA).isEmpty())
    }
}
