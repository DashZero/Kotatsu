package org.koitharu.kotatsu.comments

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GunClient @Inject constructor() {
    // ... existing implementation ...

    // Mocked/Placeholder implementations, replace with actual Gun.js logic
    fun subscribeToComments(mangaId: String, onCommentReceived: (Comment) -> Unit) {
        // TODO: Implement Gun.js subscription
    }

    fun sendComment(mangaId: String, comment: Comment) {
        // TODO: Implement Gun.js send comment
    }

    fun deleteComment(mangaId: String, commentId: String) {
        // TODO: Implement Gun.js delete comment
    }

    fun unsubscribeFromComments(mangaId: String) {
        // TODO: Implement Gun.js unsubscribe
    }
}
