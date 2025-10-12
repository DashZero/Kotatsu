package org.koitharu.kotatsu.gdrive

import kotlinx.coroutines.flow.first
import org.koitharu.kotatsu.bookmarks.domain.BookmarksRepository
import org.koitharu.kotatsu.data.IBookmarkRepository
import org.koitharu.kotatsu.gdrive.models.BookmarkItem
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GdriveBookmarkRepository @Inject constructor(
    private val bookmarksRepository: BookmarksRepository
) : IBookmarkRepository {

    override suspend fun getAllBookmarks(): List<BookmarkItem> {
        return bookmarksRepository.observeBookmarks().first().flatMap { (manga, bookmarks) ->
            bookmarks.map {
                BookmarkItem(
                    mangaId = manga.id.toString(),
                    chapterId = it.chapterId,
                    page = it.page,
                    createdAt = Instant.ofEpochMilli(it.createdAt)
                )
            }
        }
    }

    override suspend fun addBookmark(mangaId: String) {
        // This function is for updating from the sync, not for local updates.
    }

    override suspend fun removeBookmark(mangaId: String) {
        // This function is for updating from the sync, not for local updates.
    }
}
