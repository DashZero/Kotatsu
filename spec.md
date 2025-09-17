
# Comments Feature Specification

## Overview

This document outlines the implementation of a modular comment/chat system for the Kotatsu app.

## Core Requirements

- Backend uses Gun.js.
- Each manga has its own “room” identified by mangaId.
- Comments sync in real time across all online users.
- Offline mode: cached comments are visible.
- On-demand sync: only subscribe to comments when a manga page is opened.

## Implementation Details

### What has been done

- **Data Models:** The `Comment.kt` data model has been created and annotated as a Room entity.
- **Business Logic:** The `Moderation.kt`, `CommentRepository.kt`, and `CommentViewModel.kt` have been created.
- **Database:** The `CommentDao.kt` interface has been created with the necessary queries for Room database.
- **Settings:** The settings screen for the comments feature has been created as a preference XML file (`pref_comments.xml`) and a corresponding fragment (`CommentsSettingsFragment.kt`). The settings screen has been integrated into the main settings screen (`pref_root.xml`).
- **Directory Structure:** All the files for the comments module have been placed in the `app/src/main/kotlin/org/koitharu/kotatsu/comments` directory.

### Work in Progress / Partially Done

- **Gun.js Client:** The `GunClient.kt` is a placeholder. A real Gun.js client needs to be implemented.
- **Hilt Integration & Database Setup:**
    - `AppDatabase.kt` (existing Room DB) has been updated to include `Comment` entity and `commentDao()` method. Migration was handled with fallbackToDestructiveMigration for development.
    - `GunClient.kt` and `CommentRepository.kt` are being refactored to use Hilt (`@Inject constructor`, `@Singleton`, `mangaId` parameter passing adjusted).
    - A Hilt module (`DatabaseModule.kt`) will be created to provide `AppDatabase` and `CommentDao`.
    - `CommentViewModel.kt` will be refactored to use `@HiltViewModel` and `SavedStateHandle`.
- **UI (Android Views):**
    - Layout files created: `fragment_comments.xml`, `item_comment.xml`, `item_comment_placeholder.xml`, `drawable/ic_flag.xml`.
    - `CommentsFragment.kt` is being created to manage the comments UI and interact with `CommentViewModel`.
    - `CommentAdapter.kt` (RecyclerView adapter) still needs to be created.
    - Integration of `CommentsFragment` into `DetailsActivity` is pending.

## File Structure

- `app/src/main/kotlin/org/koitharu/kotatsu/comments/Comment.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/comments/CommentDao.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/comments/CommentRepository.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/comments/CommentViewModel.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/comments/GunClient.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/comments/Moderation.kt`
- `app/src/main/res/xml/pref_comments.xml`
- `app/src/main/kotlin/org/koitharu/kotatsu/settings/CommentsSettingsFragment.kt`
- `app/src/main/res/layout/fragment_comments.xml` (New)
- `app/src/main/res/layout/item_comment.xml` (New)
- `app/src/main/res/layout/item_comment_placeholder.xml` (New)
- `app/src/main/res/drawable/ic_flag.xml` (New)
- `app/src/main/kotlin/org/koitharu/kotatsu/comments/ui/CommentsFragment.kt` (New - In Progress)

## Next Steps

1.  **Complete Hilt Integration & Database Setup (In Progress):**
    -   Finalize Hilt annotations for `GunClient.kt`, `CommentRepository.kt`.
    -   Create `DatabaseModule.kt` to provide `AppDatabase` and `CommentDao`.
    -   Refactor `CommentViewModel.kt` for Hilt (`@HiltViewModel`, `SavedStateHandle`).
2.  **Implement the Gun.js client (Pending):**
    -   Choose a Gun.js client library for Java/Kotlin.
    -   Implement the methods in `GunClient.kt` to connect to a Gun.js peer and handle real-time comment synchronization.
3.  **Complete UI Implementation (In Progress):**
    -   Finalize `CommentsFragment.kt` (setup RecyclerView, ViewModel interaction).
    -   Create `CommentAdapter.kt` for the RecyclerView.
    -   Add `FragmentContainerView` to `activity_details.xml` and load `CommentsFragment`.
    -   Connect UI elements in `CommentsFragment` to `CommentViewModel` methods (send, delete, report).
    -   Implement avatar loading in `CommentAdapter`.
    -   Implement moderation UI/logic (delete/report buttons in `item_comment.xml` and handling in fragment/VM).
