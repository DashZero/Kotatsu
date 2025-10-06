package org.koitharu.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DeepPanelDetector {
    // DeepPanel dependency APIs vary; to keep build stable, return empty to allow OpenCV fallback
    suspend fun detect(bitmap: Bitmap): List<Rect> = withContext(Dispatchers.Default) {
        return@withContext emptyList()
    }
}

