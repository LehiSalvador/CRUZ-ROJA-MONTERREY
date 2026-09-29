package mx.crnl.clinica.beta.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mx.crnl.clinica.beta.core.datastore.createSessionDataStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

// Corre bajo Robolectric a propósito: DataStore reemplaza su archivo con File.renameTo, que en una JVM de
// Windows falla si el destino ya existe (en Android, donde vive la app, sí lo reemplaza). Comprobado con
// una sonda: la segunda escritura falla en JVM pura y funciona bajo Robolectric.
@RunWith(AndroidJUnit4::class)
class DataStoreSessionRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private class Store(val dataStore: DataStore<Preferences>, val repository: DataStoreSessionRepository, val job: Job)

    private fun open(file: File): Store {
        val job = SupervisorJob()
        val dataStore = createSessionDataStore(CoroutineScope(Dispatchers.IO + job)) { file }
        return Store(dataStore, DataStoreSessionRepository(dataStore), job)
    }

    private fun preferencesFile() = File(temporaryFolder.newFolder(), "session.preferences_pb")

    @Test
    fun `sin datos previos no hay sesion ni version aplicada`() = runBlocking {
        val store = open(preferencesFile())
        try {
            assertNull(store.repository.sessionUserId.first())
            assertEquals(0, store.repository.appliedSeedVersion())
        } finally {
            store.job.cancelAndJoin()
        }
    }

    @Test
    fun `iniciar y cerrar sesion se refleja en el flujo`() = runBlocking {
        val store = open(preferencesFile())
        try {
            store.repository.startSession("user-1")
            assertEquals("user-1", store.repository.sessionUserId.first())

            store.repository.endSession()
            assertNull(store.repository.sessionUserId.first())
        } finally {
            store.job.cancelAndJoin()
        }
    }

    @Test
    fun `iniciar otra sesion sustituye al usuario anterior`() = runBlocking {
        val store = open(preferencesFile())
        try {
            store.repository.startSession("user-1")
            store.repository.startSession("user-2")

            assertEquals("user-2", store.repository.sessionUserId.first())
        } finally {
            store.job.cancelAndJoin()
        }
    }

    @Test
    fun `la sesion y la version aplicada persisten al reabrir el archivo`() = runBlocking {
        val file = preferencesFile()
        val first = open(file)
        first.repository.startSession("user-1")
        first.repository.recordAppliedSeedVersion(3)
        first.job.cancelAndJoin()

        val second = open(file)
        try {
            assertEquals("user-1", second.repository.sessionUserId.first())
            assertEquals(3, second.repository.appliedSeedVersion())
        } finally {
            second.job.cancelAndJoin()
        }
    }

    @Test
    fun `una sesion activa sin usuario, como la que dejaba la version anterior, no cuenta como sesion`() = runBlocking {
        val store = open(preferencesFile())
        try {
            store.dataStore.edit { it[booleanPreferencesKey("session_active")] = true }

            assertNull(store.repository.sessionUserId.first())
        } finally {
            store.job.cancelAndJoin()
        }
    }

    @Test
    fun `cerrar la sesion no toca la version del conjunto de datos aplicado`() = runBlocking {
        val store = open(preferencesFile())
        try {
            store.repository.recordAppliedSeedVersion(2)
            store.repository.startSession("user-1")

            store.repository.endSession()

            assertEquals(2, store.repository.appliedSeedVersion())
        } finally {
            store.job.cancelAndJoin()
        }
    }

    @Test
    fun `un archivo dañado se descarta y la app arranca con valores por defecto`() = runBlocking {
        val file = preferencesFile()
        file.writeBytes(byteArrayOf(0x0B, 0x16, 0x21, 0x7F, 0x05, 0x33, 0x01))
        val store = open(file)
        try {
            assertNull(store.repository.sessionUserId.first())
            assertEquals(0, store.repository.appliedSeedVersion())

            store.repository.startSession("user-1")
            assertEquals("user-1", store.repository.sessionUserId.first())
        } finally {
            store.job.cancelAndJoin()
        }
    }
}
