package org.koitharu.kotatsu.gdrive

import android.util.Log
import org.koitharu.kotatsu.gdrive.models.SyncEvent

object SyncRegistry {

    private val TAG = "SyncRegistry"
    private var provider: SyncProvider? = null

    fun register(syncProvider: SyncProvider) {
        if (provider == null) {
            provider = syncProvider
            Log.d(TAG, "SyncProvider registered: ${syncProvider::class.simpleName}")
        } else {
            Log.w(TAG, "Attempted to register multiple SyncProviders. Only one is supported.")
        }
    }

    fun sendEvent(event: SyncEvent) {
        provider?.onEvent(event) ?: Log.w(TAG, "No SyncProvider registered to handle event: $event")
    }
}
