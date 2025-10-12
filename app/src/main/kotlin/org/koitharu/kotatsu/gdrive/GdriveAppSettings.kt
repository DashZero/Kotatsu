package org.koitharu.kotatsu.gdrive

import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.data.IAppSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GdriveAppSettings @Inject constructor(
    private val appSettings: AppSettings
) : IAppSettings {

    override suspend fun getCurrentSettings(): Map<String, String> {
        return appSettings.getAllValues().mapValues { it.value.toString() }
    }

    override suspend fun setSetting(key: String, value: String) {
        // This function is for updating from the sync, not for local updates.
    }
}
