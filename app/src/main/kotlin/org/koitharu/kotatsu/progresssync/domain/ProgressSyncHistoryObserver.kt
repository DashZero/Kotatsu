package org.koitharu.kotatsu.progresssync.domain

import androidx.room.InvalidationTracker
import org.koitharu.kotatsu.core.db.TABLE_HISTORY
import org.koitharu.kotatsu.progresssync.work.ProgressSyncWorker
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressSyncHistoryObserver @Inject constructor(
	private val scheduler: ProgressSyncWorker.Scheduler,
) : InvalidationTracker.Observer(TABLE_HISTORY) {

	override fun onInvalidated(tables: Set<String>) {
		scheduler.scheduleDebouncedExport()
	}
}
