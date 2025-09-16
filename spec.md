
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

### What is not done

- **Gun.js Client:** The `GunClient.kt` is a placeholder. A real Gun.js client needs to be implemented.
- **Room Database:** The Room database class and the Hilt module for providing the `CommentDao` are not created.
- **UI:** The UI for the comments section in the `DetailsActivity` is not implemented. The project uses Android Views, not Jetpack Compose.
- **Resources:** Placeholder icons and strings have been used. These need to be replaced with actual resources.

## File Structure

- `app/src/main/kotlin/org/koitharu/kotatsu/comments/Comment.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/comments/CommentDao.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/comments/CommentRepository.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/comments/CommentViewModel.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/comments/GunClient.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/comments/Moderation.kt`
- `app/src/main/res/xml/pref_comments.xml`
- `app/src/main/kotlin/org/koitharu/kotatsu/settings/CommentsSettingsFragment.kt`

## Next Steps

1. **Fix the build error:** Find the correct path to the Android SDK and update the `local.properties` file.
2. **Create the Room database:**
    - Create a Room database class that includes the `Comment` entity.
    - Create a Hilt module to provide the `CommentDao` to the `CommentRepository`.
3. **Implement the Gun.js client:**
    - Choose a Gun.js client library for Java/Kotlin.
    - Implement the methods in `GunClient.kt` to connect to a Gun.js peer and handle real-time comment synchronization.
4. **Implement the UI:**
    - Create an XML layout for the comments section in `DetailsActivity`.
    - Use a `RecyclerView` to display the comments.
    - Use an `EditText` and a `Button` to allow users to add new comments.
    - Connect the UI to the `CommentViewModel` to display comments and handle user actions.
5. **Add resources:**
    - Add the necessary icons and strings to the project.
