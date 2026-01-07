package org.koitharu.kotatsu.progresssync.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultCallback
import androidx.fragment.app.viewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.exceptions.resolve.SnackbarErrorObserver
import org.koitharu.kotatsu.core.os.OpenDocumentTreeHelper
import org.koitharu.kotatsu.core.ui.BasePreferenceFragment
import org.koitharu.kotatsu.core.ui.dialog.buildAlertDialog
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.tryLaunch
import org.koitharu.kotatsu.progresssync.prefs.ProgressSyncMode
import org.koitharu.kotatsu.progresssync.prefs.ProgressSyncSettings
import javax.inject.Inject

@AndroidEntryPoint
class ProgressSyncSettingsFragment :
	BasePreferenceFragment(R.string.progress_sync),
	ActivityResultCallback<Uri?>,
	android.content.SharedPreferences.OnSharedPreferenceChangeListener {

	@Inject
	lateinit var progressSyncSettings: ProgressSyncSettings

	private val viewModel by viewModels<ProgressSyncSettingsViewModel>()
	private val outputSelectCall = OpenDocumentTreeHelper(this, this)

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_progress_sync)
		findPreference<SwitchPreferenceCompat>(ProgressSyncSettings.KEY_PROGRESS_SYNC_INCLUDE_HISTORY)?.isChecked = true
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewModel.directorySummary.observe(viewLifecycleOwner, ::bindDirectorySummary)
		viewModel.statusSummary.observe(viewLifecycleOwner, ::bindStatusSummary)
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(listView, this))
		findPreference<Preference>(ProgressSyncSettings.KEY_PROGRESS_SYNC_DEVICE_NAME)?.summary = viewModel.deviceName.value
		findPreference<Preference>(ProgressSyncSettings.KEY_PROGRESS_SYNC_DEVICE_ID)?.summary = viewModel.deviceId.value
		updatePreferencesState()
		progressSyncSettings.subscribe(this)
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		val result = when (preference.key) {
			ProgressSyncSettings.KEY_PROGRESS_SYNC_DIRECTORY -> outputSelectCall.tryLaunch(null)
			KEY_EXPORT_NOW -> {
				viewModel.exportNow()
				true
			}

			KEY_IMPORT_NOW -> {
				viewModel.importNow()
				true
			}

			KEY_STATUS -> {
				showStatusDialog()
				true
			}

			else -> return super.onPreferenceTreeClick(preference)
		}
		if (!result) {
			Snackbar.make(listView, R.string.progress_sync_error_provider_missing, Snackbar.LENGTH_SHORT).show()
		}
		return true
	}

	override fun onActivityResult(result: Uri?) {
		if (result != null) {
			val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
			context?.contentResolver?.takePersistableUriPermission(result, takeFlags)
			progressSyncSettings.directory = result
			viewModel.refreshDirectorySummary()
			updatePreferencesState()
		}
	}

	override fun onResume() {
		super.onResume()
		updatePreferencesState()
	}

	override fun onDestroyView() {
		progressSyncSettings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onSharedPreferenceChanged(sharedPreferences: android.content.SharedPreferences?, key: String?) {
		when (key) {
			ProgressSyncSettings.KEY_PROGRESS_SYNC_ENABLED,
			ProgressSyncSettings.KEY_PROGRESS_SYNC_MODE -> updatePreferencesState()
		}
	}

	private fun bindDirectorySummary(summary: String?) {
		val preference = findPreference<Preference>(ProgressSyncSettings.KEY_PROGRESS_SYNC_DIRECTORY) ?: return
		preference.summary = when (summary) {
			null -> getString(R.string.invalid_value_message)
			"" -> null
			else -> summary
		}
		preference.icon = if (summary == null) getWarningIcon() else null
	}

	private fun bindStatusSummary(summary: String) {
		findPreference<Preference>(KEY_STATUS)?.summary = summary
	}

	private fun updatePreferencesState() {
		val enabled = progressSyncSettings.isEnabled && progressSyncSettings.directory != null
		val isAutomatic = progressSyncSettings.mode == ProgressSyncMode.AUTOMATIC
		findPreference<ListPreference>(ProgressSyncSettings.KEY_PROGRESS_SYNC_INTERVAL)?.isEnabled = enabled && isAutomatic
		findPreference<Preference>(KEY_EXPORT_NOW)?.isEnabled = enabled
		findPreference<Preference>(KEY_IMPORT_NOW)?.isEnabled = enabled
		findPreference<Preference>(ProgressSyncSettings.KEY_PROGRESS_SYNC_INCLUDE_FAVORITES)?.isEnabled = enabled
		findPreference<Preference>(ProgressSyncSettings.KEY_PROGRESS_SYNC_INCLUDE_BOOKMARKS)?.isEnabled = enabled
	}

	private fun showStatusDialog() {
		buildAlertDialog(context ?: return) {
			setTitle(R.string.progress_sync_status)
			setMessage(viewModel.getStatusMessage())
			setPositiveButton(android.R.string.ok, null)
		}.show()
	}

	private companion object {
		const val KEY_EXPORT_NOW = "progress_sync_export_now"
		const val KEY_IMPORT_NOW = "progress_sync_import_now"
		const val KEY_STATUS = "progress_sync_status"
	}
}
