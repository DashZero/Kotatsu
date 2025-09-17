package org.koitharu.kotatsu.comments

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class CommentRepository(private val context: Context, private val gunClient: GunClient, private val scope: CoroutineScope) {

    private val commentsDir: File = File(context.filesDir, "comments")
    private val indexFile: File = File(commentsDir, "index.json")

    // In-memory cache for comments per mangaId
    private val mangaCommentsCache = ConcurrentHashMap<String, MutableStateFlow<List<Comment>>>()

    init {
        if (!commentsDir.exists()) {
            commentsDir.mkdirs()
        }
        scope.launch(Dispatchers.IO) {
            gunClient.events.collect { event ->
                when (event) {
                    is GunClient.GunEvent.NewComment -> handleNewComment(event.comment)
                    is GunClient.GunEvent.CommentDeleted -> handleDeleteComment(event.commentId)
                    is GunClient.GunEvent.CommentSentAck -> handleCommentSentAck(event.comment)
                    is GunClient.GunEvent.CommentDeletedAck -> handleDeleteCommentAck(event.commentId)
                    is GunClient.GunEvent.CommentReportedAck -> handleCommentReportedAck(event.commentId, event.newReportCount)
                    else -> { /* Handle other events if necessary */ }
                }
            }
        }
    }

    fun getCommentsFlow(mangaId: String): Flow<List<Comment>> {
        return mangaCommentsCache.getOrPut(mangaId) { MutableStateFlow(emptyList()) }.asStateFlow()
    }

    suspend fun loadAndSubscribe(mangaId: String) {
        scope.launch(Dispatchers.IO) {
            // 1. Load from local cache
            val localComments = readCommentsFromFile(mangaId)
            mangaCommentsCache.getOrPut(mangaId) { MutableStateFlow(emptyList()) }.update { localComments }

            // 2. Subscribe to Gun.js
            gunClient.subscribe(mangaId)
        }
    }

    suspend fun unsubscribe(mangaId: String) {
        scope.launch(Dispatchers.IO) {
            gunClient.unsubscribe()
            mangaCommentsCache.remove(mangaId) // Clear cache for this manga
        }
    }

    suspend fun sendComment(comment: Comment) {
        scope.launch(Dispatchers.IO) {
            // Apply locally first
            mangaCommentsCache[comment.mangaId]?.update { currentComments ->
                (currentComments + comment).sortedBy { it.timestamp }
            }
            writeCommentsToFile(comment.mangaId, mangaCommentsCache[comment.mangaId]?.value ?: emptyList())

            // Then send to Gun
            gunClient.sendComment(comment.mangaId, comment)
        }
    }

    suspend fun deleteComment(mangaId: String, commentId: String) {
        scope.launch(Dispatchers.IO) {
            // Apply locally first (soft delete)
            mangaCommentsCache[mangaId]?.update { currentComments ->
                currentComments.map { if (it.id == commentId) it.copy(deleted = true) else it }
            }
            writeCommentsToFile(mangaId, mangaCommentsCache[mangaId]?.value ?: emptyList())

            // Then send to Gun
            gunClient.deleteComment(mangaId, commentId)
        }
    }

    suspend fun reportComment(mangaId: String, commentId: String) {
        scope.launch(Dispatchers.IO) {
            // Apply locally first (increment report count)
            mangaCommentsCache[mangaId]?.update { currentComments ->
                currentComments.map { if (it.id == commentId) it.copy(reportCount = it.reportCount + 1) else it }
            }
            writeCommentsToFile(mangaId, mangaCommentsCache[mangaId]?.value ?: emptyList())

            // Then send to Gun
            gunClient.reportComment(mangaId, commentId)
        }
    }

    private fun handleNewComment(newComment: Comment) {
        mangaCommentsCache[newComment.mangaId]?.update { currentComments ->
            val existing = currentComments.find { it.id == newComment.id }
            if (existing == null) {
                (currentComments + newComment).sortedBy { it.timestamp }
            } else {
                // Merge or update existing comment (e.g., reportCount might change)
                currentComments.map { if (it.id == newComment.id) newComment else it }
            }
        }
        scope.launch(Dispatchers.IO) { writeCommentsToFile(newComment.mangaId, mangaCommentsCache[newComment.mangaId]?.value ?: emptyList()) }
    }

    private fun handleDeleteComment(commentId: String) {
        // Find which manga this comment belongs to (could be optimized with an in-memory map if needed)
        mangaCommentsCache.forEach { (mangaId, flow) ->
            flow.update { currentComments ->
                val updated = currentComments.map { if (it.id == commentId) it.copy(deleted = true) else it }
                if (updated != currentComments) {
                    scope.launch(Dispatchers.IO) { writeCommentsToFile(mangaId, updated) }
                }
                updated
            }
        }
    }

    private fun handleCommentSentAck(comment: Comment) {
        // No specific action needed here, as local state was already updated and Gun will eventually send NewComment
        // This ACK can be used for UI feedback if needed.
    }

    private fun handleDeleteCommentAck(commentId: String) {
        // No specific action needed here, as local state was already updated and Gun will eventually send CommentDeleted
    }

    private fun handleCommentReportedAck(commentId: String, newReportCount: Int) {
        // Update local state with the new report count
        mangaCommentsCache.forEach { (mangaId, flow) ->
            flow.update { currentComments ->
                val updated = currentComments.map { if (it.id == commentId) it.copy(reportCount = newReportCount) else it }
                if (updated != currentComments) {
                    scope.launch(Dispatchers.IO) { writeCommentsToFile(mangaId, updated) }
                }
                updated
            }
        }
    }

    private fun getMangaCommentsFile(mangaId: String): File {
        val hash = mangaId.hashCode().toString()
        val subDir = File(commentsDir, hash.take(2)) // Use first two chars of hash for subfolder
        if (!subDir.exists()) {
            subDir.mkdirs()
        }
        return File(subDir, "$mangaId.json")
    }

    private fun readCommentsFromFile(mangaId: String): List<Comment> {
        val file = getMangaCommentsFile(mangaId)
        if (!file.exists()) {
            return emptyList()
        }
        return try {
            val jsonArray = JSONArray(file.readText())
            (0 until jsonArray.length()).mapNotNull { i ->
                try {
                    Comment.fromJson(jsonArray.getJSONObject(i))
                } catch (e: Exception) {
                    println("Error parsing comment JSON: $e")
                    null
                }
            }
        } catch (e: Exception) {
            println("Error reading comments from file: $e")
            emptyList()
        }
    }

    private fun writeCommentsToFile(mangaId: String, comments: List<Comment>) {
        val file = getMangaCommentsFile(mangaId)
        val prunedComments = comments.sortedByDescending { it.timestamp }.take(500).sortedBy { it.timestamp } // Keep last 500, sorted by timestamp
        val jsonArray = JSONArray(prunedComments.map { it.toJson() })
        try {
            file.writeText(jsonArray.toString(2)) // Pretty print for readability
        } catch (e: Exception) {
            println("Error writing comments to file: $e")
        }
    }

    // TODO: Implement global index file (index.json) for last seen ids/metadata for quick load.
    // This is a stretch goal for now, focusing on core functionality first.
}
