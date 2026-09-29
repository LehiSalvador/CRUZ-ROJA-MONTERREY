package mx.crnl.clinica.beta.data.repository

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mx.crnl.clinica.beta.core.datastore.createSessionDataStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private class Store(val repository: DataStoreSessionRepository, val job: Job)

    private fun open(file: File): Store {
        val job = SupervisorJob()
        val dataStore = createSessionDataStore(CoroutineScope(Dispatchers.IO + job)) { file }
        return Store(DataStoreSessionRepository(dataStore), job)
    }

    private fun preferencesFile() = File(temporaryFolder.newFolder(), "session.preferences_pb")

    @Test
    fun `sin datos previos no hay sesion ni version aplicada`() = runBlocking {
        val store = open(preferencesFile())
        try {
            assertFalse(store.repository.isSessionActive.first())
            assertEquals(0, store.repository.appliedSeedVersion())
        } finally {
            store.job.cancelAndJoin()
        }
    }

    @Test
    fun `iniciar y cerrar sesion se refleja en el flujo`() = runBlocking {
        val store = open(preferencesFile())
        try {
            store.repository.startSession()
            assertTrue(store.repository.isSessionActive.first())

            store.repository.endSession()
            assertFalse(store.repository.isSessionActive.first())
        } finally {
            store.job.cancelAndJoin()
        }
    }

    @Test
    fun `la sesion y la version aplicada persisten al reabrir el archivo`() = runBlocking {
        val file = preferencesFile()
        val first = open(file)
        first.repository.startSession()
        first.repository.recordAppliedSeedVersion(3)
        first.job.cancelAndJoin()

        val second = open(file)
        try {
            assertTrue(second.repository.isSessionActive.first())
            assertEquals(3, second.repository.appliedSeedVersion())
        } finally {
            second.job.cancelAndJoin()
        }
    }

    @Test
    fun `un archivo dañado se descarta y la app arranca con valores por defecto`() = runBlocking {
        val file = preferencesFile()
        file.writeBytes(byteArrayOf(0x0B, 0x16, 0x21, 0x7F, 0x05, 0x33, 0x01))
        val store = open(file)
        try {
            assertFalse(store.repository.isSessionActive.first())
            assertEquals(0, store.repository.appliedSeedVersion())

            store.repository.startSession()
            assertTrue(store.repository.isSessionActive.first())
        } finally {
            store.job.cancelAndJoin()
        }
    }
}
