package org.koitharu.kotatsu.comments

import org.json.JSONObject
import java.util.UUID

data class Comment(
    val id: String = UUID.randomUUID().toString(),
    val mangaId: String,
    val userId: String,
    val userName: String,
    val avatarUrl: String?,
    val text: String,
    val timestamp: Long,
    val deleted: Boolean = false,
    val reportCount: Int = 0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("mangaId", mangaId)
            put("userId", userId)
            put("userName", userName)
            put("avatarUrl", avatarUrl)
            put("text", text)
            put("timestamp", timestamp)
            put("deleted", deleted)
            put("reportCount", reportCount)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Comment {
            return Comment(
                id = json.optString("id", UUID.randomUUID().toString()),
                mangaId = json.getString("mangaId"),
                userId = json.getString("userId"),
                userName = json.getString("userName"),
                avatarUrl = json.optString("avatarUrl", null),
                text = json.getString("text"),
                timestamp = json.getLong("timestamp"),
                deleted = json.optBoolean("deleted", false),
                reportCount = json.optInt("reportCount", 0)
            )
        }
    }
}
