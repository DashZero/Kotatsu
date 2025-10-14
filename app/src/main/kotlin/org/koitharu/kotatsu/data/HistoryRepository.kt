package org.koitharu.kotatsu.data

import org.koitharu.kotatsu.gdrive.GdriveHistoryRepository
import javax.inject.Inject

class HistoryRepository @Inject constructor(
    private val gdriveHistoryRepository: GdriveHistoryRepository
) : IReadingHistoryRepository by gdriveHistoryRepository {
    // This class acts as a proxy to GdriveHistoryRepository to satisfy the DI in AppModule.
    // If other implementations of IReadingHistoryRepository are needed, they should be
    // added here or managed by AppModule's DI.
}
