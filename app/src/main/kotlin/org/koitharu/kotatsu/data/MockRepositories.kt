package org.koitharu.kotatsu.data

import org.koitharu.kotatsu.gdrive.models.LibraryData
import org.koitharu.kotatsu.gdrive.models.ReadingHistoryItem
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// --- Interfaces (these should ideally be in your core data module) ---

interface IReadingHistoryRepository {
    suspend fun getAllHistory(): Map<String, ReadingHistoryItem>
    suspend fun updateReadingProgress(mangaId: String, chapter: Int, page: Int)
}

interface IBookmarkRepository {
    suspend fun getAllBookmarks(): List<BookmarkItem>
    suspend fun addBookmark(mangaId: String)
    suspend fun removeBookmark(mangaId: String)
}

interface IFavoriteRepository {
    suspend fun getAllFavorites(): List<String>
    suspend fun addFavorite(mangaId: String)
    suspend fun removeFavorite(mangaId: String)
}

interface IAppSettings {
    suspend fun getCurrentSettings(): Map<String, String>
    suspend fun setSetting(key: String, value: String)
}

interface ILibraryRepository {
    suspend fun getLibraryData(): LibraryData
    // Add methods for modifying library data if needed
}

// --- Mock Implementations (REPLACE THESE WITH YOUR REAL ONES) ---

@Singleton
class MockReadingHistoryRepository @Inject constructor() : IReadingHistoryRepository {
    private val history = mutableMapOf<String, ReadingHistoryItem>()

    override suspend fun getAllHistory(): Map<String, ReadingHistoryItem> {
        return history.toMap()
    }

    override suspend fun updateReadingProgress(mangaId: String, chapter: Int, page: Int) {
        history[mangaId] = ReadingHistoryItem(mangaId, chapter, page, Instant.now())
    }
}

@Singleton
class MockBookmarkRepository @Inject constructor() : IBookmarkRepository {
    private val bookmarks = mutableListOf<String>()

    override suspend fun getAllBookmarks(): List<String> {
        return bookmarks.toList()
    }

    override suspend fun addBookmark(mangaId: String) {
        if (mangaId !in bookmarks) bookmarks.add(mangaId)
    }

    override suspend fun removeBookmark(mangaId: String) {
        bookmarks.remove(mangaId)
    }
}

@Singleton
class MockFavoriteRepository @Inject constructor() : IFavoriteRepository {
    private val favorites = mutableListOf<String>()

    override suspend fun getAllFavorites(): List<String> {
        return favorites.toList()
    }

    override suspend fun addFavorite(mangaId: String) {
        if (mangaId !in favorites) favorites.add(mangaId)
    }

    override suspend fun removeFavorite(mangaId: String) {
        favorites.remove(mangaId)
    }
}

@Singleton
class MockAppSettings @Inject constructor() : IAppSettings {
    private val settings = mutableMapOf<String, String>()

    override suspend fun getCurrentSettings(): Map<String, String> {
        return settings.toMap()
    }

    override suspend fun setSetting(key: String, value: String) {
        settings[key] = value
    }
}

@Singleton
class MockLibraryRepository @Inject constructor() : ILibraryRepository {
    private var libraryData = LibraryData(sources = listOf("Mock Source 1"), categories = listOf("Mock Category A"))

    override suspend fun getLibraryData(): LibraryData {
        return libraryData.copy()
    }
    // You might add methods here to simulate changes for testing library sync
}
