package mx.crnl.clinica.beta.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope

/** Preferencias simples de sesión/configuración. Un archivo corrupto se descarta en lugar de tumbar la app. */
fun createSessionDataStore(scope: CoroutineScope, produceFile: () -> File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = produceFile,
    )
