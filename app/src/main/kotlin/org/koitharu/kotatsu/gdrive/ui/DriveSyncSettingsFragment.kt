package org.koitharu.kotatsu.gdrive.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.gdrive.DriveSyncProvider
import org.koitharu.kotatsu.gdrive.DriveSyncWorker
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class DriveSyncSettingsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var driveSyncProvider: DriveSyncProvider

    private lateinit var connectAccountPref: Preference
    private lateinit var autoSyncToggle: SwitchPreferenceCompat
    private lateinit var syncNowPref: Preference
    private lateinit var lastSyncedPref: Preference
    private lateinit var syncStatusPref: Preference
    private lateinit var logoutPref: Preference

    private val googleSignInLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data: Intent? = result.data
            viewLifecycleOwner.lifecycleScope.launch {
                driveSyncProvider.handleSignInResult(data)
                updateUiState()
                if (driveSyncProvider.isEnabled) {
                    Toast.makeText(requireContext(), R.string.sync_toast_sync_started, Toast.LENGTH_SHORT).show()
                    driveSyncProvider.syncNow() // Initial sync after sign-in
                    if (autoSyncToggle.isChecked) {
                        schedulePeriodicSync()
                    }
                } else {
                    Toast.makeText(requireContext(), R.string.sync_toast_sign_in_failed, Toast.LENGTH_LONG).show()
                    cancelPeriodicSync()
                }
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.drive_sync_preferences, rootKey)

        connectAccountPref = findPreference("drive_connect_account")!!
        autoSyncToggle = findPreference("drive_auto_sync_enabled")!!
        syncNowPref = findPreference("drive_sync_now")!!
        lastSyncedPref = findPreference("drive_last_synced")!!
        syncStatusPref = findPreference("drive_sync_status")!!
        logoutPref = findPreference("drive_logout")!!

        setupListeners()
        observeSyncState()
    }

    private fun setupListeners() {
        connectAccountPref.setOnPreferenceClickListener {
            if (!driveSyncProvider.isEnabled) {
                googleSignInLauncher.launch(driveSyncProvider.getSignInIntent())
            }
            true
        }

        syncNowPref.setOnPreferenceClickListener {
            if (driveSyncProvider.isEnabled) {
                viewLifecycleOwner.lifecycleScope.launch {
                    driveSyncProvider.syncNow()
                }
            } else {
                Toast.makeText(requireContext(), R.string.sync_toast_not_connected, Toast.LENGTH_SHORT).show()
            }
            true
        }

        logoutPref.setOnPreferenceClickListener {
            if (driveSyncProvider.isEnabled) {
                viewLifecycleOwner.lifecycleScope.launch {
                    driveSyncProvider.signOut()
                    Toast.makeText(requireContext(), R.string.sync_toast_logged_out, Toast.LENGTH_SHORT).show()
                    updateUiState()
                    cancelPeriodicSync()
                }
            }
            true
        }

        autoSyncToggle.setOnPreferenceChangeListener { preference, newValue ->
            val isEnabled = newValue as Boolean
            if (isEnabled) {
                if (driveSyncProvider.isEnabled) {
                    schedulePeriodicSync()
                    Toast.makeText(requireContext(), "Auto sync enabled", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), R.string.sync_toast_not_connected, Toast.LENGTH_LONG).show()
                    (preference as SwitchPreferenceCompat).isChecked = false
                    return@setOnPreferenceChangeListener false
                }
            } else {
                cancelPeriodicSync()
                Toast.makeText(requireContext(), "Auto sync disabled", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun observeSyncState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    driveSyncProvider.lastSyncTime.collect { instant ->
                        val formattedTime = instant?.let {
                            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                                .format(it.atZone(java.time.ZoneId.systemDefault()))
                        }
                        lastSyncedPref.summary = formattedTime?.let {
                            getString(R.string.pref_summary_last_synced_at, it)
                        } ?: getString(R.string.pref_summary_never_synced)
                    }
                }
                launch {
                    driveSyncProvider.syncStatus.collect { status ->
                        syncStatusPref.summary = status
                        when {
                            status.contains("completed", ignoreCase = true) ->
                                Toast.makeText(requireContext(), R.string.sync_toast_sync_successful, Toast.LENGTH_SHORT).show()
                            status.contains("skipped: no network", ignoreCase = true) ->
                                Toast.makeText(requireContext(), R.string.sync_toast_network_error, Toast.LENGTH_LONG).show()
                            status.contains("network error", ignoreCase = true) ->
                                Toast.makeText(requireContext(), R.string.sync_toast_network_error, Toast.LENGTH_LONG).show()
                            status.contains("google api not connected", ignoreCase = true) ->
                                Toast.makeText(requireContext(), R.string.sync_toast_google_api_not_connected, Toast.LENGTH_LONG).show()
                            status.contains("user action required", ignoreCase = true) ->
                                Toast.makeText(requireContext(), R.string.sync_toast_google_api_resolution_required, Toast.LENGTH_LONG).show()
                            status.contains("connection timed out", ignoreCase = true) ->
                                Toast.makeText(requireContext(), R.string.sync_toast_google_api_timeout, Toast.LENGTH_LONG).show()
                            status.contains("corrupted sync data", ignoreCase = true) ->
                                Toast.makeText(requireContext(), R.string.sync_toast_data_corruption, Toast.LENGTH_LONG).show()
                            status.contains("drive service not initialized", ignoreCase = true) ->
                                Toast.makeText(requireContext(), R.string.sync_toast_drive_not_initialized, Toast.LENGTH_LONG).show()
                            status.contains("failed", ignoreCase = true) ->
                                Toast.makeText(requireContext(), getString(R.string.sync_toast_sync_failed, status), Toast.LENGTH_LONG).show()
                            status.contains("syncing", ignoreCase = true) ->
                                Toast.makeText(requireContext(), R.string.sync_toast_sync_started, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                launch {
                    driveSyncProvider.isEnabledFlow.collect { isEnabled ->
                        updateUiState(isEnabled)
                        if (!isEnabled && autoSyncToggle.isChecked) {
                            autoSyncToggle.isChecked = false
                            cancelPeriodicSync()
                            Toast.makeText(requireContext(), "Disconnected from Drive, auto sync disabled.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun updateUiState(isConnected: Boolean = driveSyncProvider.isEnabled) {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (isConnected && account != null) {
            connectAccountPref.title = getString(R.string.pref_summary_connect_google_drive_connected, account.email)
            connectAccountPref.summary = null
            connectAccountPref.isEnabled = false

            autoSyncToggle.isEnabled = true
            syncNowPref.isEnabled = true
            logoutPref.isEnabled = true
        } else {
            connectAccountPref.title = getString(R.string.pref_title_connect_google_drive)
            connectAccountPref.summary = getString(R.string.pref_summary_connect_google_drive_disconnected)
            connectAccountPref.isEnabled = true

            autoSyncToggle.isEnabled = false
            syncNowPref.isEnabled = false
            logoutPref.isEnabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        updateUiState()
        if (autoSyncToggle.isChecked && driveSyncProvider.isEnabled) {
            schedulePeriodicSync()
        } else {
            cancelPeriodicSync()
        }
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicSyncRequest = PeriodicWorkRequestBuilder<DriveSyncWorker>(
            DriveSyncWorker.REPEAT_INTERVAL, TimeUnit.HOURS,
            DriveSyncWorker.FLEX_INTERVAL, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            DriveSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSyncRequest
        )
        Log.d("DriveSyncFragment", "Periodic sync scheduled.")
    }

    private fun cancelPeriodicSync() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork(DriveSyncWorker.WORK_NAME)
        Log.d("DriveSyncFragment", "Periodic sync cancelled.")
    }
}
