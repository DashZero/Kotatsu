package org.koitharu.kotatsu

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import org.koitharu.kotatsu.gdrive.DriveSyncProvider
import org.koitharu.kotatsu.gdrive.SyncRegistry
import javax.inject.Inject

@HiltAndroidApp
class KotatsuApp : Application(), Configuration.Provider {

    @Inject
    lateinit var driveSyncProvider: DriveSyncProvider

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsSyncTrigger: SettingsSyncTrigger

    override fun onCreate() {
        super.onCreate()
        SyncRegistry.register(driveSyncProvider)
        settingsSyncTrigger.register()
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
    }
}
