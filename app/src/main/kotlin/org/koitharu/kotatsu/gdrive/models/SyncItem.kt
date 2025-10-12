package org.koitharu.kotatsu.gdrive.models

import com.google.gson.annotations.SerializedName
import java.time.Instant

data class ReadingHistoryItem(
    @SerializedName("manga_id") val mangaId: String,
    @SerializedName("chapter") val chapter: Int,
    @SerializedName("page") val page: Int,
    @SerializedName("updated_at") val updated_at: Instant
)

data class BookmarkItem(
    @SerializedName("manga_id") val mangaId: String,
    @SerializedName("chapter_id") val chapterId: Long,
    @SerializedName("page") val page: Int,
    @SerializedName("created_at") val createdAt: Instant
)

data class LibraryData(
    @SerializedName("sources") val sources: List<String> = emptyList(),
    @SerializedName("categories") val categories: List<String> = emptyList()
)

data class SettingsData(
    // Using Map<String, String> for simplicity, can be more specific if needed
    @SerializedName("app_settings") val appSettings: Map<String, String> = emptyMap()
)
