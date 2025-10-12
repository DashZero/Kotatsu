package org.koitharu.kotatsu.gdrive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.gdrive.models.SyncEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsSyncTrigger @Inject constructor(
    private val appSettings: AppSettings,
    private val syncRegistry: SyncRegistry
) {

    fun register() {
        appSettings.observeChanges()
            .onEach { key ->
                key?.let { syncRegistry.sendEvent(SyncEvent.SettingsChanged(it)) }
            }
            .launchIn(CoroutineScope(Dispatchers.Default))
    }
}
