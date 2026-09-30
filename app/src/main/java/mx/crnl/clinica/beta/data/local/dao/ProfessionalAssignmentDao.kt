package mx.crnl.clinica.beta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.crnl.clinica.beta.data.local.entity.ProfessionalAssignmentEntity

/** Una asignación con los nombres del profesional y de quien la hizo ya resueltos. */
data class AssignmentRow(
    val assignmentId: String,
    val patientId: String,
    val areaCode: String,
    val professionalId: String,
    val professionalFirstName: String,
    val professionalPaternalSurname: String,
    val startAt: Long,
    val endAt: Long?,
    val status: String,
    val reason: String?,
    val assignedByFirstName: String,
    val assignedByPaternalSurname: String,
    val assignedByMaternalSurname: String?,
)

private const val ASSIGNMENT_SELECT = """
    SELECT a.assignmentId AS assignmentId,
           a.patientId AS patientId,
           a.areaCode AS areaCode,
           a.professionalId AS professionalId,
           u.firstName AS professionalFirstName,
           u.paternalSurname AS professionalPaternalSurname,
           a.startAt AS startAt,
           a.endAt AS endAt,
           a.status AS status,
           a.reason AS reason,
           b.firstName AS assignedByFirstName,
           b.paternalSurname AS assignedByPaternalSurname,
           b.maternalSurname AS assignedByMaternalSurname
    FROM professional_assignments a
    JOIN demo_users u ON u.userId = a.professionalId
    JOIN demo_users b ON b.userId = a.assignedBy
"""

/** Historial de asignaciones: solo se inserta; una asignación cerrada nunca se reescribe. */
@Dao
interface ProfessionalAssignmentDao {
    @Query("$ASSIGNMENT_SELECT WHERE a.patientId = :patientId ORDER BY a.startAt DESC, a.createdAt DESC, a.assignmentId ASC")
    fun observeRows(patientId: String): Flow<List<AssignmentRow>>

    @Query("$ASSIGNMENT_SELECT WHERE a.assignmentId = :assignmentId")
    suspend fun getRow(assignmentId: String): AssignmentRow?

    @Query("$ASSIGNMENT_SELECT WHERE a.patientId = :patientId AND a.areaCode = :areaCode AND a.status = 'ACTIVE' ORDER BY a.startAt DESC LIMIT 1")
    suspend fun getActiveRow(patientId: String, areaCode: String): AssignmentRow?

    @Query("SELECT COUNT(*) FROM professional_assignments WHERE patientId = :patientId AND areaCode = :areaCode AND status = 'ACTIVE'")
    suspend fun countActive(patientId: String, areaCode: String): Int

    /** Asignaciones (vigentes y cerradas) del paciente en el área: indica que el área lo ha atendido. */
    @Query("SELECT COUNT(*) FROM professional_assignments WHERE patientId = :patientId AND areaCode = :areaCode")
    suspend fun countForArea(patientId: String, areaCode: String): Int

    @Insert
    suspend fun insert(assignment: ProfessionalAssignmentEntity)

    /** Cierra una asignación solo si sigue vigente (un cambio de profesional cierra, nunca reescribe); devuelve las filas cambiadas. */
    @Query("UPDATE professional_assignments SET status = 'ENDED', endAt = :endAt WHERE assignmentId = :assignmentId AND status = 'ACTIVE'")
    suspend fun end(assignmentId: String, endAt: Long): Int
}
