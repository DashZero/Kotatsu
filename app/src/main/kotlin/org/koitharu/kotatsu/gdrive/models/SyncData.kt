package org.koitharu.kotatsu.gdrive.models

import com.google.gson.annotations.SerializedName
import java.time.Instant

data class SyncData(
    @SerializedName("schema_version") var schema_version: Int = CURRENT_SCHEMA_VERSION,
    @SerializedName("updated_at") var updated_at: Instant = Instant.EPOCH,
    @SerializedName("reading_history") val reading_history: MutableMap<String, ReadingHistoryItem> = mutableMapOf(),
    @SerializedName("bookmarks") val bookmarks: MutableList<BookmarkItem> = mutableListOf(),
    @SerializedName("favorites") val favorites: MutableList<String> = mutableListOf(),
    @SerializedName("settings") val settings: MutableMap<String, String> = mutableMapOf(), // Generic settings map
    @SerializedName("library") var library: LibraryData = LibraryData()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        fun empty() = SyncData(updated_at = Instant.EPOCH)
    }
}
