package org.koitharu.kotatsu.gdrive

import org.koitharu.kotatsu.gdrive.models.SyncEvent

interface SyncProvider {
    val isEnabled: Boolean
    suspend fun syncNow()
    fun onEvent(event: SyncEvent)
}
