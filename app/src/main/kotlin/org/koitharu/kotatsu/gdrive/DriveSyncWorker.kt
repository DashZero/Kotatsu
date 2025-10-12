package org.koitharu.kotatsu.gdrive

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import android.util.Log

@HiltWorker
class DriveSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val driveSyncProvider: DriveSyncProvider
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("DriveSyncWorker", "Starting periodic sync work...")
        if (!driveSyncProvider.isEnabled) {
            Log.d("DriveSyncWorker", "DriveSyncProvider is not enabled. Skipping sync.")
            return@withContext Result.failure()
        }

        try {
            driveSyncProvider.syncNow()
            Log.d("DriveSyncWorker", "Periodic sync work completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e("DriveSyncWorker", "Periodic sync work failed: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "DriveSyncWorker"
        val REPEAT_INTERVAL = 6L
        val FLEX_INTERVAL = 1L
    }
}
