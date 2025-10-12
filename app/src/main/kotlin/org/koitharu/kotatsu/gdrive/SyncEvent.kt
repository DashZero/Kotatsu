package org.koitharu.kotatsu.gdrive

sealed class SyncEvent {
    data class PageChanged(val mangaId: String, val chapter: Int, val page: Int) : SyncEvent()
    data class ChapterCompleted(val mangaId: String, val chapter: Int) : SyncEvent()
    data class BookmarkAdded(val mangaId: String) : SyncEvent()
    data class BookmarkRemoved(val mangaId: String) : SyncEvent()
    data class FavoriteAdded(val mangaId: String) : SyncEvent()
    data class FavoriteRemoved(val mangaId: String) : SyncEvent()
    object SettingsChanged : SyncEvent()
    object LibraryChanged : SyncEvent()
}
