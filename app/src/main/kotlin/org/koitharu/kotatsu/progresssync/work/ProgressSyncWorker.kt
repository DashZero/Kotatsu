package org.koitharu.kotatsu.progresssync.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import org.koitharu.kotatsu.core.util.ext.awaitUniqueWorkInfoByName
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.progresssync.data.ProgressSyncRepository
import org.koitharu.kotatsu.progresssync.prefs.ProgressSyncMode
import org.koitharu.kotatsu.progresssync.prefs.ProgressSyncSettings
import org.koitharu.kotatsu.settings.work.PeriodicWorkScheduler
import java.io.FileNotFoundException
import java.nio.file.AccessDeniedException
import java.util.concurrent.TimeUnit

@HiltWorker
class ProgressSyncWorker @AssistedInject constructor(
	@Assisted appContext: Context,
	@Assisted params: WorkerParameters,
	private val repository: ProgressSyncRepository,
	private val settings: ProgressSyncSettings,
) : CoroutineWorker(appContext, params) {

	override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
		if (!settings.isEnabled || settings.directory == null) {
			return@withContext Result.success()
		}
		try {
			when (inputData.getString(KEY_ACTION) ?: ACTION_FULL_SYNC) {
				ACTION_EXPORT_ONLY -> {
					repository.exportNow()
					settings.lastExport = System.currentTimeMillis()
				}

				else -> {
					repository.importNow()
					settings.lastImport = System.currentTimeMillis()
					repository.exportNow()
					settings.lastExport = System.currentTimeMillis()
				}
			}
			settings.lastError = null
			Result.success()
		} catch (e: Exception) {
			settings.lastError = if (e is AccessDeniedException) {
				applicationContext.getString(org.koitharu.kotatsu.R.string.progress_sync_error_provider_missing)
			} else {
				e.getDisplayMessage(applicationContext.resources)
			}
			when (e) {
				is SerializationException,
				is FileNotFoundException,
				is AccessDeniedException -> Result.failure()
				else -> Result.retry()
			}
		}
	}

	@Reusable
	class Scheduler @javax.inject.Inject constructor(
		private val workManager: WorkManager,
		private val settings: ProgressSyncSettings,
	) : PeriodicWorkScheduler {

		override suspend fun schedule() {
			if (!shouldRunAutomatic()) {
				return unschedule()
			}
			val interval = settings.interval.minutes
			val request = PeriodicWorkRequestBuilder<ProgressSyncWorker>(interval, TimeUnit.MINUTES)
				.addTag(TAG)
				.setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
				.build()
			workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
		}

		override suspend fun unschedule() {
			workManager.cancelUniqueWork(TAG)
		}

		override suspend fun isScheduled(): Boolean {
			return workManager.awaitUniqueWorkInfoByName(TAG)
				.any { !it.state.isFinished }
		}

		fun scheduleDebouncedExport() {
			if (!shouldRunAutomatic()) {
				return
			}
			val request = OneTimeWorkRequestBuilder<ProgressSyncWorker>()
				.setInitialDelay(EXPORT_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
				.setInputData(Data.Builder().putString(KEY_ACTION, ACTION_EXPORT_ONLY).build())
				.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
				.addTag(TAG_EXPORT)
				.build()
			workManager.enqueueUniqueWork(TAG_EXPORT, ExistingWorkPolicy.REPLACE, request)
		}

		private fun shouldRunAutomatic(): Boolean {
			return settings.isEnabled &&
				settings.directory != null &&
				settings.mode == ProgressSyncMode.AUTOMATIC
		}
	}

	companion object {
		private const val TAG = "progress_sync"
		private const val TAG_EXPORT = "progress_sync_export"
		private const val KEY_ACTION = "action"
		private const val ACTION_EXPORT_ONLY = "export_only"
		private const val ACTION_FULL_SYNC = "full_sync"
		private const val EXPORT_DEBOUNCE_MS = 45_000L
	}
}
