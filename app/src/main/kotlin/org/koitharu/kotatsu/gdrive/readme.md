# Google Drive Sync Module for Kotatsu

This module introduces a modular, server-less cloud synchronization system for Kotatsu, leveraging Google Drive's AppData folder to sync user data across multiple devices. It is designed to be as independent as possible, with minimal intrusion into the core application logic.

## 🎯 Objectives

* **Seamless Multi-Device Sync:** Automatically and manually sync user data (reading history, bookmarks, favorites, settings, library metadata) via Google Drive.
* **Modular Integration:** Implemented as an isolated module (`DriveSyncProvider`) under a `SyncProvider` abstraction, registered with a global `SyncRegistry`.
* **Privacy & Security:** Utilizes Google Drive's AppData folder (`drive.appdata`) for non-user-visible storage and encrypts all sync data using Android Keystore AES.
* **Full Data Coverage:** Syncs all specified data types into a single, encrypted `kotatsu_sync.encrypted` JSON file.
* **Offline Support:** Queues changes when offline and syncs upon reconnection.
* **Conflict Resolution:** Uses timestamp-based logic ("newer wins") for data merging.

## ⚙️ Technical Overview

The sync system is built around the `SyncProvider` interface, allowing for potential future integration of other sync services. `DriveSyncProvider` is the concrete implementation for Google Drive.

### Data Flow

1. **Local Changes:** User actions (e.g., reading progress, adding bookmarks) trigger `SyncEvent`s which are sent to the `SyncRegistry`.
2. **`DriveSyncProvider`:** Catches `SyncEvent`s and marks internal state as "dirty".
3. **Sync Trigger:** `syncNow()` can be called manually (from UI), automatically (on app startup/resume), or periodically (via `WorkManager`).
4. **Network Check:** Verifies internet connectivity.
5. **Google Sign-In:** Authenticates the user silently or interactively.
6. **File Management:**
    * Locates `kotatsu_sync.encrypted` in Google Drive AppData folder.
    * Creates the file if it doesn't exist.
7. **Download & Decrypt:** Downloads the encrypted file, decrypts it using Android Keystore, and parses it into `SyncData`.
8. **Collect Local Data:** Gathers the latest data from local repositories (reading history, bookmarks, etc.) into a `SyncData` object.
9. **Merge:** Compares remote and local `SyncData` using timestamp-based conflict resolution, creating a `mergedData` object.
10. **Encrypt & Upload:** If changes are detected in `mergedData` or there were unsynced local changes, the `mergedData` is encrypted (AES via Android Keystore), Base64-encoded, and uploaded back to Google Drive. The IV (Initialization Vector) is stored as a custom property of the Drive file.
11. **UI Update:** Updates UI elements (last sync time, status messages) via `StateFlow`s.

## 🗄️ Module Structure & Files

The core files for this module are located under `app/src/main/kotlin/org/koitharu/kotatsu/gdrive/`.

### ➕ New Files Created

1. **`gdrive/SyncProvider.kt`**: Interface for generic sync providers.
2. **`gdrive/SyncRegistry.kt`**: Singleton object to manage and coordinate multiple `SyncProvider` instances.
3. **`gdrive/EncryptionUtil.kt`**: Utility class for AES encryption/decryption using Android Keystore.
4. **`gdrive/InstantAdapter.kt`**: Custom Gson `TypeAdapter` for `java.time.Instant`.
5. **`gdrive/DriveSyncProvider.kt`**: The concrete implementation of `SyncProvider` for Google Drive. Handles authentication, Drive API interaction, encryption, and data merging.
6. **`gdrive/DriveSyncWorker.kt`**: A `CoroutineWorker` for performing periodic background syncs using WorkManager.
7. **`gdrive/models/SyncEvent.kt`**: Sealed class defining various events that trigger sync actions (e.g., `PageChanged`, `BookmarkAdded`).
8. **`gdrive/models/SyncItem.kt`**: Data classes defining the structure of individual syncable items (e.g., `ReadingHistoryItem`).
9. **`gdrive/models/SyncData.kt`**: The main data class representing the entire `kotatsu_sync.encrypted` JSON schema.
10. **`gdrive/ui/DriveSyncSettingsFragment.kt`**: A `PreferenceFragmentCompat` that provides the user interface for managing Google Drive sync settings.
11. **`data/MockRepositories.kt`**: (Temporary for development) Interfaces and mock implementations for `IReadingHistoryRepository`, `IBookmarkRepository`, `IFavoriteRepository`, `IAppSettings`, `ILibraryRepository`. **These must be replaced with actual Kotatsu repository implementations.**
12. **`app/src/main/res/xml/drive_sync_preferences.xml`**: XML layout for the `DriveSyncSettingsFragment` UI.
13. **`app/src/main/res/values/strings.xml`**: New string resources for sync-related UI and messages.
14. **`app/src/main/res/drawable/*.xml`**: Placeholder drawable assets (e.g., `ic_google_drive_logo.xml`, `ic_sync_auto.xml`, `ic_sync_now.xml`, `ic_history.xml`, `ic_info.xml`, `ic_logout.xml`, `ic_cloud_sync.xml`). **These should be replaced with proper Material Design icons or app-specific assets.**

### 📝 Modified Core Files

To integrate this module, the following existing core Kotatsu files were minimally modified:

1. **`app/src/main/AndroidManifest.xml`**:
    * Added `<activity android:name="com.google.android.gms.auth.api.signin.internal.SignInHubActivity" ... />` for Google Sign-In.
    * Added `INTERNET` and `ACCESS_NETWORK_STATE` permissions.
    * Updated `<application android:name=".KotatsuApp" ... />` to point to the new custom `Application` class.
2. **`app/src/main/kotlin/org/koitharu/kotatsu/KotatsuApp.kt`**:
    * Created (if not existing) or modified to extend `Application` and implement `Configuration.Provider`.
    * Annotated with `@HiltAndroidApp`.
    * Injected `DriveSyncProvider` and `HiltWorkerFactory`.
    * Registered `driveSyncProvider` with `SyncRegistry` in `onCreate()`.
    * Provided `WorkManager` configuration using `HiltWorkerFactory`.
3. **`app/src/main/kotlin/org/koitharu/kotatsu/di/AppModule.kt`**:
    * Updated to provide `EncryptionUtil`, `DriveSyncProvider`, and the `MockRepository` implementations.
    * **Crucially, this is where you will swap out the `MockRepository` providers with your actual Kotatsu repository implementations.**
4. **`app/src/main/res/xml/preferences.xml`**: (Your main settings XML)
    * Added a `<Preference app:fragment="org.koitharu.kotatsu.gdrive.ui.DriveSyncSettingsFragment" ... />` entry to navigate to the new `DriveSyncSettingsFragment`.
    * Added `pref_summary_cloud_sync_status` string to `strings.xml`.
5. **Kotatsu Core Data Modifying Classes**: (Examples, *not directly modified by AI output, but require manual integration*)
    * Any `ViewModel`, `Manager`, or `Repository` classes that modify reading history, bookmarks, favorites, settings, or library metadata will need to:
        * Inject `SyncRegistry`.
        * Call `SyncRegistry.sendEvent(SyncEvent.<AppropriateEvent>)` after a successful local data modification.
        * Examples include classes handling `ReaderViewModel.saveReadingProgress()`, `DetailsViewModel.addBookmark()`, `AppSettings.setTheme()`, `LibraryManager.addSource()`, etc.

## 🛠️ Integration Steps for the Developer

1. **Create New Files:** Place the new `.kt` and `.xml` files in their specified paths.
2. **Update Existing Files:** Apply the outlined modifications to `AndroidManifest.xml`, `KotatsuApp.kt`, `AppModule.kt`, and your main `preferences.xml`.
3. **Replace Mock Repositories:** This is the **most critical manual step**. In `AppModule.kt`, replace the `MockReadingHistoryRepository`, `MockBookmarkRepository`, etc., with your actual Kotatsu repository implementations. Ensure these repositories return data in the `SyncData` format (e.g., `Map<String, ReadingHistoryItem>`, `List<String>`, `SettingsData`, `LibraryData`).
4. **Implement `SyncEvent` Triggers:** Go through your application's data modification points (as exemplified in "Modified Core Files" above) and add `SyncRegistry.sendEvent()` calls.
5. **Add Drawable Assets:** Create or import appropriate drawable icons for the preferences.
6. **Run Gradle Sync:** Ensure all new dependencies (WorkManager Hilt, Google Drive API) are resolved.

## ▶️ How to Test Locally

Refer to the "Testing and Validation" guide above for a comprehensive approach to verifying the sync system's functionality. This includes initial setup, multi-device sync, offline behavior, conflict resolution, and error handling.

## 🔮 Future Enhancements

* **Granular Dirty State Tracking:** Instead of `hasUnsyncedLocalChanges` for the entire `SyncData` object, implement dirty flags for `reading_history_dirty`, `bookmarks_dirty`, etc., to allow more efficient partial uploads.
* **Throttling/Debouncing Events:** Implement a mechanism to prevent `SyncRegistry.sendEvent()` from triggering `syncNow()` too frequently (e.g., debounce reading progress updates, batch multiple small setting changes).
* **User-Configurable Sync Intervals:** Allow users to choose different periodic sync intervals (e.g., 1 hour, 12 hours) instead of a fixed 6 hours.
* **Progress Indicators:** Add more detailed progress indicators during `syncNow()` (e.g., "Downloading...", "Merging...", "Uploading...").
* **Push Notifications for Sync Status:** Optionally notify users when a background sync completes or fails.
* **Manual Backup/Restore:** Extend the system to allow users to manually save/load specific versions of `kotatsu_sync.encrypted` from a user-accessible Drive folder.
* **Migration Logic for Schema:** If `schema_version` changes in the future, implement migration logic within the `merge` function to handle older schema versions gracefully.
