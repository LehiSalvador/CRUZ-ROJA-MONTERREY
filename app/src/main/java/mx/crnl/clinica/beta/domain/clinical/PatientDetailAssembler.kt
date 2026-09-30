package mx.crnl.clinica.beta.domain.clinical

import mx.crnl.clinica.beta.domain.access.BetaClinicalAccessPolicy
import mx.crnl.clinica.beta.domain.model.AccessGrant
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.AreaActivity
import mx.crnl.clinica.beta.domain.model.AssessmentSummary
import mx.crnl.clinica.beta.domain.model.AssignmentStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.EncounterSummary
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.model.PatientAssignment
import mx.crnl.clinica.beta.domain.model.PatientContact
import mx.crnl.clinica.beta.domain.model.PatientDetail
import mx.crnl.clinica.beta.domain.model.UserAccount

/**
 * Arma el expediente que una persona puede ver a partir de todo lo registrado. El filtrado por área ocurre aquí, en
 * el dominio, para que ninguna pantalla pueda mostrar de más: de las áreas ajenas solo sale [AreaActivity], salvo las
 * que un acceso temporal de lectura vigente ([grants], ya filtradas por vigencia) abre en modo lectura.
 */
object PatientDetailAssembler {
    fun assemble(
        viewer: UserAccount,
        patient: Patient,
        contacts: List<PatientContact>,
        assignments: List<PatientAssignment>,
        appointments: List<AppointmentSummary>,
        encounters: List<EncounterSummary>,
        assessments: List<AssessmentSummary>,
        grants: Map<ClinicalArea, AccessGrant> = emptyMap(),
        pendingAccessRequests: Map<ClinicalArea, String> = emptyMap(),
        pendingChangeRequests: Map<ClinicalArea, String> = emptyMap(),
    ): PatientDetail {
        val byRole = BetaClinicalAccessPolicy.viewableAreas(viewer)
        val granted = grants.filterKeys { it !in byRole }
        val viewable = byRole + granted.keys
        val seesEverything = viewable.containsAll(ClinicalArea.entries)

        val visibleAssignments = assignments.filter { it.area in viewable }
        return PatientDetail(
            patient = patient,
            contacts = contacts,
            assignments = visibleAssignments.filter { it.status == AssignmentStatus.ACTIVE }.sortedBy { it.area },
            appointments = appointments.filter { it.area in viewable },
            encounters = encounters.filter { it.area in viewable }.sortedByDescending { it.eventAt },
            // Una evaluación sin área no se puede atribuir a nadie: solo quien ve todas las áreas la recibe.
            assessments = assessments.filter { assessment -> assessment.area?.let { it in viewable } ?: seesEverything },
            assignmentHistory = visibleAssignments.sortedWith(compareByDescending<PatientAssignment> { it.since }.thenBy { it.area }),
            viewableAreas = viewable,
            restrictedAreas = restricted(viewable, assignments, encounters),
            grantedAreas = granted.mapValues { it.value.expiresAt },
            pendingAccessRequests = pendingAccessRequests.filterKeys { it !in viewable },
            pendingChangeRequests = pendingChangeRequests.filterKeys { it in viewable },
        )
    }

    private fun restricted(
        viewable: Set<ClinicalArea>,
        assignments: List<PatientAssignment>,
        encounters: List<EncounterSummary>,
    ): List<AreaActivity> = ClinicalArea.entries
        .filter { it !in viewable }
        .mapNotNull { area ->
            val areaEncounters = encounters.filter { it.area == area }
            if (areaEncounters.isEmpty() && assignments.none { it.area == area }) {
                null
            } else {
                AreaActivity(area, areaEncounters.size, areaEncounters.maxOfOrNull { it.eventAt })
            }
        }
}
