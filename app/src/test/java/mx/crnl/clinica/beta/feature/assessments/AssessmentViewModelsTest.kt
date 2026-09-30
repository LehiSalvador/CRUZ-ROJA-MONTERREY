package mx.crnl.clinica.beta.feature.assessments

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.domain.model.AdministrationMode
import mx.crnl.clinica.beta.domain.model.AssessmentInstrument
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.testing.FakeAssessmentRepository
import mx.crnl.clinica.beta.testing.FakeAuthRepository
import mx.crnl.clinica.beta.testing.MainDispatcherRule
import mx.crnl.clinica.beta.testing.assessmentDetailOf
import mx.crnl.clinica.beta.testing.userAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AssessmentViewModelsTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val rodrigo = userAccount(id = "rodrigo", role = UserRole.PROFESSIONAL, area = ClinicalArea.PSYCHOLOGY)
    private val auth = FakeAuthRepository(listOf(rodrigo to "x"), initialUserId = "rodrigo")

    @Test
    fun `el detalle de una evaluacion existente se muestra como instrumento generico`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = AssessmentDetailViewModel(
            SavedStateHandle(mapOf("assessmentId" to "s1")),
            auth,
            FakeAssessmentRepository(mapOf("s1" to assessmentDetailOf())),
        )
        viewModel.state.onEach {}.launchIn(backgroundScope)
        advanceUntilIdle()

        val detail = (viewModel.state.value as UiState.Content).data
        assertEquals(AssessmentInstrument.PLACEHOLDER_A, detail.summary.instrument)
        assertEquals(AdministrationMode.SUPERVISED_PATIENT, detail.summary.mode)
    }

    @Test
    fun `una evaluacion inexistente o no legible para la persona no se muestra`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = AssessmentDetailViewModel(SavedStateHandle(mapOf("assessmentId" to "s9")), auth, FakeAssessmentRepository())
        viewModel.state.onEach {}.launchIn(backgroundScope)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Empty)
    }

    @Test
    fun `la vista previa supervisada solo se muestra cuando el repositorio la autoriza y la audita una vez`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeAssessmentRepository(mapOf("s1" to assessmentDetailOf()))
        val viewModel = SupervisedModeViewModel(SavedStateHandle(mapOf("assessmentId" to "s1")), auth, repository)
        assertEquals(SupervisedAccess.CHECKING, viewModel.access.value)

        advanceUntilIdle()

        assertEquals(SupervisedAccess.GRANTED, viewModel.access.value)
        assertEquals(listOf("s1"), repository.previewsOpened)
    }

    @Test
    fun `sin autorizacion la vista previa no se muestra`() = runTest(mainDispatcher.dispatcher) {
        val repository = FakeAssessmentRepository(allowPreview = false)
        val viewModel = SupervisedModeViewModel(SavedStateHandle(mapOf("assessmentId" to "s1")), auth, repository)

        advanceUntilIdle()

        assertEquals(SupervisedAccess.DENIED, viewModel.access.value)
        assertTrue(repository.previewsOpened.isEmpty())
    }
}
