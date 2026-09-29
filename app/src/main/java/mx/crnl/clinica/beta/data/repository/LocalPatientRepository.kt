package mx.crnl.clinica.beta.data.repository

import androidx.room.withTransaction
import java.text.Collator
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.core.util.ClinicTime
import mx.crnl.clinica.beta.data.local.dao.AssessmentRow
import mx.crnl.clinica.beta.data.local.dao.AssignmentRow
import mx.crnl.clinica.beta.data.local.dao.EncounterRow
import mx.crnl.clinica.beta.data.local.dao.PatientWithContacts
import mx.crnl.clinica.beta.data.local.entity.PatientContactEntity
import mx.crnl.clinica.beta.data.local.entity.PatientEntity
import mx.crnl.clinica.beta.data.local.mapper.personName
import mx.crnl.clinica.beta.data.local.mapper.toDomain
import mx.crnl.clinica.beta.data.local.mapper.toRecord
import mx.crnl.clinica.beta.domain.model.AppointmentSummary
import mx.crnl.clinica.beta.domain.model.AssessmentStatus
import mx.crnl.clinica.beta.domain.model.AssessmentSummary
import mx.crnl.clinica.beta.domain.model.AuditAction
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.ContactType
import mx.crnl.clinica.beta.domain.model.DuplicateCandidate
import mx.crnl.clinica.beta.domain.model.EncounterStatus
import mx.crnl.clinica.beta.domain.model.EncounterSummary
import mx.crnl.clinica.beta.domain.model.EncounterType
import mx.crnl.clinica.beta.domain.model.Patient
import mx.crnl.clinica.beta.domain.model.PatientAssignment
import mx.crnl.clinica.beta.domain.model.PatientDetail
import mx.crnl.clinica.beta.domain.model.PatientDraft
import mx.crnl.clinica.beta.domain.model.PatientRecord
import mx.crnl.clinica.beta.domain.model.PatientStatus
import mx.crnl.clinica.beta.domain.model.PatientSummary
import mx.crnl.clinica.beta.domain.model.PopulationType
import mx.crnl.clinica.beta.domain.model.RecordStatus
import mx.crnl.clinica.beta.domain.model.Sex
import mx.crnl.clinica.beta.domain.patient.DuplicateDetector
import mx.crnl.clinica.beta.domain.patient.PatientField
import mx.crnl.clinica.beta.domain.patient.PatientNumber
import mx.crnl.clinica.beta.domain.patient.PatientSearch
import mx.crnl.clinica.beta.domain.repository.PatientFilter
import mx.crnl.clinica.beta.domain.repository.PatientNotFoundException
import mx.crnl.clinica.beta.domain.repository.PatientRepository
import mx.crnl.clinica.beta.domain.text.TextNormalizer

class LocalPatientRepository(
    private val database: ClinicalDatabase,
    private val audit: AuditRecorder,
    private val clock: Clock,
    private val idGenerator: () -> String,
) : PatientRepository {
    private val patientDao = database.patientDao()
    private val detailDao = database.patientDetailDao()
    private val appointmentDao = database.appointmentDao()

    override fun observePatients(query: String, filter: PatientFilter): Flow<List<PatientSummary>> =
        patientDao.observeAggregates()
            .map { aggregates ->
                val scoped = aggregates.map { it.toRecord() }.filter { filter.accepts(it) }
                PatientSearch.filter(scoped, query)
                    .sortedWith(byName)
                    .map { record -> PatientSummary(record.patient, record.assignments.map { it.area }.distinct().sorted()) }
            }
            .flowOn(Dispatchers.Default)

    override suspend fun getPatient(patientId: String): Patient? = patientDao.getById(patientId)?.toDomain()

    override fun observePatientDetail(patientId: String): Flow<PatientDetail?> = combine(
        patientDao.observeWithContacts(patientId),
        detailDao.observeActiveAssignments(patientId),
        appointmentDao.observeRowsByPatient(patientId),
        detailDao.observeEncounters(patientId),
        detailDao.observeAssessments(patientId),
    ) { patient, assignments, appointments, encounters, assessments ->
        patient?.toDetail(assignments, appointments.map { it.toDomain() }, encounters, assessments)
    }.flowOn(Dispatchers.Default)

    override suspend fun getPatientDraft(patientId: String): PatientDraft? {
        val patient = patientDao.getById(patientId) ?: return null
        return patient.toDraft(patientDao.getActiveContacts(patientId))
    }

    override suspend fun findDuplicateCandidates(draft: PatientDraft, excludePatientId: String?): List<DuplicateCandidate> {
        val records = patientDao.getAggregates().map { it.toRecord() }
        return withContext(Dispatchers.Default) { DuplicateDetector.detect(draft, records, excludePatientId) }
    }

    override suspend fun createPatient(draft: PatientDraft, actorUserId: String): Patient = database.withTransaction {
        val now = clock.millis()
        val patientId = idGenerator()
        val patient = PatientEntity(
            patientId = patientId,
            patientNumber = PatientNumber.next(patientDao.getAllPatientNumbers()),
            firstName = draft.firstName,
            paternalSurname = draft.paternalSurname,
            maternalSurname = draft.maternalSurname,
            birthDate = draft.birthDate.toString(),
            birthPlace = draft.birthPlace,
            sexCode = draft.sex.name,
            municipality = draft.municipality,
            populationTypeCode = draft.populationType.name,
            status = PatientStatus.ACTIVE.name,
            createdAt = now,
            createdBy = actorUserId,
            updatedAt = now,
            updatedBy = actorUserId,
        )
        patientDao.insert(patient)
        val contacts = listOfNotNull(
            draft.phone?.let { newContact(patientId, ContactType.PHONE, it, now) },
            draft.email?.let { newContact(patientId, ContactType.EMAIL, it, now) },
        )
        if (contacts.isNotEmpty()) patientDao.insertContacts(contacts)
        audit.record(
            AuditAction.PATIENT_CREATED,
            actorUserId = actorUserId,
            entityType = ENTITY_PATIENT,
            entityId = patientId,
            patientId = patientId,
            metadata = mapOf("patientNumber" to AuditRecorder.text(patient.patientNumber)),
        )
        patient.toDomain()
    }

    override suspend fun updatePatient(patientId: String, draft: PatientDraft, actorUserId: String): Patient =
        database.withTransaction {
            val current = patientDao.getById(patientId) ?: throw PatientNotFoundException(patientId)
            val contacts = patientDao.getActiveContacts(patientId)
            val changedFields = changedFields(current.toDraft(contacts), draft)
            if (changedFields.isEmpty()) return@withTransaction current.toDomain()

            val now = clock.millis()
            val updated = current.copy(
                firstName = draft.firstName,
                paternalSurname = draft.paternalSurname,
                maternalSurname = draft.maternalSurname,
                birthDate = draft.birthDate.toString(),
                birthPlace = draft.birthPlace,
                sexCode = draft.sex.name,
                municipality = draft.municipality,
                populationTypeCode = draft.populationType.name,
                updatedAt = now,
                updatedBy = actorUserId,
            )
            patientDao.update(updated)
            syncPrimaryContact(patientId, ContactType.PHONE, draft.phone, contacts, now)
            syncPrimaryContact(patientId, ContactType.EMAIL, draft.email, contacts, now)
            audit.record(
                AuditAction.PATIENT_UPDATED,
                actorUserId = actorUserId,
                entityType = ENTITY_PATIENT,
                entityId = patientId,
                patientId = patientId,
                metadata = mapOf("fields" to AuditRecorder.list(changedFields.map { it.name })),
            )
            updated.toDomain()
        }

    override suspend fun recordPatientViewed(patientId: String, actorUserId: String) {
        audit.record(
            AuditAction.PATIENT_VIEWED,
            actorUserId = actorUserId,
            entityType = ENTITY_PATIENT,
            entityId = patientId,
            patientId = patientId,
        )
    }

    // Un contacto principal que cambia se desactiva y se sustituye por uno nuevo: el valor anterior no se pierde.
    private suspend fun syncPrimaryContact(
        patientId: String,
        type: ContactType,
        newValue: String?,
        active: List<PatientContactEntity>,
        now: Long,
    ) {
        val current = active.firstOrNull { it.contactType == type.name }
        val unchanged = when {
            current == null -> newValue == null
            newValue == null -> false
            else -> sameContact(type, current.contactValue, newValue)
        }
        if (unchanged) return
        current?.let {
            patientDao.updateContact(it.copy(isPrimary = false, status = RecordStatus.INACTIVE.name, updatedAt = now))
        }
        if (newValue != null) patientDao.insertContacts(listOf(newContact(patientId, type, newValue, now)))
    }

    private fun newContact(patientId: String, type: ContactType, value: String, now: Long) = PatientContactEntity(
        contactId = idGenerator(),
        patientId = patientId,
        contactType = type.name,
        contactValue = if (type == ContactType.EMAIL) TextNormalizer.email(value) else TextNormalizer.tidy(value),
        isPrimary = true,
        status = RecordStatus.ACTIVE.name,
        createdAt = now,
        updatedAt = now,
    )

    private fun changedFields(current: PatientDraft, updated: PatientDraft): List<PatientField> = buildList {
        if (current.firstName != updated.firstName) add(PatientField.FIRST_NAME)
        if (current.paternalSurname != updated.paternalSurname) add(PatientField.PATERNAL_SURNAME)
        if (current.maternalSurname != updated.maternalSurname) add(PatientField.MATERNAL_SURNAME)
        if (current.birthDate != updated.birthDate) add(PatientField.BIRTH_DATE)
        if (current.birthPlace != updated.birthPlace) add(PatientField.BIRTH_PLACE)
        if (current.sex != updated.sex) add(PatientField.SEX)
        if (current.municipality != updated.municipality) add(PatientField.MUNICIPALITY)
        if (current.populationType != updated.populationType) add(PatientField.POPULATION_TYPE)
        if (!sameOptionalContact(ContactType.PHONE, current.phone, updated.phone)) add(PatientField.PHONE)
        if (!sameOptionalContact(ContactType.EMAIL, current.email, updated.email)) add(PatientField.EMAIL)
    }

    private fun sameOptionalContact(type: ContactType, first: String?, second: String?): Boolean =
        if (first == null || second == null) first == second else sameContact(type, first, second)

    private fun sameContact(type: ContactType, first: String, second: String): Boolean = when (type) {
        ContactType.PHONE -> TextNormalizer.phoneDigits(first) == TextNormalizer.phoneDigits(second)
        ContactType.EMAIL -> TextNormalizer.email(first) == TextNormalizer.email(second)
    }

    private fun PatientEntity.toDraft(contacts: List<PatientContactEntity>) = PatientDraft(
        firstName = firstName,
        paternalSurname = paternalSurname,
        maternalSurname = maternalSurname,
        birthDate = LocalDate.parse(birthDate),
        birthPlace = birthPlace,
        sex = Sex.valueOf(sexCode),
        municipality = municipality,
        populationType = PopulationType.valueOf(populationTypeCode),
        phone = contacts.firstOrNull { it.contactType == ContactType.PHONE.name }?.contactValue,
        email = contacts.firstOrNull { it.contactType == ContactType.EMAIL.name }?.contactValue,
    )

    private fun PatientWithContacts.toDetail(
        assignments: List<AssignmentRow>,
        appointments: List<AppointmentSummary>,
        encounters: List<EncounterRow>,
        assessments: List<AssessmentRow>,
    ) = PatientDetail(
        patient = patient.toDomain(),
        contacts = contacts
            .filter { it.status == RecordStatus.ACTIVE.name }
            .sortedWith(
                compareByDescending<PatientContactEntity> { it.isPrimary }
                    .thenBy { ContactType.valueOf(it.contactType) }
                    .thenBy { it.createdAt }
                    .thenBy { it.contactId },
            )
            .map { it.toDomain() },
        assignments = assignments
            .map {
                PatientAssignment(
                    area = ClinicalArea.valueOf(it.areaCode),
                    professionalName = personName(it.professionalFirstName, it.professionalPaternalSurname, null),
                    since = Instant.ofEpochMilli(it.startAt),
                )
            }
            .sortedBy { it.area },
        appointments = appointments,
        encounters = encounters.map {
            EncounterSummary(
                encounterId = it.encounterId,
                area = ClinicalArea.valueOf(it.areaCode),
                professionalName = personName(it.professionalFirstName, it.professionalPaternalSurname, null),
                type = EncounterType.valueOf(it.encounterTypeCode),
                status = EncounterStatus.valueOf(it.status),
                eventAt = Instant.ofEpochMilli(it.eventAt),
            )
        },
        assessments = assessments.map {
            AssessmentSummary(
                assessmentId = it.assessmentId,
                area = it.areaCode?.let(ClinicalArea::valueOf),
                professionalName = personName(it.professionalFirstName, it.professionalPaternalSurname, null),
                status = AssessmentStatus.valueOf(it.status),
                startedAt = Instant.ofEpochMilli(it.startedAt),
                hasResult = it.resultId != null,
                classificationLabel = if (it.classificationCode == UNDEFINED_CLASSIFICATION) null else it.classificationLabel,
            )
        },
    )

    private fun PatientFilter.accepts(record: PatientRecord): Boolean = when (this) {
        PatientFilter.All -> true
        is PatientFilter.AssignedTo -> record.assignments.any { it.professionalId == professionalId }
    }

    private companion object {
        const val ENTITY_PATIENT = "PATIENT"
        const val UNDEFINED_CLASSIFICATION = "TBD"

        private val collator: Collator = Collator.getInstance(ClinicTime.locale).apply { strength = Collator.PRIMARY }

        val byName: Comparator<PatientRecord> = Comparator { first, second ->
            compareValuesBy(
                first,
                second,
                { collator.getCollationKey(it.patient.paternalSurname) },
                { collator.getCollationKey(it.patient.maternalSurname.orEmpty()) },
                { collator.getCollationKey(it.patient.firstName) },
            )
        }
    }
}
