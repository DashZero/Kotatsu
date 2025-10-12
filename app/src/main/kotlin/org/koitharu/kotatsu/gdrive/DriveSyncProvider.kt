package org.koitharu.kotatsu.gdrive

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.asDeferred
import org.koitharu.kotatsu.data.IBookmarkRepository
import org.koitharu.kotatsu.data.IFavoriteRepository
import org.koitharu.kotatsu.data.IAppSettings
import org.koitharu.kotatsu.data.ILibraryRepository
import org.koitharu.kotatsu.data.IReadingHistoryRepository
import org.koitharu.kotatsu.gdrive.models.LibraryData
import org.koitharu.kotatsu.gdrive.models.ReadingHistoryItem
import org.koitharu.kotatsu.gdrive.models.SyncData
import org.koitharu.kotatsu.gdrive.models.SyncEvent
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant

class DriveSyncProvider(
    private val context: Context,
    private val readingHistoryRepository: IReadingHistoryRepository,
    private val bookmarkRepository: IBookmarkRepository,
    private val favoriteRepository: IFavoriteRepository,
    private val appSettings: IAppSettings,
    private val libraryRepository: ILibraryRepository,
    private var driveService: Drive? = null
) : SyncProvider {

    private val TAG = "DriveSyncProvider"
    private val ENCRYPTED_SYNC_FILE_NAME = "kotatsu_sync.encrypted"
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, InstantAdapter())
        .setPrettyPrinting()
        .create()

    private val encryptionUtil = EncryptionUtil(context)

    private var inMemorySyncData: SyncData = SyncData.empty()
    private val syncMutex = Mutex()
    private val isSyncing = MutableStateFlow(false)
    val isSyncingFlow: StateFlow<Boolean> = isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Instant?>(null)
    val lastSyncTime: StateFlow<Instant?> = _lastSyncTime.asStateFlow()

    private val _syncStatus = MutableStateFlow<String>("Idle")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private var hasUnsyncedLocalChanges: Boolean = false

    private val _isEnabled = MutableStateFlow(false)
    val isEnabledFlow: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val googleSignInClient: GoogleSignInClient by lazy {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, signInOptions)
    }

    override val isEnabled: Boolean
        get() {
            val connected = GoogleSignIn.getLastSignedInAccount(context) != null && driveService != null
            if (_isEnabled.value != connected) {
                _isEnabled.value = connected
            }
            return connected
        }

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    suspend fun handleSignInResult(data: Intent?) = withContext(Dispatchers.IO) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
                .setSelectedAccount(account.account)
            driveService = Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credential)
                .setApplicationName("Kotatsu")
                .build()
            Log.d(TAG, "Google Drive service initialized successfully.")
            _syncStatus.value = "Connected"
            _isEnabled.value = true
        } catch (e: ApiException) {
            val errorMessage = "Sign-in failed: ${e.statusCode} (${e.localizedMessage})"
            Log.e(TAG, errorMessage, e)
            driveService = null
            _syncStatus.value = errorMessage
            _isEnabled.value = false
        } catch (e: Exception) {
            val errorMessage = "Error initializing Drive service: ${e.localizedMessage}"
            Log.e(TAG, errorMessage, e)
            driveService = null
            _syncStatus.value = errorMessage
            _isEnabled.value = false
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            googleSignInClient.signOut().asDeferred().await()
            driveService = null
            inMemorySyncData = SyncData.empty()
            hasUnsyncedLocalChanges = false
            _lastSyncTime.value = null
            _syncStatus.value = "Signed Out"
            _isEnabled.value = false
            Log.d(TAG, "Signed out from Google Drive.")
        } catch (e: Exception) {
            val errorMessage = "Error signing out: ${e.localizedMessage}"
            Log.e(TAG, errorMessage, e)
            _syncStatus.value = errorMessage
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    override suspend fun syncNow() = syncMutex.withLock {
        if (!isEnabled) {
            Log.d(TAG, "Sync skipped: Not enabled.")
            _syncStatus.value = "Sync skipped: Not connected to Google Drive."
            return // Sync not enabled
        }
        if (isSyncing.value) {
            Log.d(TAG, "Sync skipped: Already syncing.")
            _syncStatus.value = "Sync already in progress."
            return
        }
        if (!isNetworkAvailable()) {
            Log.d(TAG, "Sync skipped: No network connection.")
            _syncStatus.value = "Sync skipped: No network connection."
            return
        }

        isSyncing.value = true
        _syncStatus.value = "Syncing..."
        Log.d(TAG, "Starting sync operation.")

        try {
            val fileId = ensureSyncFile()
            val remoteSyncData = downloadFile(fileId) ?: SyncData.empty()
            val localSyncData = collectLocalData()

            Log.d(TAG, "Remote data collected: ${remoteSyncData.updated_at}")
            Log.d(TAG, "Local data collected: ${localSyncData.updated_at}")

            val mergedData = merge(remoteSyncData, localSyncData)

            if (mergedData != remoteSyncData || hasUnsyncedLocalChanges) {
                Log.d(TAG, "Changes detected, uploading merged data.")
                uploadFile(fileId, mergedData)
                inMemorySyncData = mergedData // Update in-memory cache after successful upload
                hasUnsyncedLocalChanges = false // Reset dirty flag
            } else {
                Log.d(TAG, "No changes detected, skipping upload.")
            }

            _lastSyncTime.value = Instant.now()
            _syncStatus.value = "Sync completed at: ${_lastSyncTime.value?.atZone(java.time.ZoneId.systemDefault())?.format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT))}"
            Log.d(TAG, "Sync operation completed successfully.")

        } catch (e: IOException) {
            Log.e(TAG, "Sync failed due to network error: ${e.message}", e)
            _syncStatus.value = "Sync failed: Network error. Please check your connection."
        } catch (e: ApiException) {
            val errorMessage = when (e.statusCode) {
                com.google.android.gms.common.api.CommonStatusCodes.API_NOT_CONNECTED -> "Sync failed: Google API not connected. Please reconnect."
                com.google.android.gms.common.api.CommonStatusCodes.RESOLUTION_REQUIRED -> "Sync failed: User action required to resolve Google Drive issue."
                com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> "Sync failed: Network error during Google API call."
                com.google.android.gms.common.api.CommonStatusCodes.TIMEOUT -> "Sync failed: Connection timed out."
                else -> "Sync failed: Google Drive API error (${e.statusCode}). ${e.localizedMessage}"
            }
            Log.e(TAG, errorMessage, e)
            _syncStatus.value = errorMessage
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Sync failed: Data corruption. Could not parse sync file.", e)
            _syncStatus.value = "Sync failed: Corrupted sync data detected. Please try logging out and back in."
        }
        catch (e: Exception) {
            val errorMessage = "Sync failed: An unexpected error occurred: ${e.localizedMessage}"
            Log.e(TAG, errorMessage, e)
            _syncStatus.value = errorMessage
        } finally {
            isSyncing.value = false
        }
    }

    override fun onEvent(event: SyncEvent) {
        Log.d(TAG, "Received sync event: $event, marking local changes as unsynced.")
        hasUnsyncedLocalChanges = true
    }

    private suspend fun ensureSyncFile(): String = withContext(Dispatchers.IO) {
        checkNotNull(driveService) { "Drive service not initialized." }
        Log.d(TAG, "Ensuring sync file ($ENCRYPTED_SYNC_FILE_NAME) exists...")

        val files = driveService!!.files().list()
            .setSpaces("appDataFolder")
            .setQ("name='$ENCRYPTED_SYNC_FILE_NAME'")
            .setFields("files(id, name, properties)")
            .execute()

        val fileList = files.files

        return if (fileList.isNullOrEmpty()) {
            Log.d(TAG, "Sync file not found, creating new one.")
            val metadata = File().apply {
                name = ENCRYPTED_SYNC_FILE_NAME
                parents = listOf("appDataFolder")
                mimeType = "application/octet-stream"
            }
            val emptySyncDataJson = gson.toJson(SyncData.empty())
            val (emptyEncryptedContent, iv) = encryptionUtil.encryptToString(emptySyncDataJson)

            val content = ByteArrayContent.fromString("text/plain", emptyEncryptedContent)
            
            metadata.properties = mutableMapOf("iv" to iv as Any)

            driveService!!.files().create(metadata, content)
                .setFields("id")
                .execute()
                .id
        } else {
            Log.d(TAG, "Sync file found with ID: ${fileList.first().id}")
            fileList.first().id
        }
    }

    private suspend fun downloadFile(fileId: String): SyncData? = withContext(Dispatchers.IO) {
        checkNotNull(driveService) { "Drive service not initialized." }
        Log.d(TAG, "Downloading file with ID: $fileId")
        try {
            val fileMetadata = driveService!!.files().get(fileId)
                .setFields("properties, mimeType")
                .execute()

            val ivBase64 = fileMetadata.properties?.get("iv") as? String
            if (ivBase64 == null) {
                Log.w(TAG, "IV not found in file properties. Cannot decrypt. This might be an old file or empty.")
                val outputStream = ByteArrayOutputStream()
                driveService!!.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                val rawContent = String(outputStream.toByteArray(), Charsets.UTF_8)
                if (rawContent.isBlank() || rawContent == "{}") {
                    return@withContext SyncData.empty()
                }
                return try {
                    gson.fromJson(rawContent, SyncData::class.java)
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "Could not parse downloaded content as unencrypted JSON either. Returning null.")
                    null
                }
            }

            val outputStream = ByteArrayOutputStream()
            driveService!!.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            val encryptedContentBase64 = String(outputStream.toByteArray(), Charsets.UTF_8)

            if (encryptedContentBase64.isBlank()) {
                Log.d(TAG, "Downloaded file is empty, returning empty SyncData.")
                return@withContext SyncData.empty()
            }

            val decryptedJson = encryptionUtil.decryptFromString(encryptedContentBase64, ivBase64)
            return@withContext gson.fromJson(decryptedJson, SyncData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading or decrypting file: ${e.message}", e)
            throw e
        }
    }

    private suspend fun uploadFile(fileId: String, data: SyncData) = withContext(Dispatchers.IO) {
        checkNotNull(driveService) { "Drive service not initialized." }
        Log.d(TAG, "Uploading file with ID: $fileId")

        val json = gson.toJson(data.copy(updated_at = Instant.now()))
        val (encryptedContentBase64, ivBase64) = encryptionUtil.encryptToString(json)

        val content = ByteArrayContent.fromString("text/plain", encryptedContentBase64)

        val metadata = File().apply {
            properties = mutableMapOf("iv" to ivBase64 as Any)
            mimeType = "application/octet-stream"
        }

        try {
            driveService!!.files().update(fileId, metadata, content)
                .setFields("id")
                .execute()
            Log.d(TAG, "File uploaded successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file: ${e.message}", e)
            throw e
        }
    }

    private suspend fun collectLocalData(): SyncData = withContext(Dispatchers.IO) {
        Log.d(TAG, "Collecting local data from repositories...")

        val localHistory = readingHistoryRepository.getAllHistory()
        val localBookmarks = bookmarkRepository.getAllBookmarks()
        val localFavorites = favoriteRepository.getAllFavorites()
        val localSettings = appSettings.getCurrentSettings()
        val localLibrary = libraryRepository.getLibraryData()

        val syncData = SyncData(
            reading_history = localHistory.toMutableMap(),
            bookmarks = localBookmarks.toMutableList(),
            favorites = localFavorites.toMutableList(),
            settings = localSettings.toMutableMap(),
            library = localLibrary
        )
        syncData.updated_at = Instant.now()
        syncData.schema_version = SyncData.CURRENT_SCHEMA_VERSION

        Log.d(TAG, "Local data collection complete. Items: " +
                "History=${localHistory.size}, " +
                "Bookmarks=${localBookmarks.size}, " +
                "Favorites=${localFavorites.size}")
        return@withContext syncData
    }

    private fun merge(remote: SyncData, local: SyncData): SyncData {
        Log.d(TAG, "Merging remote and local sync data.")
        val merged = remote.copy(
            reading_history = remote.reading_history.toMutableMap(),
            bookmarks = remote.bookmarks.toMutableList(),
            favorites = remote.favorites.toMutableList(),
            settings = remote.settings.toMutableMap(),
            library = remote.library.copy()
        )

        local.reading_history.forEach { (mangaId, localItem) ->
            val remoteItem = merged.reading_history[mangaId]
            if (remoteItem == null || localItem.updated_at.isAfter(remoteItem.updated_at)) {
                merged.reading_history[mangaId] = localItem
            }
        }

        (local.bookmarks + remote.bookmarks).distinct().sorted().forEach { mangaId ->
            if (mangaId !in merged.bookmarks) {
                merged.bookmarks.add(mangaId)
            }
        }

        (local.favorites + remote.favorites).distinct().sorted().forEach { mangaId ->
            if (mangaId !in merged.favorites) {
                merged.favorites.add(mangaId)
            }
        }

        local.settings.forEach { (key, value) ->
            merged.settings[key] = value
        }

        merged.library = local.library.copy()

        merged.updated_at = Instant.now()
        merged.schema_version = SyncData.CURRENT_SCHEMA_VERSION

        Log.d(TAG, "Merge completed. Merged data updated at: ${merged.updated_at}")
        return merged
    }
}
