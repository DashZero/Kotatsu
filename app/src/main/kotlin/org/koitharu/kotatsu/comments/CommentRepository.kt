
package org.koitharu.kotatsu.comments

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach

class CommentRepository(private val commentDao: CommentDao, private val gunClient: GunClient) {

    fun getComments(mangaId: String): Flow<List<Comment>> {
        val localComments = commentDao.getComments(mangaId)
        val remoteComments = gunClient.subscribe(mangaId)

        return combine(localComments, remoteComments) { local, remote ->
            (local + remote).distinctBy { it.id }.sortedByDescending { it.timestamp }
        }.onEach { comments ->
            // Prune comments if there are more than 1000
            if (comments.size > 1000) {
                val commentsToPrune = comments.sortedBy { it.timestamp }.take(comments.size - 1000)
                commentDao.delete(commentsToPrune.map { it.id })
            }

            // Cache the latest 500 comments
            val commentsToCache = comments.sortedByDescending { it.timestamp }.take(500)
            commentDao.insertAll(commentsToCache)
        }
    }

    suspend fun sendComment(comment: Comment) {
        val sanitizedText = Moderation.sanitize(comment.text)
        if (sanitizedText.isNotEmpty()) {
            val sanitizedComment = comment.copy(text = sanitizedText)
            commentDao.insertAll(listOf(sanitizedComment))
            gunClient.sendComment(sanitizedComment)
        }
    }

    suspend fun deleteComment(comment: Comment) {
        val updatedComment = comment.copy(deleted = true)
        commentDao.insertAll(listOf(updatedComment))
        gunClient.deleteComment(updatedComment)
    }

    suspend fun reportComment(comment: Comment) {
        val updatedComment = comment.copy(reportCount = comment.reportCount + 1)
        commentDao.insertAll(listOf(updatedComment))
        gunClient.reportComment(updatedComment)

        if (updatedComment.reportCount >= 5) {
            val userComments = commentDao.getCommentsByUser(updatedComment.userId)
            val deletedComments = userComments.map { it.copy(deleted = true) }
            commentDao.insertAll(deletedComments)
        }
    }

    fun unsubscribe(mangaId: String) {
        gunClient.unsubscribe(mangaId)
    }
}
