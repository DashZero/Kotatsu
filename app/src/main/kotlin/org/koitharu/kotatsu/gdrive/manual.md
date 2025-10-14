# Kotatsu – Google Drive Sync Restoration & Integration Manual

This document provides comprehensive, developer-grade instructions for **restoring and reintegrating the Google Drive Sync feature** into a clean Kotatsu codebase.

It is optimized for use by both human developers and AI coding agents.  
All procedures preserve **core code integrity** and ensure compatibility with **Gradle 8.13**, **Hilt**, and **KSP**.

---

## ⚠️ Core Protection Rules

Before implementing or rebuilding, **strictly follow these principles**:

1. **Do not modify or overwrite Kotatsu core modules**  
   (reading, parsing, UI, sources, database, etc.) unless absolutely required.
2. **All Google Drive sync logic must reside inside**  
   `org.koitharu.kotatsu.gdrive`
3. **Use existing interfaces only**  
   (`IReadingHistoryRepository`, `IAppSettings`, etc.). Never rename or refactor them.
4. **Dependency Injection changes must be additive**  
   (no deletion or alteration of existing `@Provides` or `@Inject` bindings).
5. If any minimal modification to core code is required,  
   clearly document it with a comment prefix:  
   `// NOTE: Required modification for GDrive sync compatibility`

---

## 1. File Structure Overview

All files related to Google Drive sync must be created inside:

```
app/src/main/kotlin/org/koitharu/kotatsu/gdrive/
```

### 1.1 DriveSyncProvider.kt

**Purpose:** Main orchestrator for Google Drive synchronization.

**Requirements:**
- Annotate with `@Singleton`
- Use `@Inject constructor`
- Implement `SyncProvider`
- Accept dependencies:
  ```
  Context,
  IReadingHistoryRepository,
  IBookmarkRepository,
  IFavoriteRepository,
  IAppSettings,
  ILibraryRepository,
  EncryptionUtil
  ```
**Methods:**
- `fun syncNow()`
- `fun uploadData()`
- `fun downloadData()`

**Rules:**
- Must not directly modify repositories or core database logic.

---

### 1.2 SettingsSyncTrigger.kt

**Purpose:** Observes `AppSettings` changes and dispatches sync events.

**Requirements:**
- Annotate with `@Singleton`
- Constructor:
  ```kotlin
  @Inject constructor(appSettings: AppSettings, syncRegistry: SyncRegistry)
  ```
- Example logic:
  ```kotlin
  if (key == AppSettings.KEY_AUTO_SYNC) {
      syncRegistry.sendEvent(SyncEvent.Trigger)
  }
  ```

**Rules:**
- Do not alter the structure or logic of `AppSettings` or shared preferences.

---

### 1.3 SyncRegistry.kt

**Purpose:** Centralized event bus for coordinating all sync providers.

**Implementation Example:**
```kotlin
@Singleton
object SyncRegistry {
    private val providers = mutableListOf<SyncProvider>()

    fun register(provider: SyncProvider) {
        if (!providers.contains(provider)) providers.add(provider)
    }

    fun sendEvent(event: SyncEvent) {
        providers.forEach { it.onSyncEvent(event) }
    }
}
```

**Rules:**
- Must handle only coordination logic — no database operations.

---

### 1.4 EncryptionUtil.kt

**Purpose:** Provides lightweight AES encryption for Drive data.

**Requirements:**
- Annotate with `@Singleton`
- Constructor:
  ```kotlin
  @Inject constructor(context: Context)
  ```
- Must provide:
  ```kotlin
  fun encrypt(data: String): String
  fun decrypt(data: String): String
  ```
**Rules:**
- Use `Cipher` and `Base64`.
- Do not depend on or modify any global crypto utilities.

---

### 1.5 models/ Directory

Path:  
`app/src/main/kotlin/org/koitharu/kotatsu/gdrive/models/`

**Include the following:**

| File | Purpose |
|------|----------|
| `SyncData.kt` | Root data container for upload/download |
| `ReadingHistoryItem.kt` | Represents reading history entries |
| `LibraryData.kt` | Represents library and favorites metadata |
| `SyncEvent.kt` | Event structure used for registry communication |

**SyncEvent Example:**
```kotlin
sealed class SyncEvent {
    object Trigger : SyncEvent()
    data class Error(val reason: String) : SyncEvent()
}
```

---

## 2. Dependency Injection Setup (Hilt / Dagger)

### 2.1 AppModule.kt

Path:  
`app/src/main/kotlin/org/koitharu/kotatsu/di/AppModule.kt`

Append the following (if not present):

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides fun provideReadingHistoryRepository(repo: HistoryRepository): IReadingHistoryRepository = repo
    @Provides fun provideBookmarkRepository(repo: BookmarksRepository): IBookmarkRepository = repo
    @Provides fun provideFavoriteRepository(repo: FavouritesRepository): IFavoriteRepository = repo
    @Provides fun provideLibraryRepository(repo: MangaSourcesRepository): ILibraryRepository = repo
    @Provides fun provideAppSettings(settings: AppSettings): IAppSettings = settings
    @Provides fun provideEncryptionUtil(util: EncryptionUtil): EncryptionUtil = util
    @Provides fun provideSyncRegistry(): SyncRegistry = SyncRegistry
}
```

**Note:** Only append new providers when necessary. Never remove or alter existing ones.

---

### 2.2 Repository Requirements

| Repository | File Path | Interface | Requirements |
|-------------|------------|------------|---------------|
| `HistoryRepository` | `data/HistoryRepository.kt` | `IReadingHistoryRepository` | Must have `@Inject` constructor |
| `BookmarksRepository` | `data/BookmarksRepository.kt` | `IBookmarkRepository` | Must have `@Inject` constructor |
| `FavouritesRepository` | `data/FavouritesRepository.kt` | `IFavoriteRepository` | Must have `@Inject` constructor |
| `MangaSourcesRepository` | `data/MangaSourcesRepository.kt` | `ILibraryRepository` | Must have `@Inject` constructor |

If any repository is missing, create a minimal placeholder implementation — **no logic** beyond interface compliance.

---

### 2.3 AppSettings.kt

Path:  
`app/src/main/kotlin/org/koitharu/kotatsu/core/prefs/AppSettings.kt`

**Requirements:**
- Annotate with `@Singleton`
- Constructor:
  ```kotlin
  @Inject constructor(context: Context)
  ```
- Do not modify preference keys, schema, or logic.

---

## 3. Gradle Configuration

### 3.1 App-Level (`app/build.gradle`)

```groovy
plugins {
    id 'com.google.dagger.hilt.android'
    id 'com.google.devtools.ksp'
}

dependencies {
    implementation "com.google.dagger:hilt-android:<latest>"
    ksp "com.google.dagger:hilt-compiler:<latest>"

    implementation "androidx.hilt:hilt-work:<latest>"
    ksp "androidx.hilt:hilt-compiler:<latest>"
}
```

### 3.2 Project-Level (`build.gradle`)

```groovy
classpath "com.google.dagger:hilt-android-gradle-plugin:<latest>"
classpath "com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:<latest>"
```

---

## 4. Build and Verification Workflow

1. **Stop and clean Gradle**
   ```bash
   ./gradlew --stop
   ./gradlew clean
   ```
2. **Rebuild using KSP**
   ```bash
   ./gradlew kspDebugKotlin --stacktrace --info
   ```
3. **Assemble for testing**
   ```bash
   ./gradlew assembleDebug
   ```
4. **If build fails:**
   - Open `build/reports/problems/problems-report.html`
   - Resolve missing DI bindings or undefined classes
   - Add minimal code required (no refactors)

---

## 5. AI Agent Execution Rules

When using this manual as instructions for AI-assisted rebuild:

- Treat tasks as **additive integration**, not refactoring.
- Modify only:
  - `/gdrive/` directory
  - `AppModule.kt` (append-only)
- Keep logic modular, reusable, and extendable for future backends (e.g., Dropbox, OneDrive).
- Do **not** alter core app logic, data models, or UI unless strictly necessary.
- Document every exception with `// NOTE:` comments.

---

## 6. Google Drive Sync Strings & UI Integration

### 6.1 Purpose
Integrate user-facing strings and preferences for Google Drive Sync **without modifying the app’s core UI or settings structure**.

---

### 6.2 String Resources
Add these entries to `app/src/main/res/values/strings.xml`.

```xml
<!-- START: ADDED FOR GOOGLE DRIVE SYNC -->
<string name="pref_header_cloud_sync">Cloud Sync</string>
<string name="pref_title_connect_google_drive">Connect to Google Drive</string>
<string name="pref_summary_connect_google_drive_disconnected">Sign in to sync your data</string>
<string name="pref_summary_connect_google_drive_connected">Connected as %1$s</string>
<string name="pref_title_auto_sync">Auto Sync</string>
<string name="pref_summary_auto_sync">Automatically sync data in the background</string>
<string name="pref_title_sync_now">Sync Now</string>
<string name="pref_title_last_synced">Last Synced</string>
<string name="pref_summary_never_synced">Never synced</string>
<string name="pref_summary_last_synced_at">Last synced: %1$s</string>
<string name="pref_title_sync_status">Sync Status</string>
<string name="pref_summary_sync_status_idle">Idle</string>
<string name="pref_title_logout_google_drive">Log Out from Google Drive</string>
<string name="pref_summary_logout_google_drive">Disconnect your Google Drive account</string>

<!-- Google Drive Sync Preferences -->
<string name="pref_header_google_drive_sync">Google Drive Sync</string>
<string name="pref_summary_cloud_sync_status">Displays the latest sync status</string>
<string name="pref_title_google_drive_account">Google Account</string>
<string name="pref_summary_google_drive_account">Choose the account used for Drive backup &amp; sync</string>
<string name="pref_title_enable_google_drive_sync">Enable Google Drive Sync</string>
<string name="pref_summary_enable_google_drive_sync">Automatically back up reading history and favourites to Google Drive</string>
<string name="pref_title_last_sync_time">Last Synced</string>
<string name="pref_summary_last_sync_time">Shows the last successful sync time</string>
<string name="pref_action_sync_now">Sync Now</string>
<string name="pref_summary_sync_now">Manually trigger cloud sync</string>

<string name="sync_toast_sync_started">Sync started…</string>
<string name="sync_toast_sync_successful">Sync completed successfully!</string>
<string name="sync_toast_sync_failed">Sync failed: %1$s</string>
<string name="sync_toast_sign_in_failed">Google Sign-In failed.</string>
<string name="sync_toast_not_connected">Not connected to Google Drive.</string>
<string name="sync_toast_logged_out">Logged out from Google Drive.</string>
<string name="sync_toast_network_error">Network error. Please check your connection.</string>
<string name="sync_toast_google_api_not_connected">Google API not connected. Please reconnect.</string>
<string name="sync_toast_google_api_resolution_required">User action required to resolve Google Drive issue.</string>
<string name="sync_toast_google_api_timeout">Connection timed out with Google Drive.</string>
<string name="sync_toast_data_corruption">Sync failed: Corrupted sync data detected. Try logging out and back in.</string>
<string name="sync_toast_unknown_error">Sync failed: An unexpected error occurred.</string>
<string name="sync_toast_drive_not_initialized">Google Drive service not initialized.</string>
<string name="sync_toast_sign_out_error">Error signing out: %1$s</string>
<!-- END: ADDED FOR GOOGLE DRIVE SYNC -->
```

---

### 6.3 Preferences XML

File: `app/src/main/res/xml/drive_sync_preferences.xml`

```xml
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
    <PreferenceCategory android:title="@string/pref_header_google_drive_sync" />
    <Preference android:key="gd_connect" android:title="@string/pref_title_connect_google_drive" android:summary="@string/pref_summary_connect_google_drive_disconnected" />
    <SwitchPreferenceCompat android:key="gd_auto_sync" android:title="@string/pref_title_auto_sync" android:summary="@string/pref_summary_auto_sync" android:defaultValue="false" />
    <Preference android:key="gd_sync_now" android:title="@string/pref_action_sync_now" android:summary="@string/pref_summary_sync_now" />
    <Preference android:key="gd_last_synced" android:title="@string/pref_title_last_synced" android:summary="@string/pref_summary_never_synced" />
    <Preference android:key="gd_sync_status" android:title="@string/pref_title_sync_status" android:summary="@string/pref_summary_sync_status_idle" />
    <Preference android:key="gd_logout" android:title="@string/pref_title_logout_google_drive" android:summary="@string/pref_summary_logout_google_drive" />
</PreferenceScreen>
```

---

### 6.4 Settings Integration

Add the following entry to `pref_root.xml` or equivalent:

```xml
<Preference
    android:key="open_gdrive_sync"
    android:title="@string/pref_header_cloud_sync"
    android:summary="@string/pref_summary_cloud_sync_status" />
```

Handle click events to open a new fragment loading `drive_sync_preferences.xml`.

---

### 6.5 DriveSyncPreferencesFragment

```kotlin
package org.koitharu.kotatsu.gdrive.ui

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.gdrive.DriveSyncProvider
import javax.inject.Inject

class DriveSyncPreferencesFragment : PreferenceFragmentCompat() {

    @Inject lateinit var driveSyncProvider: DriveSyncProvider

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.drive_sync_preferences, rootKey)

        val connect = findPreference<Preference>("gd_connect")
        val auto = findPreference<SwitchPreferenceCompat>("gd_auto_sync")
        val syncNow = findPreference<Preference>("gd_sync_now")
        val last = findPreference<Preference>("gd_last_synced")
        val status = findPreference<Preference>("gd_sync_status")
        val logout = findPreference<Preference>("gd_logout")

        connect?.setOnPreferenceClickListener {
            driveSyncProvider.signIn(requireActivity())
            true
        }

        syncNow?.setOnPreferenceClickListener {
            driveSyncProvider.syncNow(
                onStart = { toast(getString(R.string.sync_toast_sync_started)) },
                onSuccess = { toast(getString(R.string.sync_toast_sync_successful)) },
                onError = { e -> toast(getString(R.string.sync_toast_sync_failed, e.message ?: "Unknown")) }
            )
            true
        }

        auto?.setOnPreferenceChangeListener { _, newValue ->
            driveSyncProvider.setAutoSyncEnabled(newValue as Boolean)
            true
        }

        logout?.setOnPreferenceClickListener {
            driveSyncProvider.signOut(
                onSuccess = { toast(getString(R.string.sync_toast_logged_out)) },
                onError = { e -> toast(getString(R.string.sync_toast_sign_out_error, e.message ?: "Unknown")) }
            )
            true
        }

        last?.summary = driveSyncProvider.lastSyncedFormatted()
        status?.summary = driveSyncProvider.currentStatusSummary(requireContext())
        connect?.summary = driveSyncProvider.connectedSummary(requireContext())
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
}
```

---

### 6.6 Testing & Validation

1. Run:
   ```bash
   ./gradlew :app:mergeDebugResources
   ```
   Ensure no duplicate strings.
2. Verify:
   - “Cloud Sync” entry appears in Settings.
   - All actions trigger correct toast messages.
3. Confirm:
   - No DI or Hilt build errors.
   - Sync triggers perform as expected.

---

### 6.7 Safety Summary

All GDrive additions must stay within:
- `/gdrive/` package
- `res/values/strings.xml`
- `res/xml/drive_sync_preferences.xml`
- Settings entry (navigation only)

No other files may be modified or replaced.

---

**End of Manual**
# Kotatsu – Google Drive Sync Module Build & Integration Guide (AI Execution Edition)

## Objective
Rebuild the **Google Drive Sync system** for Kotatsu Android Reader, using **Hilt**, **WorkManager**, and **Google Drive AppData**.  
All new code must live inside the `org.koitharu.kotatsu.gdrive` package and integrate seamlessly with existing data repositories.  
The design must be **additive** — no modification of the core Kotatsu logic unless explicitly required.

---

## 1. Core Rules (Safety for AI & Developers)

✅ **DO NOT MODIFY**:
- Any existing repositories, data models, or UI code outside `/gdrive`.
- Any original app architecture under `/core`, `/data`, or `/ui`.

✅ **PERMITTED CHANGES**:
- Add new files under `/gdrive/`
- Append new `@Provides` methods in `AppModule.kt`
- Add string resources & preferences XML
- Add new fragment for GDrive settings UI

✅ **CODE COMMENT CONVENTION**:
Use:
```kotlin
// NOTE: Required for GDrive Sync
```
for every intentional integration change.

---

## 2. System Overview

### 2.1 Modules to Implement
| File | Purpose |
|------|----------|
| `DriveSyncProvider.kt` | Handles upload/download with Google Drive |
| `SettingsSyncTrigger.kt` | Monitors settings & triggers sync events |
| `SyncRegistry.kt` | Event hub for SyncProviders |
| `EncryptionUtil.kt` | AES encryption for synced JSON |
| `models/` | Data model set: `SyncData.kt`, `SyncEvent.kt`, etc. |
| `DriveSyncPreferencesFragment.kt` | Settings screen fragment |
| `drive_sync_preferences.xml` | Preferences layout XML |
| `strings.xml` | UI strings for sync menu |

### 2.2 Dependencies
- Hilt / KSP
- WorkManager
- Google Drive API (`com.google.api-client`, `com.google.apis:drive:v3`)
- Gson for serialization

---

## 3. Folder Structure (Final Layout)

```
app/src/main/kotlin/org/koitharu/kotatsu/gdrive/
│
├── DriveSyncProvider.kt
├── SettingsSyncTrigger.kt
├── SyncRegistry.kt
├── EncryptionUtil.kt
│
└── ui/
    └── drive_sync_preferences.xml
│
├── models
└── models/
    ├── SyncData.kt
    ├── ReadingHistoryItem.kt
    ├── LibraryData.kt
    └── SyncEvent.kt
```

---

## 4. File Implementation Details

### 4.1 DriveSyncProvider.kt
```kotlin
@Singleton
class DriveSyncProvider @Inject constructor(
    private val context: Context,
    private val readingRepo: IReadingHistoryRepository,
    private val bookmarkRepo: IBookmarkRepository,
    private val favoriteRepo: IFavoriteRepository,
    private val appSettings: IAppSettings,
    private val libraryRepo: ILibraryRepository,
    private val encryptionUtil: EncryptionUtil
) : SyncProvider {

    fun syncNow(onStart: () -> Unit, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        try {
            onStart()
            // Upload logic
            onSuccess()
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun signIn(activity: Activity) { /* Google sign-in flow */ }
    fun signOut(onSuccess: () -> Unit, onError: (Exception) -> Unit) { /* Sign-out */ }
    fun setAutoSyncEnabled(enabled: Boolean) { /* Update prefs */ }
}
```

---

### 4.2 SettingsSyncTrigger.kt
```kotlin
@Singleton
class SettingsSyncTrigger @Inject constructor(
    private val appSettings: AppSettings,
    private val syncRegistry: SyncRegistry
) {
    init {
        appSettings.registerListener { key ->
            if (key == AppSettings.KEY_AUTO_SYNC) {
                syncRegistry.sendEvent(SyncEvent.Trigger)
            }
        }
    }
}
```

---

### 4.3 SyncRegistry.kt
```kotlin
@Singleton
object SyncRegistry {
    private val providers = mutableListOf<SyncProvider>()

    fun register(provider: SyncProvider) {
        if (!providers.contains(provider)) providers.add(provider)
    }

    fun sendEvent(event: SyncEvent) {
        providers.forEach { it.onSyncEvent(event) }
    }
}
```

---

### 4.4 EncryptionUtil.kt
```kotlin
@Singleton
class EncryptionUtil @Inject constructor(private val context: Context) {
    private val charset = Charsets.UTF_8

    fun encrypt(data: String): String {
        val key = SecretKeySpec("KotatsuAESKey123".toByteArray(charset), "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return Base64.encodeToString(cipher.doFinal(data.toByteArray(charset)), Base64.NO_WRAP)
    }

    fun decrypt(data: String): String {
        val key = SecretKeySpec("KotatsuAESKey123".toByteArray(charset), "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, key)
        return String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), charset)
    }
}
```

---

## 5. Dependency Injection (Hilt)

Edit:  
`app/src/main/kotlin/org/koitharu/kotatsu/di/AppModule.kt`

Append:
```kotlin
@Provides fun provideSyncRegistry(): SyncRegistry = SyncRegistry
@Provides fun provideEncryptionUtil(util: EncryptionUtil): EncryptionUtil = util
```

Ensure repository bindings exist:
```kotlin
@Provides fun provideReadingHistoryRepository(repo: HistoryRepository): IReadingHistoryRepository = repo
```

---

## 6. UI Integration

### 6.1 Add Settings Screen Entry
**File:** `app/src/main/res/xml/pref_root.xml`
```xml
<Preference
    android:key="open_gdrive_sync"
    android:title="@string/pref_header_cloud_sync"
    android:summary="@string/pref_summary_cloud_sync_status" />
```

### 6.2 New XML: `drive_sync_preferences.xml`
```xml
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
    <PreferenceCategory android:title="@string/pref_header_google_drive_sync" />
    <Preference android:key="gd_connect" android:title="@string/pref_title_connect_google_drive" />
    <SwitchPreferenceCompat android:key="gd_auto_sync" android:title="@string/pref_title_auto_sync" />
    <Preference android:key="gd_sync_now" android:title="@string/pref_action_sync_now" />
    <Preference android:key="gd_logout" android:title="@string/pref_title_logout_google_drive" />
</PreferenceScreen>
```

### 6.3 Fragment
**File:** `DriveSyncPreferencesFragment.kt`  
Inject `DriveSyncProvider` and wire all preferences as shown earlier.

---

## 7. Strings

Add to `res/values/strings.xml`:

```xml
<string name="pref_header_cloud_sync">Cloud Sync</string>
<string name="pref_title_connect_google_drive">Connect to Google Drive</string>
<string name="pref_summary_connect_google_drive_connected">Connected as %1$s</string>
<string name="pref_title_auto_sync">Auto Sync</string>
<string name="pref_action_sync_now">Sync Now</string>
<string name="sync_toast_sync_successful">Sync completed successfully!</string>
<string name="sync_toast_sync_failed">Sync failed: %1$s</string>
```

---

## 8. Build Validation Checklist

- [ ] Run `./gradlew clean assembleDebug`
- [ ] Ensure no `PROCESSING_ERROR` or missing binding
- [ ] Check Preferences UI → “Cloud Sync” visible
- [ ] Verify sign-in, sync, and sign-out flow runs without crash
- [ ] Confirm `SyncRegistry` events trigger properly

---

## 9. AI Agent Instructions

If an AI agent is executing this:
1. Create all files and XMLs listed above.
2. Use provided Kotlin templates.
3. Ensure each class compiles independently.
4. Validate Gradle sync success.
5. Document results in `build_log.txt`.

---

**End of Build Manual**