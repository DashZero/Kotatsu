package org.koitharu.kotatsu.progresssync.prefs

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import org.koitharu.kotatsu.core.util.ext.getEnumValue
import org.koitharu.kotatsu.core.util.ext.observeChanges
import org.koitharu.kotatsu.core.util.ext.putEnumValue
import org.koitharu.kotatsu.core.util.ext.toUriOrNull
import org.koitharu.kotatsu.parsers.util.nullIfEmpty
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressSyncSettings @Inject constructor(
	@ApplicationContext context: Context,
) {

	private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

	var isEnabled: Boolean
		get() = prefs.getBoolean(KEY_PROGRESS_SYNC_ENABLED, false)
		set(value) = prefs.edit { putBoolean(KEY_PROGRESS_SYNC_ENABLED, value) }

	var directory: Uri?
		get() = prefs.getString(KEY_PROGRESS_SYNC_DIRECTORY, null)?.toUriOrNull()
		set(value) = prefs.edit { putString(KEY_PROGRESS_SYNC_DIRECTORY, value?.toString()) }

	var mode: ProgressSyncMode
		get() = prefs.getEnumValue(KEY_PROGRESS_SYNC_MODE, ProgressSyncMode.MANUAL)
		set(value) = prefs.edit { putEnumValue(KEY_PROGRESS_SYNC_MODE, value) }

	var interval: ProgressSyncInterval
		get() = prefs.getEnumValue(KEY_PROGRESS_SYNC_INTERVAL, ProgressSyncInterval.HOURS_1)
		set(value) = prefs.edit { putEnumValue(KEY_PROGRESS_SYNC_INTERVAL, value) }

	var conflictStrategy: ProgressSyncConflictStrategy
		get() = prefs.getEnumValue(KEY_PROGRESS_SYNC_CONFLICT, ProgressSyncConflictStrategy.LAST_WRITE_WINS)
		set(value) = prefs.edit { putEnumValue(KEY_PROGRESS_SYNC_CONFLICT, value) }

	var includeFavorites: Boolean
		get() = prefs.getBoolean(KEY_PROGRESS_SYNC_INCLUDE_FAVORITES, false)
		set(value) = prefs.edit { putBoolean(KEY_PROGRESS_SYNC_INCLUDE_FAVORITES, value) }

	var includeBookmarks: Boolean
		get() = prefs.getBoolean(KEY_PROGRESS_SYNC_INCLUDE_BOOKMARKS, false)
		set(value) = prefs.edit { putBoolean(KEY_PROGRESS_SYNC_INCLUDE_BOOKMARKS, value) }

	val includeHistory: Boolean
		get() = prefs.getBoolean(KEY_PROGRESS_SYNC_INCLUDE_HISTORY, true)

	var deviceId: String
		get() = prefs.getString(KEY_PROGRESS_SYNC_DEVICE_ID, null) ?: run {
			val value = UUID.randomUUID().toString()
			prefs.edit { putString(KEY_PROGRESS_SYNC_DEVICE_ID, value) }
			value
		}
		set(value) = prefs.edit { putString(KEY_PROGRESS_SYNC_DEVICE_ID, value) }

	var deviceName: String
		get() = prefs.getString(KEY_PROGRESS_SYNC_DEVICE_NAME, null) ?: run {
			val value = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
				.joinToString(" ")
				.trim()
				.ifEmpty { "Android" }
			prefs.edit { putString(KEY_PROGRESS_SYNC_DEVICE_NAME, value) }
			value
		}
		set(value) = prefs.edit { putString(KEY_PROGRESS_SYNC_DEVICE_NAME, value) }

	var lastExport: Long
		get() = prefs.getLong(KEY_PROGRESS_SYNC_LAST_EXPORT, 0L)
		set(value) = prefs.edit { putLong(KEY_PROGRESS_SYNC_LAST_EXPORT, value) }

	var lastImport: Long
		get() = prefs.getLong(KEY_PROGRESS_SYNC_LAST_IMPORT, 0L)
		set(value) = prefs.edit { putLong(KEY_PROGRESS_SYNC_LAST_IMPORT, value) }

	var lastError: String?
		get() = prefs.getString(KEY_PROGRESS_SYNC_LAST_ERROR, null)?.nullIfEmpty()
		set(value) = prefs.edit { putString(KEY_PROGRESS_SYNC_LAST_ERROR, value) }

	fun subscribe(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
		prefs.registerOnSharedPreferenceChangeListener(listener)
	}

	fun unsubscribe(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
		prefs.unregisterOnSharedPreferenceChangeListener(listener)
	}

	fun observe(vararg keys: String): Flow<String?> = prefs.observeChanges()
		.filter { key -> key == null || key in keys }
		.onStart { emit(null) }
		.flowOn(Dispatchers.IO)

	companion object {
		const val KEY_PROGRESS_SYNC_ENABLED = "progress_sync_enabled"
		const val KEY_PROGRESS_SYNC_DIRECTORY = "progress_sync_directory"
		const val KEY_PROGRESS_SYNC_MODE = "progress_sync_mode"
		const val KEY_PROGRESS_SYNC_INTERVAL = "progress_sync_interval"
		const val KEY_PROGRESS_SYNC_CONFLICT = "progress_sync_conflict"
		const val KEY_PROGRESS_SYNC_INCLUDE_HISTORY = "progress_sync_include_history"
		const val KEY_PROGRESS_SYNC_INCLUDE_FAVORITES = "progress_sync_include_favorites"
		const val KEY_PROGRESS_SYNC_INCLUDE_BOOKMARKS = "progress_sync_include_bookmarks"
		const val KEY_PROGRESS_SYNC_DEVICE_ID = "progress_sync_device_id"
		const val KEY_PROGRESS_SYNC_DEVICE_NAME = "progress_sync_device_name"
		const val KEY_PROGRESS_SYNC_LAST_EXPORT = "progress_sync_last_export"
		const val KEY_PROGRESS_SYNC_LAST_IMPORT = "progress_sync_last_import"
		const val KEY_PROGRESS_SYNC_LAST_ERROR = "progress_sync_last_error"
	}
}
