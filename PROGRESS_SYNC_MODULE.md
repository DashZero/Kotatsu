# Progress Sync Module (Reading Progress JSON Sync)

This module isolates reading-progress sync from core settings and scheduling logic.
It relies on existing app infrastructure (preferences, WorkManager, Room observers)
but keeps most of the implementation under `app/src/main/kotlin/org/koitharu/kotatsu/progresssync`.

## What It Does
- Exports/imports reading progress to a JSON file in a user-selected SAF folder.
- Supports manual and automatic sync via WorkManager.
- Triggers debounced exports when reading history changes.

## Key Entry Points
- Settings UI: `app/src/main/kotlin/org/koitharu/kotatsu/progresssync/ui/ProgressSyncSettingsFragment.kt`
- Repository: `app/src/main/kotlin/org/koitharu/kotatsu/progresssync/data/ProgressSyncRepository.kt`
- Worker + scheduler: `app/src/main/kotlin/org/koitharu/kotatsu/progresssync/work/ProgressSyncWorker.kt`
- Settings storage: `app/src/main/kotlin/org/koitharu/kotatsu/progresssync/prefs/ProgressSyncSettings.kt`

## New Module Wiring
- Dagger module: `app/src/main/kotlin/org/koitharu/kotatsu/progresssync/ProgressSyncModule.kt`
  - Adds a DB observer and lifecycle initializer via multibindings.
- History observer: `app/src/main/kotlin/org/koitharu/kotatsu/progresssync/domain/ProgressSyncHistoryObserver.kt`
  - Debounces export when the history table changes.
- Schedule manager: `app/src/main/kotlin/org/koitharu/kotatsu/progresssync/work/ProgressSyncScheduleManager.kt`
  - Watches progress-sync prefs and schedules/unschedules the periodic worker.
- App initializer: `app/src/main/kotlin/org/koitharu/kotatsu/progresssync/ProgressSyncInitializer.kt`
  - Starts the schedule manager on first activity creation.

## Minimal Core Touch Points
The module plugs into existing app infrastructure without adding new core settings
or modifying base app startup logic:
- Uses the shared preference store via its own `ProgressSyncSettings`.
- Registers observers and lifecycle callbacks through Dagger multibindings.
- Uses existing WorkManager integration already configured in the app.

## Settings/Resources
- Pref XML: `app/src/main/res/xml/pref_progress_sync.xml`
- Strings/arrays: `app/src/main/res/values/strings.xml`, `app/src/main/res/values/arrays.xml`
