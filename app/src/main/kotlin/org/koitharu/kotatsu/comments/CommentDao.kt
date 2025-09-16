
package org.koitharu.kotatsu.comments

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CommentDao {

    @Query("SELECT * FROM comments WHERE mangaId = :mangaId ORDER BY timestamp DESC")
    fun getComments(mangaId: String): Flow<List<Comment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(comments: List<Comment>)

    @Query("DELETE FROM comments WHERE id IN (:commentIds)")
    suspend fun delete(commentIds: List<String>)

    @Query("SELECT * FROM comments WHERE userId = :userId")
    suspend fun getCommentsByUser(userId: String): List<Comment>
}
