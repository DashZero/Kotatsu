
package org.koitharu.kotatsu.comments

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * This is a placeholder for a real Gun.js client.
 * A real implementation would require a Gun.js client library for Kotlin/Java.
 * This class simulates the behavior of a Gun.js client.
 */
object GunClient {

    private val comments = mutableMapOf<String, MutableList<Comment>>()
    private val commentFlow = MutableStateFlow<List<Comment>>(emptyList())

    fun subscribe(mangaId: String): Flow<List<Comment>> {
        val mangaComments = comments.getOrPut(mangaId) { mutableListOf() }
        commentFlow.value = mangaComments
        return commentFlow
    }

    fun unsubscribe(mangaId: String) {
        // In a real implementation, you would unsubscribe from the Gun.js node.
    }

    fun sendComment(comment: Comment) {
        val mangaComments = comments.getOrPut(comment.mangaId) { mutableListOf() }
        mangaComments.add(comment)
        commentFlow.value = mangaComments.toList() // Notify subscribers
    }

    fun deleteComment(comment: Comment) {
        val mangaComments = comments[comment.mangaId]
        mangaComments?.let { list ->
            val index = list.indexOfFirst { it.id == comment.id }
            if (index != -1) {
                list[index] = list[index].copy(deleted = true)
                commentFlow.value = list.toList() // Notify subscribers
            }
        }
    }

    fun reportComment(comment: Comment) {
        val mangaComments = comments[comment.mangaId]
        mangaComments?.let { list ->
            val index = list.indexOfFirst { it.id == comment.id }
            if (index != -1) {
                val updatedComment = list[index].copy(reportCount = list[index].reportCount + 1)
                list[index] = updatedComment
                commentFlow.value = list.toList() // Notify subscribers

                if (updatedComment.reportCount >= 5) {
                    // Auto-ban user and delete all their comments
                    val userId = updatedComment.userId
                    comments.values.forEach { commentList ->
                        commentList.forEachIndexed { i, c ->
                            if (c.userId == userId) {
                                commentList[i] = c.copy(deleted = true)
                            }
                        }
                    }
                    commentFlow.value = list.toList() // Notify subscribers
                }
            }
        }
    }
}
