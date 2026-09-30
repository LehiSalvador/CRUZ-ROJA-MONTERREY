package mx.crnl.clinica.beta.feature.appointments.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.crnl.clinica.beta.core.navigation.AppRoute
import mx.crnl.clinica.beta.core.ui.state.UiState
import mx.crnl.clinica.beta.core.util.runCatchingCancellable
import mx.crnl.clinica.beta.domain.access.BetaClinicalAccessPolicy
import mx.crnl.clinica.beta.domain.appointment.AppointmentAction
import mx.crnl.clinica.beta.domain.appointment.AppointmentStatePolicy
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.contact.AppointmentContactMessageBuilder
import mx.crnl.clinica.beta.domain.contact.WhatsAppLink
import mx.crnl.clinica.beta.domain.model.AppointmentDetail
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.OPEN_APPOINTMENT_STATUSES
import mx.crnl.clinica.beta.domain.model.PatientAssignment
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.AppointmentRepository
import mx.crnl.clinica.beta.domain.repository.AuthRepository
import mx.crnl.clinica.beta.domain.repository.ProfessionalAssignmentRepository

/**
 * La cita y solo las acciones que esta persona puede hacer con ella en su estado actual. La política de estados y la
 * de acceso deciden qué se ofrece; el repositorio vuelve a validar cada acción.
 */
data class AppointmentDetailContent(
    val detail: AppointmentDetail,
    val viewer: UserAccount,
    val actions: List<AppointmentAction>,
    val canEdit: Boolean,
    /** Enlace externo listo para abrir; nulo si no hay teléfono utilizable o la cita ya no admite contacto. */
    val contactLink: String?,
    val canRegisterEncounter: Boolean,
    val now: Instant,
)

sealed interface DetailNotice {
    data object WhatsAppUnavailable : DetailNotice

    data class Failed(val error: OperationError) : DetailNotice

    data class Unavailable(val cause: Throwable) : DetailNotice
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val appointmentRepository: AppointmentRepository,
    private val assignmentRepository: ProfessionalAssignmentRepository,
    private val clock: Clock,
) : ViewModel() {
    private val appointmentId = savedStateHandle.toRoute<AppRoute.AppointmentDetail>().appointmentId
    private val reloadCount = MutableStateFlow(0)

    /** Vacío significa que la cita no existe o que esta persona no puede verla. */
    val state: StateFlow<UiState<AppointmentDetailContent>> = reloadCount
        .flatMapLatest {
            authRepository.currentUser
                .filterNotNull()
                .flatMapLatest { viewer ->
                    appointmentRepository.observeAppointment(appointmentId, viewer).flatMapLatest { detail ->
                        if (detail == null) {
                            flowOf(null)
                        } else {
                            assignmentRepository.observeActiveAssignments(detail.summary.patientId, viewer)
                                .map { assignments -> content(detail, viewer, assignments) }
                        }
                    }
                }
                .map<AppointmentDetailContent?, UiState<AppointmentDetailContent>> { content ->
                    if (content == null) UiState.Empty else UiState.Content(content)
                }
                .onStart { emit(UiState.Loading) }
                .catch { emit(UiState.Error(it)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private val _working = MutableStateFlow(false)

    /** Una acción de estado está guardándose: los botones se deshabilitan para evitar el doble toque. */
    val working: StateFlow<Boolean> = _working.asStateFlow()

    private val _notice = MutableStateFlow<DetailNotice?>(null)
    val notice: StateFlow<DetailNotice?> = _notice.asStateFlow()

    fun retry() {
        reloadCount.update { it + 1 }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    /** Aplica programar, confirmar, realizada, no asistió o cancelar sobre el estado que la persona está viendo. */
    fun onAction(action: AppointmentAction, reason: String? = null) {
        val content = (state.value as? UiState.Content)?.data ?: return
        if (_working.value || action !in content.actions || action == AppointmentAction.RESCHEDULE) return
        _working.value = true
        _notice.value = null
        viewModelScope.launch {
            val outcome = runCatchingCancellable {
                appointmentRepository.applyAction(appointmentId, action, content.detail.status, reason, content.viewer.userId)
            }
            _working.value = false
            outcome.fold(
                onSuccess = { result -> if (result is OperationResult.Failure) _notice.value = DetailNotice.Failed(result.error) },
                onFailure = { _notice.value = DetailNotice.Unavailable(it) },
            )
        }
    }

    /** La aplicación externa se abrió: se deja constancia (sin teléfono ni texto). */
    fun onContactOpened() {
        val content = (state.value as? UiState.Content)?.data ?: return
        viewModelScope.launch {
            runCatchingCancellable { appointmentRepository.recordWhatsAppOpened(appointmentId, content.viewer.userId) }
        }
    }

    /** No hay aplicación capaz de abrir el enlace: se avisa sin que la pantalla falle. */
    fun onContactUnavailable() {
        _notice.value = DetailNotice.WhatsAppUnavailable
    }

    private fun content(detail: AppointmentDetail, viewer: UserAccount, assignments: List<PatientAssignment>): AppointmentDetailContent {
        val summary = detail.summary
        val canManage = BetaClinicalAccessPolicy.canManageAppointment(viewer, summary.area, summary.professionalId)
        val assignment = assignments.firstOrNull { it.area == summary.area }
        val canRegister = detail.status == AppointmentStatus.COMPLETED &&
            detail.encounters.isEmpty() &&
            assignment != null &&
            assignment.professionalId == summary.professionalId &&
            BetaClinicalAccessPolicy.canCreateEncounter(viewer, summary.area, assignment.professionalId, assignment.professionalId)
        val link = if (canManage && detail.status in OPEN_APPOINTMENT_STATUSES) {
            WhatsAppLink.create(detail.contactPhone, AppointmentContactMessageBuilder.build(detail.patientFirstName, summary.start))
        } else {
            null
        }
        return AppointmentDetailContent(
            detail = detail,
            viewer = viewer,
            actions = if (canManage) AppointmentStatePolicy.actionsFor(detail.status) else emptyList(),
            canEdit = canManage && AppointmentStatePolicy.canEditAdministrativeData(detail.status),
            contactLink = link,
            canRegisterEncounter = canRegister,
            now = clock.instant(),
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
