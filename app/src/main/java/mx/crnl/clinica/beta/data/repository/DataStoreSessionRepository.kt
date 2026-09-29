package mx.crnl.clinica.beta.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import mx.crnl.clinica.beta.domain.repository.SessionRepository

class DataStoreSessionRepository(private val dataStore: DataStore<Preferences>) : SessionRepository {
    override val isSessionActive: Flow<Boolean> = preferences().map { it[SESSION_ACTIVE] ?: false }.distinctUntilChanged()

    override suspend fun startSession() {
        dataStore.edit { it[SESSION_ACTIVE] = true }
    }

    override suspend fun endSession() {
        dataStore.edit { it[SESSION_ACTIVE] = false }
    }

    override suspend fun appliedSeedVersion(): Int = preferences().first()[APPLIED_SEED_VERSION] ?: 0

    override suspend fun recordAppliedSeedVersion(version: Int) {
        dataStore.edit { it[APPLIED_SEED_VERSION] = version }
    }

    private fun preferences(): Flow<Preferences> =
        dataStore.data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    private companion object {
        val SESSION_ACTIVE = booleanPreferencesKey("session_active")
        val APPLIED_SEED_VERSION = intPreferencesKey("applied_seed_version")
    }
}
