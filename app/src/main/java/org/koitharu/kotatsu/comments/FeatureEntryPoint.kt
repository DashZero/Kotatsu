package org.koitharu.kotatsu.comments

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

object FeatureEntryPoint {

    /**
     * The main entry point for the comments feature.
     * This composable should be mounted in the manga details screen.
     *
     * @param mangaId The ID of the manga for which to display comments.
     * @param modifier Modifier for the composable.
     */
    @Composable
    fun CommentsEntryPoint(mangaId: String, modifier: Modifier = Modifier) {
        // Check if comments are enabled before rendering the screen
        if (CommentsSettings.isEnabled.value) {
            CommentScreen(mangaId = mangaId)
        }
    }

    /**
     * Fallback action for an overflow menu item.
     * This function can be called to navigate to a separate comments screen
     * if direct embedding is not feasible.
     *
     * Note: This function does not provide navigation itself, but rather
     * returns a lambda that can be used within a navigation system.
     * The actual navigation implementation would depend on the core app's
     * navigation framework (e.g., NavController).
     *
     * @param mangaId The ID of the manga.
     * @return A lambda that takes a Context and can be used to launch a new Activity
     *         or navigate to a new Composable route that hosts the CommentScreen.
     */
    fun getOverflowMenuAction(mangaId: String): (@Composable () -> Unit) {
        return { CommentScreen(mangaId = mangaId) }
    }
}
