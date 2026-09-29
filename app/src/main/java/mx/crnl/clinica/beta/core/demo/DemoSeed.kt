package mx.crnl.clinica.beta.core.demo

data class DemoSeed(
    val metas: Map<String, SeedMeta>,
    val users: List<SeedUser>,
    val patients: List<SeedPatient>,
    val assignments: List<SeedAssignment>,
    val appointments: List<SeedAppointment>,
    val encounters: List<SeedEncounter>,
    val assessments: List<SeedAssessment>,
) {
    companion object {
        /** Versión del conjunto de datos que esta compilación sabe cargar. */
        const val SUPPORTED_VERSION = 1

        const val USERS_FILE = "seed_users.json"
        const val PATIENTS_FILE = "seed_patients.json"
        const val ASSIGNMENTS_FILE = "seed_assignments.json"
        const val APPOINTMENTS_FILE = "seed_appointments.json"
        const val ENCOUNTERS_FILE = "seed_encounters.json"
        const val ASSESSMENTS_FILE = "seed_assessment_results.json"
    }
}

class SeedException(message: String, cause: Throwable? = null) : Exception(message, cause)
