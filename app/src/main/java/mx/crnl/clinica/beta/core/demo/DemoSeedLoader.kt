package mx.crnl.clinica.beta.core.demo

import java.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class DemoSeedLoader(private val reader: SeedFileReader) {
    // Estricto a propósito: una llave desconocida en un JSON casi siempre es un error de captura.
    private val json = Json { ignoreUnknownKeys = false }

    fun load(): DemoSeed {
        val users = decode(SeedUsersFile.serializer(), DemoSeed.USERS_FILE)
        val credentials = decode(SeedCredentialsFile.serializer(), DemoSeed.CREDENTIALS_FILE)
        val patients = decode(SeedPatientsFile.serializer(), DemoSeed.PATIENTS_FILE)
        val assignments = decode(SeedAssignmentsFile.serializer(), DemoSeed.ASSIGNMENTS_FILE)
        val appointments = decode(SeedAppointmentsFile.serializer(), DemoSeed.APPOINTMENTS_FILE)
        val encounters = decode(SeedEncountersFile.serializer(), DemoSeed.ENCOUNTERS_FILE)
        val assessments = decode(SeedAssessmentsFile.serializer(), DemoSeed.ASSESSMENTS_FILE)
        return DemoSeed(
            metas = mapOf(
                DemoSeed.USERS_FILE to users.meta,
                DemoSeed.CREDENTIALS_FILE to credentials.meta,
                DemoSeed.PATIENTS_FILE to patients.meta,
                DemoSeed.ASSIGNMENTS_FILE to assignments.meta,
                DemoSeed.APPOINTMENTS_FILE to appointments.meta,
                DemoSeed.ENCOUNTERS_FILE to encounters.meta,
                DemoSeed.ASSESSMENTS_FILE to assessments.meta,
            ),
            users = users.users,
            credentials = credentials.credentials,
            patients = patients.patients,
            assignments = assignments.assignments,
            appointments = appointments.appointments,
            encounters = encounters.encounters,
            assessments = assessments.assessments,
        )
    }

    private fun <T> decode(serializer: KSerializer<T>, fileName: String): T =
        try {
            json.decodeFromString(serializer, reader.read(fileName))
        } catch (error: SerializationException) {
            throw SeedException("$fileName: formato inválido (${error.message})", error)
        } catch (error: IOException) {
            throw SeedException("$fileName: no se pudo leer", error)
        }
}
