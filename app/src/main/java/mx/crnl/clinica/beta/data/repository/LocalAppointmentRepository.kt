package mx.crnl.clinica.beta.data.repository

import androidx.room.withTransaction
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.data.local.entity.AppointmentEntity
import mx.crnl.clinica.beta.data.local.mapper.toDomain
import mx.crnl.clinica.beta.data.local.mapper.toSummary
import mx.crnl.clinica.beta.domain.access.BetaClinicalAccessPolicy
import mx.crnl.clinica.beta.domain.appointment.AppointmentAction
import mx.crnl.clinica.beta.domain.appointment.AppointmentConflictDetector
import mx.crnl.clinica.beta.domain.appointment.AppointmentField
import mx.crnl.clinica.beta.domain.appointment.AppointmentIssue
import mx.crnl.clinica.beta.domain.appointment.AppointmentRules
import mx.crnl.clinica.beta.domain.appointment.AppointmentStatePolicy
import mx.crnl.clinica.beta.domain.appointment.ScheduleSlot
import mx.crnl.clinica.beta.domain.common.EntityKind
import mx.crnl.clinica.beta.domain.common.OperationError
import mx.crnl.clinica.beta.domain.common.OperationResult
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.AppointmentAdminUpdate
import mx.crnl.clinica.beta.domain.model.AppointmentDetail
import mx.crnl.clinica.beta.domain.model.AppointmentDraft
import mx.crnl.clinica.beta.domain.model.AppointmentModality
import mx.crnl.clinica.beta.domain.model.AppointmentReschedule
import mx.crnl.clinica.beta.domain.model.AppointmentStatus
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.AuditAction
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.OPEN_APPOINTMENT_STATUSES
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.ScheduleConflict
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole
import mx.crnl.clinica.beta.domain.repository.AppointmentRepository
import mx.crnl.clinica.beta.domain.text.TextNormalizer

class LocalAppointmentRepository(
    private val database: ClinicalDatabase,
    private val audit: AuditRecorder,
    private val clock: Clock,
    private val idGenerator: () -> String,
) : AppointmentRepository {
    private val appointmentDao = database.appointmentDao()
    private val assignmentDao = database.assignmentDao()
    private val encounterDao = database.encounterDao()
    private val patientDao = database.patientDao()
    private val userDao = database.userDao()

    // ---------------------------------------------------------------- lecturas

    override fun observeAppointments(viewer: UserAccount): Flow<List<AppointmentSummary>> {
        val scope = BetaClinicalAccessPolicy.appointmentScope(viewer)
        return appointmentDao.observeRows()
            .map { rows -> rows.map { it.toDomain() }.filter { scope.accepts(it.area, it.professionalId) } }
            .flowOn(Dispatchers.Default)
    }

    override fun observePatientAppointments(patientId: String, viewer: UserAccount): Flow<List<AppointmentSummary>> {
        val viewable = BetaClinicalAccessPolicy.viewableAreas(viewer)
        return appointmentDao.observeRowsByPatient(patientId)
            .map { rows -> rows.map { it.toDomain() }.filter { it.area in viewable } }
            .flowOn(Dispatchers.Default)
    }

    override fun observeAppointment(appointmentId: String, viewer: UserAccount): Flow<AppointmentDetail?> = combine(
        appointmentDao.observeDetailRow(appointmentId),
        encounterDao.observeRowsByAppointment(appointmentId),
    ) { row, encounters ->
        row
            ?.takeIf { BetaClinicalAccessPolicy.canViewAreaDetail(viewer, ClinicalArea.valueOf(it.areaCode)) }
            ?.toDomain(encounters.map { it.toSummary() })
    }.flowOn(Dispatchers.Default)

    override suspend fun getAppointment(appointmentId: String, viewer: UserAccount): AppointmentDetail? =
        observeAppointment(appointmentId, viewer).first()

    override suspend fun getNextAppointment(patientId: String, viewer: UserAccount): AppointmentSummary? {
        val now = clock.instant()
        return observePatientAppointments(patientId, viewer).first()
            .filter { it.status in OPEN_APPOINTMENT_STATUSES && it.end >= now }
            .minByOrNull { it.start }
    }

    // ---------------------------------------------------------------- escrituras

    override suspend fun createAppointment(draft: AppointmentDraft, actorUserId: String): OperationResult<AppointmentDetail> =
        database.withTransaction {
            val actor = actor(actorUserId)
            if (actor == null || !BetaClinicalAccessPolicy.canManageAppointment(actor, draft.area, draft.professionalId)) {
                return@withTransaction failure(OperationError.NotAuthorized)
            }
            val patient = patientDao.getById(draft.patientId) ?: return@withTransaction failure(OperationError.NotFound(EntityKind.PATIENT))
            if (patient.status != PatientStatus.ACTIVE.name) return@withTransaction failure(OperationError.PatientNotActive)

            val assigned = assignmentDao.getActiveRow(draft.patientId, draft.area.name)
                ?: return@withTransaction failure(OperationError.NoActiveAssignment)
            if (assigned.professionalId != draft.professionalId) return@withTransaction failure(OperationError.ProfessionalNotAssigned)
            if (!isAvailableProfessional(draft.professionalId, draft.area)) {
                return@withTransaction failure(OperationError.ProfessionalNotAvailable)
            }

            val now = clock.instant()
            val issues = AppointmentRules.scheduleIssues(draft.start, draft.durationMinutes, now) +
                AppointmentRules.logisticsIssues(draft.modality, draft.location, draft.meetingUrl, draft.administrativeNotes)
            if (issues.isNotEmpty()) return@withTransaction failure(OperationError.InvalidAppointment(issues))

            val end = draft.start.plusSeconds(draft.durationMinutes * SECONDS_PER_MINUTE)
            val conflicts = conflictsFor(actor, draft.patientId, draft.professionalId, draft.start, end, excludeAppointmentId = null)
            if (conflicts.isNotEmpty()) return@withTransaction failure(OperationError.ScheduleConflicts(conflicts))

            val logistics = AppointmentRules.normalizeLogistics(draft.modality, draft.location, draft.meetingUrl, draft.administrativeNotes)
            val appointmentId = idGenerator()
            appointmentDao.insert(
                AppointmentEntity(
                    appointmentId = appointmentId,
                    patientId = draft.patientId,
                    areaCode = draft.area.name,
                    professionalId = draft.professionalId,
                    startDateTime = draft.start.toEpochMilli(),
                    endDateTime = end.toEpochMilli(),
                    modality = logistics.modality.name,
                    location = logistics.location,
                    meetingUrl = logistics.meetingUrl,
                    status = AppointmentStatus.SCHEDULED.name,
                    administrativeNotes = logistics.administrativeNotes,
                    createdBy = actor.userId,
                    createdAt = now.toEpochMilli(),
                    updatedAt = now.toEpochMilli(),
                ),
            )
            audit.record(
                AuditAction.APPOINTMENT_CREATED,
                actorUserId = actor.userId,
                entityType = ENTITY_APPOINTMENT,
                entityId = appointmentId,
                patientId = draft.patientId,
                areaCode = draft.area.name,
                metadata = mapOf(
                    "professionalId" to AuditRecorder.text(draft.professionalId),
                    "status" to AuditRecorder.text(AppointmentStatus.SCHEDULED.name),
                    "start" to AuditRecorder.text(draft.start.toString()),
                    "modality" to AuditRecorder.text(logistics.modality.name),
                ),
            )
            success(detail(appointmentId))
        }

    override suspend fun updateAdministrativeData(
        appointmentId: String,
        update: AppointmentAdminUpdate,
        actorUserId: String,
    ): OperationResult<AppointmentDetail> = database.withTransaction {
        val (actor, current) = when (val loaded = load(appointmentId, actorUserId)) {
            is Loaded.Denied -> return@withTransaction failure(loaded.error)
            is Loaded.Ok -> loaded.actor to loaded.appointment
        }
        if (!AppointmentStatePolicy.canEditAdministrativeData(current.status())) {
            return@withTransaction failure(OperationError.NotEditable(current.status()))
        }
        val issues = AppointmentRules.logisticsIssues(update.modality, update.location, update.meetingUrl, update.administrativeNotes)
        if (issues.isNotEmpty()) return@withTransaction failure(OperationError.InvalidAppointment(issues))

        val logistics = AppointmentRules.normalizeLogistics(update.modality, update.location, update.meetingUrl, update.administrativeNotes)
        val changed = buildList {
            if (current.modality != logistics.modality.name) add("MODALITY")
            if (current.location != logistics.location) add("LOCATION")
            if (current.meetingUrl != logistics.meetingUrl) add("MEETING_URL")
            if (current.administrativeNotes != logistics.administrativeNotes) add("NOTES")
        }
        if (changed.isEmpty()) return@withTransaction success(detail(appointmentId))

        appointmentDao.update(
            current.copy(
                modality = logistics.modality.name,
                location = logistics.location,
                meetingUrl = logistics.meetingUrl,
                administrativeNotes = logistics.administrativeNotes,
                updatedAt = clock.millis(),
            ),
        )
        audit.record(
            AuditAction.APPOINTMENT_UPDATED,
            actorUserId = actor.userId,
            entityType = ENTITY_APPOINTMENT,
            entityId = appointmentId,
            patientId = current.patientId,
            areaCode = current.areaCode,
            metadata = mapOf("fields" to AuditRecorder.list(changed)),
        )
        success(detail(appointmentId))
    }

    override suspend fun reschedule(
        appointmentId: String,
        schedule: AppointmentReschedule,
        expectedStatus: AppointmentStatus,
        actorUserId: String,
    ): OperationResult<AppointmentDetail> = database.withTransaction {
        val (actor, current) = when (val loaded = load(appointmentId, actorUserId)) {
            is Loaded.Denied -> return@withTransaction failure(loaded.error)
            is Loaded.Ok -> loaded.actor to loaded.appointment
        }
        checkTransition(current, expectedStatus, AppointmentAction.RESCHEDULE)?.let { return@withTransaction failure(it) }

        val now = clock.instant()
        val issues = AppointmentRules.scheduleIssues(schedule.start, schedule.durationMinutes, now)
        if (issues.isNotEmpty()) return@withTransaction failure(OperationError.InvalidAppointment(issues))

        val end = schedule.start.plusSeconds(schedule.durationMinutes * SECONDS_PER_MINUTE)
        val conflicts = conflictsFor(actor, current.patientId, current.professionalId, schedule.start, end, excludeAppointmentId = appointmentId)
        if (conflicts.isNotEmpty()) return@withTransaction failure(OperationError.ScheduleConflicts(conflicts))

        appointmentDao.update(
            current.copy(
                startDateTime = schedule.start.toEpochMilli(),
                endDateTime = end.toEpochMilli(),
                status = AppointmentStatus.RESCHEDULED.name,
                updatedAt = now.toEpochMilli(),
            ),
        )
        audit.record(
            AuditAction.APPOINTMENT_RESCHEDULED,
            actorUserId = actor.userId,
            entityType = ENTITY_APPOINTMENT,
            entityId = appointmentId,
            patientId = current.patientId,
            areaCode = current.areaCode,
            metadata = mapOf(
                "fromStatus" to AuditRecorder.text(current.status),
                "fromStart" to AuditRecorder.text(Instant.ofEpochMilli(current.startDateTime).toString()),
                "toStart" to AuditRecorder.text(schedule.start.toString()),
                "toEnd" to AuditRecorder.text(end.toString()),
            ),
        )
        success(detail(appointmentId))
    }

    override suspend fun applyAction(
        appointmentId: String,
        action: AppointmentAction,
        expectedStatus: AppointmentStatus,
        reason: String?,
        actorUserId: String,
    ): OperationResult<AppointmentDetail> = database.withTransaction {
        val (actor, current) = when (val loaded = load(appointmentId, actorUserId)) {
            is Loaded.Denied -> return@withTransaction failure(loaded.error)
            is Loaded.Ok -> loaded.actor to loaded.appointment
        }
        // Reprogramar mueve la cita y tiene su propia operación: por aquí no se acepta.
        if (action == AppointmentAction.RESCHEDULE) {
            return@withTransaction failure(OperationError.InvalidTransition(current.status(), action))
        }
        checkTransition(current, expectedStatus, action)?.let { return@withTransaction failure(it) }

        val target = AppointmentStatePolicy.targetOf(action)
        val cleanReason = if (action == AppointmentAction.CANCEL) TextNormalizer.tidy(reason.orEmpty()).ifEmpty { null } else null
        if (cleanReason != null && cleanReason.length > MAX_REASON_LENGTH) {
            return@withTransaction failure(OperationError.InvalidAppointment(mapOf(AppointmentField.NOTES to AppointmentIssue.TOO_LONG)))
        }
        // La razón administrativa de una cancelación queda junto a las notas de la cita; la bitácora solo sabe que se dio.
        val notes = when {
            cleanReason == null -> current.administrativeNotes
            current.administrativeNotes.isNullOrEmpty() -> "$CANCELLATION_PREFIX$cleanReason"
            else -> "${current.administrativeNotes}\n$CANCELLATION_PREFIX$cleanReason"
        }
        appointmentDao.update(current.copy(status = target.name, administrativeNotes = notes, updatedAt = clock.millis()))
        audit.record(
            action.auditAction(),
            actorUserId = actor.userId,
            entityType = ENTITY_APPOINTMENT,
            entityId = appointmentId,
            patientId = current.patientId,
            areaCode = current.areaCode,
            metadata = buildMap {
                put("from", AuditRecorder.text(current.status))
                put("to", AuditRecorder.text(target.name))
                if (action == AppointmentAction.CANCEL) put("reasonGiven", AuditRecorder.flag(cleanReason != null))
            },
        )
        success(detail(appointmentId))
    }

    override suspend fun recordWhatsAppOpened(appointmentId: String, actorUserId: String): OperationResult<Unit> =
        database.withTransaction {
            val (actor, current) = when (val loaded = load(appointmentId, actorUserId)) {
            is Loaded.Denied -> return@withTransaction failure(loaded.error)
            is Loaded.Ok -> loaded.actor to loaded.appointment
        }
            audit.record(
                AuditAction.APPOINTMENT_WHATSAPP_OPENED,
                actorUserId = actor.userId,
                entityType = ENTITY_APPOINTMENT,
                entityId = appointmentId,
                patientId = current.patientId,
                areaCode = current.areaCode,
            )
            success(Unit)
        }

    // ---------------------------------------------------------------- apoyo

    private suspend fun actor(actorUserId: String): UserAccount? = userDao.getById(actorUserId)?.toDomain()

    private sealed interface Loaded {
        data class Ok(val actor: UserAccount, val appointment: AppointmentEntity) : Loaded

        data class Denied(val error: OperationError) : Loaded
    }

    /** La cita y quien actúa, ya autorizado para gestionarla; o el error que corresponde. */
    private suspend fun load(appointmentId: String, actorUserId: String): Loaded {
        val actor = actor(actorUserId) ?: return Loaded.Denied(OperationError.NotAuthorized)
        val current = appointmentDao.getById(appointmentId) ?: return Loaded.Denied(OperationError.NotFound(EntityKind.APPOINTMENT))
        val area = ClinicalArea.valueOf(current.areaCode)
        if (!BetaClinicalAccessPolicy.canManageAppointment(actor, area, current.professionalId)) {
            return Loaded.Denied(OperationError.NotAuthorized)
        }
        return Loaded.Ok(actor, current)
    }

    /** El estado que la persona veía debe seguir vigente y la acción debe estar permitida desde él. */
    private fun checkTransition(current: AppointmentEntity, expected: AppointmentStatus, action: AppointmentAction): OperationError? {
        val status = current.status()
        return when {
            status != expected -> OperationError.StatusChanged(status)
            !AppointmentStatePolicy.canApply(action, status) -> OperationError.InvalidTransition(status, action)
            else -> null
        }
    }

    private suspend fun isAvailableProfessional(professionalId: String, area: ClinicalArea): Boolean {
        val professional = userDao.getById(professionalId) ?: return false
        return professional.status == AccountStatus.ACTIVE.name &&
            professional.roleCode == UserRole.PROFESSIONAL.name &&
            professional.areaCode == area.name
    }

    private suspend fun conflictsFor(
        actor: UserAccount,
        patientId: String,
        professionalId: String,
        start: Instant,
        end: Instant,
        excludeAppointmentId: String?,
    ): List<ScheduleConflict> {
        val candidates = appointmentDao.findOverlapping(patientId, professionalId, start.toEpochMilli(), end.toEpochMilli())
        val slots = candidates.map {
            ScheduleSlot(
                appointmentId = it.appointmentId,
                patientId = it.patientId,
                professionalId = it.professionalId,
                start = Instant.ofEpochMilli(it.startDateTime),
                end = Instant.ofEpochMilli(it.endDateTime),
                status = AppointmentStatus.valueOf(it.status),
            )
        }
        return AppointmentConflictDetector.detect(patientId, professionalId, start, end, slots, excludeAppointmentId).map { conflict ->
            val row = appointmentDao.getListRow(conflict.slot.appointmentId)?.toDomain()
            // Una cita de un área que la persona no ve solo revela que ese horario está ocupado.
            val visible = row != null && BetaClinicalAccessPolicy.canViewAreaDetail(actor, row.area)
            ScheduleConflict(
                kind = conflict.kind,
                start = conflict.slot.start,
                end = conflict.slot.end,
                appointmentId = row?.appointmentId.takeIf { visible },
                patientName = row?.patientName.takeIf { visible },
                professionalName = row?.professionalName.takeIf { visible },
            )
        }
    }

    private suspend fun detail(appointmentId: String): AppointmentDetail {
        val row = requireNotNull(appointmentDao.getDetailRow(appointmentId)) { "La cita $appointmentId debería existir" }
        val encounters = encounterDao.observeRowsByAppointment(appointmentId).first().map { it.toSummary() }
        return row.toDomain(encounters)
    }

    private fun AppointmentEntity.status(): AppointmentStatus = AppointmentStatus.valueOf(status)

    private fun AppointmentAction.auditAction(): AuditAction = when (this) {
        AppointmentAction.SCHEDULE -> AuditAction.APPOINTMENT_SCHEDULED
        AppointmentAction.CONFIRM -> AuditAction.APPOINTMENT_CONFIRMED
        AppointmentAction.RESCHEDULE -> AuditAction.APPOINTMENT_RESCHEDULED
        AppointmentAction.COMPLETE -> AuditAction.APPOINTMENT_COMPLETED
        AppointmentAction.MARK_NO_SHOW -> AuditAction.APPOINTMENT_NO_SHOW
        AppointmentAction.CANCEL -> AuditAction.APPOINTMENT_CANCELLED
    }

    private fun <T> success(value: T): OperationResult<T> = OperationResult.Success(value)

    private fun failure(error: OperationError): OperationResult<Nothing> = OperationResult.Failure(error)

    private companion object {
        const val ENTITY_APPOINTMENT = "APPOINTMENT"
        const val SECONDS_PER_MINUTE = 60L
        const val MAX_REASON_LENGTH = 200
        const val CANCELLATION_PREFIX = "Cancelación: "
    }
}
