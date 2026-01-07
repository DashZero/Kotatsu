package org.koitharu.kotatsu.progresssync

import android.app.Activity
import android.os.Bundle
import org.koitharu.kotatsu.core.ui.DefaultActivityLifecycleCallbacks
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Provider
import org.koitharu.kotatsu.progresssync.work.ProgressSyncScheduleManager

@Singleton
class ProgressSyncInitializer @Inject constructor(
	private val scheduleManagerProvider: Provider<ProgressSyncScheduleManager>,
) : DefaultActivityLifecycleCallbacks {

	private val initialized = AtomicBoolean(false)

	override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
		if (initialized.compareAndSet(false, true)) {
			scheduleManagerProvider.get().init()
		}
	}
}
