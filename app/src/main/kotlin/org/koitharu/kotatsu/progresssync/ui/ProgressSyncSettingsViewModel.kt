package org.koitharu.kotatsu.progresssync.ui

import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.resolveFile
import org.koitharu.kotatsu.progresssync.data.ProgressSyncRepository
import org.koitharu.kotatsu.progresssync.prefs.ProgressSyncSettings
import javax.inject.Inject

@HiltViewModel
class ProgressSyncSettingsViewModel @Inject constructor(
	private val settings: ProgressSyncSettings,
	private val repository: ProgressSyncRepository,
	@ApplicationContext private val appContext: Context,
) : BaseViewModel() {

	val directorySummary = MutableStateFlow<String?>(null)
	val statusSummary = MutableStateFlow("")
	val deviceName = MutableStateFlow(settings.deviceName)
	val deviceId = MutableStateFlow(settings.deviceId)

	init {
		updateDirectorySummary()
		updateStatusSummary()
		launchJob(Dispatchers.Default) {
			settings.observe(
				ProgressSyncSettings.KEY_PROGRESS_SYNC_DIRECTORY,
				ProgressSyncSettings.KEY_PROGRESS_SYNC_ENABLED,
				ProgressSyncSettings.KEY_PROGRESS_SYNC_LAST_EXPORT,
				ProgressSyncSettings.KEY_PROGRESS_SYNC_LAST_IMPORT,
				ProgressSyncSettings.KEY_PROGRESS_SYNC_LAST_ERROR,
			).collectLatest {
				updateDirectorySummary()
				updateStatusSummary()
			}
		}
	}

	fun exportNow() {
		launchLoadingJob(Dispatchers.IO) {
			runCatching {
				repository.exportNow()
			}.onSuccess {
				settings.lastExport = System.currentTimeMillis()
				settings.lastError = null
			}.onFailure { error ->
				settings.lastError = errorMessage(error)
				throw error
			}
			updateStatusSummary()
		}
	}

	fun importNow() {
		launchLoadingJob(Dispatchers.IO) {
			runCatching {
				repository.importNow()
			}.onSuccess {
				settings.lastImport = System.currentTimeMillis()
				settings.lastError = null
			}.onFailure { error ->
				settings.lastError = errorMessage(error)
				throw error
			}
			updateStatusSummary()
		}
	}

	fun getStatusMessage(): String {
		val lastImport = formatTime(settings.lastImport)
		val lastExport = formatTime(settings.lastExport)
		val lastError = settings.lastError
		val status = if (settings.isEnabled && isDirectoryWritable()) {
			appContext.getString(R.string.progress_sync_status_connected)
		} else {
			appContext.getString(R.string.progress_sync_status_disconnected)
		}
		val parts = mutableListOf(
			status,
			appContext.getString(R.string.progress_sync_status_imported, lastImport),
			appContext.getString(R.string.progress_sync_status_exported, lastExport),
		)
		if (!lastError.isNullOrBlank()) {
			parts += appContext.getString(R.string.progress_sync_status_error, lastError)
		}
		return parts.joinToString("\n")
	}

	fun refreshDirectorySummary() {
		updateDirectorySummary()
	}

	private fun updateDirectorySummary() {
		val uri = settings.directory
		directorySummary.value = uri?.toUserFriendlyString()
	}

	private fun updateStatusSummary() {
		statusSummary.value = getStatusMessage()
	}

	private fun Uri.toUserFriendlyString(): String? {
		val df = DocumentFile.fromTreeUri(appContext, this)
		if (df?.canWrite() != true) {
			return null
		}
		return resolveFile(appContext)?.path ?: df.name ?: toString()
	}

	private fun formatTime(timestamp: Long): String {
		return if (timestamp == 0L) {
			appContext.getString(R.string.progress_sync_status_never)
		} else {
			DateUtils.getRelativeTimeSpanString(timestamp).toString()
		}
	}

	private fun isDirectoryWritable(): Boolean {
		val uri = settings.directory ?: return false
		val df = DocumentFile.fromTreeUri(appContext, uri)
		return df?.canWrite() == true
	}

	private fun errorMessage(error: Throwable): String {
		return if (error is java.nio.file.AccessDeniedException) {
			appContext.getString(R.string.progress_sync_error_provider_missing)
		} else {
			error.getDisplayMessage(appContext.resources)
		}
	}
}
