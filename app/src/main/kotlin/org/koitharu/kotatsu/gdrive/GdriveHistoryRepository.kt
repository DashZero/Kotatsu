package org.koitharu.kotatsu.gdrive

import org.koitharu.kotatsu.data.IReadingHistoryRepository
import org.koitharu.kotatsu.gdrive.models.ReadingHistoryItem
import org.koitharu.kotatsu.history.data.HistoryRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GdriveHistoryRepository @Inject constructor(
    private val historyRepository: HistoryRepository
) : IReadingHistoryRepository {

    override suspend fun getAllHistory(): Map<String, ReadingHistoryItem> {
        return historyRepository.getList(0, Int.MAX_VALUE).associate { manga ->
            val history = historyRepository.getOne(manga)
            manga.id.toString() to ReadingHistoryItem(
                mangaId = manga.id.toString(),
                chapter = history?.chapterId?.toInt() ?: 0,
                page = history?.page ?: 0,
                timestamp = Instant.ofEpochMilli(history?.updatedAt ?: 0)
            )
        }
    }

    override suspend fun updateReadingProgress(mangaId: String, chapter: Int, page: Int) {
        // This function is for updating from the sync, not for local updates.
        // The sync logic will handle updating the local database.
    }
}
