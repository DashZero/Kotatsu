package org.koitharu.kotatsu.comments

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentRepository @Inject constructor(
    private val commentDao: CommentDao,
    private val gunClient: GunClient
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun getComments(mangaId: String): Flow<List<Comment>> {
        gunClient.subscribeToComments(mangaId) { comment ->
            repositoryScope.launch {
                commentDao.insertComment(comment)
            }
        }
        return commentDao.getCommentsByMangaId(mangaId)
    }

    suspend fun sendComment(comment: Comment) {
        gunClient.sendComment(comment.mangaId, comment)
    }

    suspend fun deleteComment(comment: Comment) {
        gunClient.deleteComment(comment.mangaId, comment.id)
        commentDao.deleteComment(comment)
    }

    suspend fun reportComment(comment: Comment) {
        // TODO: Implement reporting logic
    }

    fun unsubscribe(mangaId: String) {
        gunClient.unsubscribeFromComments(mangaId)
    }
}
