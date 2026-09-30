package mx.crnl.clinica.beta.domain.clinical

import mx.crnl.clinica.beta.domain.access.BetaClinicalAccessPolicy
import mx.crnl.clinica.beta.domain.access.InterareaAccessPolicy
import mx.crnl.clinica.beta.domain.access.ProfessionalChangePolicy
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.PatientAssignment
import mx.crnl.clinica.beta.domain.model.UserAccount

/** Qué puede hacer una persona con un paciente en un área; las pantallas solo muestran las acciones que sí. */
data class AreaCapabilities(
    /** Puede leer el detalle del área, por su rol o por un acceso temporal de lectura vigente. */
    val canViewDetail: Boolean,
    val canAssign: Boolean,
    val canSchedule: Boolean,
    val canRegisterEncounter: Boolean,
    /** Puede pedir el cambio formal del profesional vigente (nunca lo cambia directamente). */
    val canRequestProfessionalChange: Boolean = false,
    /** No ve el área y puede pedir acceso de lectura a ella. */
    val canRequestAccess: Boolean = false,
)

/**
 * Deriva las acciones visibles de la política de la Beta; los repositorios vuelven a validar cada una al escribir.
 * Un acceso temporal solo amplía la lectura: ninguna acción de escritura depende de él.
 */
object AreaCapabilitiesResolver {
    fun resolve(
        viewer: UserAccount,
        area: ClinicalArea,
        activeAssignment: PatientAssignment?,
        viewableAreas: Set<ClinicalArea> = BetaClinicalAccessPolicy.viewableAreas(viewer),
    ): AreaCapabilities {
        val professionalId = activeAssignment?.professionalId
        val canView = area in viewableAreas
        return AreaCapabilities(
            canViewDetail = canView,
            // Solo la asignación inicial: con un profesional vigente, el cambio va por su solicitud formal.
            canAssign = activeAssignment == null && BetaClinicalAccessPolicy.canCreateAssignment(viewer, area),
            canSchedule = professionalId != null && BetaClinicalAccessPolicy.canManageAppointment(viewer, area, professionalId),
            canRegisterEncounter = professionalId != null &&
                BetaClinicalAccessPolicy.canCreateEncounter(viewer, area, professionalId, professionalId),
            canRequestProfessionalChange = professionalId != null && ProfessionalChangePolicy.canRequest(viewer, area, professionalId),
            canRequestAccess = !canView && InterareaAccessPolicy.canRequest(viewer, area),
        )
    }
}
