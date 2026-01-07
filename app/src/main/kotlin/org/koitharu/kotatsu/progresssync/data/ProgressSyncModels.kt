package org.koitharu.kotatsu.progresssync.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProgressSyncFile(
	@SerialName("schema_version") val schemaVersion: Int = 1,
	@SerialName("device_id") val deviceId: String,
	@SerialName("device_name") val deviceName: String,
	@SerialName("exported_at") val exportedAt: String,
	@SerialName("items") val items: List<ProgressSyncItem>,
)

@Serializable
data class ProgressSyncItem(
	@SerialName("manga_key") val mangaKey: String,
	@SerialName("source") val source: String,
	@SerialName("title") val title: String,
	@SerialName("url") val url: String? = null,
	@SerialName("public_url") val publicUrl: String? = null,
	@SerialName("cover_url") val coverUrl: String? = null,
	@SerialName("progress") val progress: ProgressSyncProgress,
	@SerialName("flags") val flags: ProgressSyncFlags = ProgressSyncFlags(),
	@SerialName("bookmarks") val bookmarks: List<ProgressSyncBookmark>? = null,
)

@Serializable
data class ProgressSyncProgress(
	@SerialName("chapter_id") val chapterId: Long? = null,
	@SerialName("chapter_number") val chapterNumber: String? = null,
	@SerialName("page") val page: Int,
	@SerialName("percent") val percent: Float? = null,
	@SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class ProgressSyncFlags(
	@SerialName("favorite") val favorite: Boolean = false,
	@SerialName("bookmarked") val bookmarked: Boolean = false,
)

@Serializable
data class ProgressSyncBookmark(
	@SerialName("page_id") val pageId: Long,
	@SerialName("chapter_id") val chapterId: Long,
	@SerialName("page") val page: Int,
	@SerialName("scroll") val scroll: Int,
	@SerialName("image_url") val imageUrl: String,
	@SerialName("created_at") val createdAt: String,
	@SerialName("percent") val percent: Float,
)
