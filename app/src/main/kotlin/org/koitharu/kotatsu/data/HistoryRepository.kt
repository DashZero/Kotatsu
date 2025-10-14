package org.koitharu.kotatsu.data

import org.koitharu.kotatsu.gdrive.GdriveHistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val gdriveHistoryRepository: GdriveHistoryRepository
) : IReadingHistoryRepository {
    override suspend fun getAllHistory(): Map<String, ReadingHistoryItem> {
        return gdriveHistoryRepository.getAllHistory()
    }

    override suspend fun insertHistory(item: ReadingHistoryItem) {
        gdriveHistoryRepository.insertHistory(item)
    }

    override suspend fun deleteHistory(itemId: String) {
        gdriveHistoryRepository.deleteHistory(itemId)
    }

    override suspend fun clearHistory() {
        gdriveHistoryRepository.clearHistory()
    }
}
