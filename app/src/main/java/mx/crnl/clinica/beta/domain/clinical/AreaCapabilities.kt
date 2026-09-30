package mx.crnl.clinica.beta.domain.clinical

import mx.crnl.clinica.beta.domain.access.BetaClinicalAccessPolicy
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.PatientAssignment
import mx.crnl.clinica.beta.domain.model.UserAccount

/** Qué puede hacer una persona con un paciente en un área; las pantallas solo muestran las acciones que sí. */
data class AreaCapabilities(
    val canViewDetail: Boolean,
    val canAssign: Boolean,
    val canSchedule: Boolean,
    val canRegisterEncounter: Boolean,
)

/** Deriva las acciones visibles de la política de la Beta; los repositorios vuelven a validar cada una al escribir. */
object AreaCapabilitiesResolver {
    fun resolve(viewer: UserAccount, area: ClinicalArea, activeAssignment: PatientAssignment?): AreaCapabilities {
        val professionalId = activeAssignment?.professionalId
        return AreaCapabilities(
            canViewDetail = BetaClinicalAccessPolicy.canViewAreaDetail(viewer, area),
            // Solo la asignación inicial: con un profesional vigente, cambiarlo no está habilitado.
            canAssign = activeAssignment == null && BetaClinicalAccessPolicy.canCreateAssignment(viewer, area),
            canSchedule = professionalId != null && BetaClinicalAccessPolicy.canManageAppointment(viewer, area, professionalId),
            canRegisterEncounter = professionalId != null &&
                BetaClinicalAccessPolicy.canCreateEncounter(viewer, area, professionalId, professionalId),
        )
    }
}
