package to.kuudere.anisuge.platform

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import okio.Path.Companion.toPath

lateinit var androidAppContext: Context

actual fun createDataStore(): DataStore<Preferences> {
    val file = androidAppContext.filesDir.resolve("session.preferences_pb")
    // filesDir should exist, but be defensive; add corruption handler to survive missing/deleted pb file
    file.parentFile?.mkdirs()
    return PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { file.absolutePath.toPath() }
    )
}
