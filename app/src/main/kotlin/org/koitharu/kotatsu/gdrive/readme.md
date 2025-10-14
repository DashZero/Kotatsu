# Google Drive Sync Module for Kotatsu

This document details a modular, server-less cloud synchronization system for Kotatsu, leveraging Google Drive's AppData folder. It was integrated into the existing project with a focus on modularity and minimal intrusion into core application logic.

## 🎯 Objectives

* **Seamless Multi-Device Sync:** Automatically and manually sync reading history, bookmarks, favorites, settings, and library metadata.
* **Modular Integration:** Implemented via a `SyncProvider` interface, with `DriveSyncProvider` as the Google Drive implementation.
* **Privacy & Security:** Uses Google Drive's AppData folder (`drive.appdata`) and encrypts all data at rest using AES via the Android Keystore.
* **Offline Support:** Caches local changes and syncs them upon reconnection.
* **Conflict Resolution:** Employs a timestamp-based "newer wins" logic for merging data.

## ⚙️ File Manifest

The following files were created or modified to implement this feature.

### ➕ New Files Created

The core logic and UI of the sync module reside in these new files:

**Core Logic (`app/src/main/kotlin/org/koitharu/kotatsu/`):**
* `gdrive/SyncProvider.kt`: The primary interface for any sync provider.
* `gdrive/SyncRegistry.kt`: A singleton to manage and dispatch sync events.
* `gdrive/DriveSyncProvider.kt`: The main implementation for Google Drive sync, handling authentication, API calls, data merging, and encryption.
* `gdrive/DriveSyncWorker.kt`: The `WorkManager` worker for periodic background synchronization.
* `gdrive/EncryptionUtil.kt`: A utility for AES encryption/decryption using Android Keystore.
* `gdrive/InstantAdapter.kt`: A custom Gson `TypeAdapter` for `java.time.Instant`.
* `gdrive/models/SyncEvent.kt`: Defines events that trigger sync actions (e.g., `PageChanged`).
* `gdrive/models/SyncItem.kt`: Defines the data structure for syncable items (e.g., `ReadingHistoryItem`).
* `gdrive/models/SyncData.kt`: Defines the complete JSON schema for the `kotatsu_sync.encrypted` file.
* `di/AppModule.kt`: A Hilt module to provide dependencies like `DriveSyncProvider` and mock repositories.
* `KotatsuApp.kt`: A custom `Application` class, required for Hilt and custom WorkManager initialization.

**UI & Resources:**
* `gdrive/ui/DriveSyncSettingsFragment.kt`: The settings screen fragment for managing sync.
* `res/xml/drive_sync_preferences.xml`: The XML layout for the sync settings screen.
* `res/drawable/ic_cloud_sync.xml`: Icon for the main settings entry.
* `res/drawable/ic_google_drive_logo.xml`: Logo for the "Connect" button.
* `res/drawable/ic_sync_auto.xml`: Icon for the auto-sync toggle.
* `res/drawable/ic_sync_now.xml`: Icon for the "Sync Now" button.
* `res/drawable/ic_history.xml`: Icon for the "Last Synced" preference.
* `res/drawable/ic_info.xml`: Icon for the "Sync Status" preference.
* `res/drawable/ic_logout.xml`: Icon for the "Logout" button.

**Developer Placeholders:**
* `data/MockRepositories.kt`: **Temporary mock implementations** for data repositories. These **must be replaced** with actual Kotatsu repository implementations in `di/AppModule.kt`.

### 📝 Modified Core Files

To integrate the module, the following existing files were modified:

1. **`app/build.gradle` (Module: app):**
    * Added `implementation` lines for Google Play Services Auth, Google Drive API, Gson, Hilt-WorkManager, and Kotlin Coroutines Play Services.
    * Added a `packagingOptions` block to `android` to exclude duplicate `META-INF/` files from dependencies.

2. **`app/src/main/AndroidManifest.xml`:**
    * Set `android:name=".KotatsuApp"` on the `<application>` tag.
    * Added `<uses-permission>` for `INTERNET` and `ACCESS_NETWORK_STATE`.
    * Added a `<provider>` tag with `tools:node="remove"` to disable WorkManager's default initializer, preventing conflicts with the Hilt setup.

3. **`app/src/main/res/xml/pref_root.xml`:**
    * Added a `<PreferenceScreen>` entry to link to the new `DriveSyncSettingsFragment` from the main settings menu.

4. **`app/src/main/res/values/strings.xml`:**
    * Added all necessary string resources for the sync settings UI, including titles, summaries, and toast messages.

## 🛠️ Critical Integration Steps for the Developer

This module has been added, but requires manual integration with the app's core data logic.

1. **Replace Mock Repositories:** This is the **most critical manual step**. In `di/AppModule.kt`, replace the `binds` for mock repositories (e.g., `MockReadingHistoryRepository`) with your actual Kotatsu repository implementations. Ensure these repositories can provide and receive data in the formats defined in `SyncData.kt`.
2. **Implement `SyncEvent` Triggers:** In your `ViewModel`s, `Manager`s, or `Repository` classes that modify user data (e.g., saving reading progress, adding a bookmark), inject `SyncRegistry` and call `SyncRegistry.sendEvent(SyncEvent.<AppropriateEvent>)` after a successful local data update.
3. **Verify Drawable Assets:** The created drawables are simple vector placeholders. Replace them with proper Material Design icons or app-specific assets if desired.
