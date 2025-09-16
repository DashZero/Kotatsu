
package org.koitharu.kotatsu.comments

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "comments",
    indices = [
        Index(value = ["mangaId"]),
        Index(value = ["userId"])
    ]
)
data class Comment(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val mangaId: String,
    val userId: String,
    val userName: String,
    val avatarUrl: String?,
    val text: String,
    val timestamp: Long,
    val deleted: Boolean = false,
    val reportCount: Int = 0
)
