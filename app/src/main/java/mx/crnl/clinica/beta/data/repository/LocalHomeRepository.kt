package mx.crnl.clinica.beta.data.repository

import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import mx.crnl.clinica.beta.core.database.ClinicalDatabase
import mx.crnl.clinica.beta.data.local.mapper.toDomain
import mx.crnl.clinica.beta.data.local.mapper.toRecord
import mx.crnl.clinica.beta.domain.home.HomeSummary
import mx.crnl.clinica.beta.domain.home.HomeSummaryBuilder
import mx.crnl.clinica.beta.domain.home.PendingAccessRequest
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.repository.HomeRepository

class LocalHomeRepository(database: ClinicalDatabase, private val clock: Clock) : HomeRepository {
    private val patientDao = database.patientDao()
    private val appointmentDao = database.appointmentDao()
    private val accessRequestDao = database.accessRequestDao()

    override fun observeSummary(user: UserAccount): Flow<HomeSummary> = combine(
        patientDao.observeAggregates(),
        appointmentDao.observeRows(),
        accessRequestDao.observePending(),
    ) { patients, appointments, pending ->
        HomeSummaryBuilder.build(
            user = user,
            patients = patients.map { it.toRecord() },
            appointments = appointments.map { it.toDomain() },
            pendingRequests = pending.map { request ->
                PendingAccessRequest(request.requesterUserId, ClinicalArea.entries.firstOrNull { it.name == request.ownerAreaCode })
            },
            now = clock.instant(),
        )
    }.flowOn(Dispatchers.Default)
}
