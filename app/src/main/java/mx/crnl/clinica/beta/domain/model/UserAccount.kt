package mx.crnl.clinica.beta.domain.model

data class UserAccount(
    val userId: String,
    val firstName: String,
    val paternalSurname: String,
    val maternalSurname: String?,
    val email: String,
    val role: UserRole,
    val area: ClinicalArea?,
    val professionalLicense: String?,
    val status: AccountStatus,
) {
    val fullName: String
        get() = listOfNotNull(firstName, paternalSurname, maternalSurname).joinToString(" ")
}
