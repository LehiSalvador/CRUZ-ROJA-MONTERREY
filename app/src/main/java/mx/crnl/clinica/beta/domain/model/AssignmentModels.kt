package mx.crnl.clinica.beta.domain.model

/** Profesional que se puede elegir al asignar un paciente: cuenta activa del área. */
data class ProfessionalOption(
    val userId: String,
    val fullName: String,
    val professionalLicense: String?,
    val area: ClinicalArea,
)
