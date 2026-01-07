package org.koitharu.kotatsu.progresssync.work

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.util.ext.processLifecycleScope
import org.koitharu.kotatsu.progresssync.prefs.ProgressSyncMode
import org.koitharu.kotatsu.progresssync.prefs.ProgressSyncSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressSyncScheduleManager @Inject constructor(
	private val settings: ProgressSyncSettings,
	private val scheduler: ProgressSyncWorker.Scheduler,
) : SharedPreferences.OnSharedPreferenceChangeListener {

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		when (key) {
			ProgressSyncSettings.KEY_PROGRESS_SYNC_ENABLED,
			ProgressSyncSettings.KEY_PROGRESS_SYNC_MODE,
			ProgressSyncSettings.KEY_PROGRESS_SYNC_INTERVAL,
			ProgressSyncSettings.KEY_PROGRESS_SYNC_DIRECTORY -> updateWorker(
				isEnabled = shouldSchedule(),
				force = key != ProgressSyncSettings.KEY_PROGRESS_SYNC_ENABLED,
			)
		}
	}

	fun init() {
		settings.subscribe(this)
		processLifecycleScope.launch(Dispatchers.Default) {
			updateWorkerImpl(isEnabled = shouldSchedule(), force = true)
		}
	}

	private fun updateWorker(isEnabled: Boolean, force: Boolean) {
		processLifecycleScope.launch(Dispatchers.Default) {
			updateWorkerImpl(isEnabled, force)
		}
	}

	private suspend fun updateWorkerImpl(isEnabled: Boolean, force: Boolean) {
		if (force || scheduler.isScheduled() != isEnabled) {
			if (isEnabled) {
				scheduler.schedule()
			} else {
				scheduler.unschedule()
			}
		}
	}

	private fun shouldSchedule(): Boolean {
		return settings.isEnabled &&
			settings.directory != null &&
			settings.mode == ProgressSyncMode.AUTOMATIC
	}
}
