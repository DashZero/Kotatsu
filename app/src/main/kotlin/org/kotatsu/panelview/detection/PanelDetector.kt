package org.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Rect

object PanelDetector {
    suspend fun detectPanels(bitmap: Bitmap): List<Rect> {
        return try {
            val deepResults = DeepPanelDetector.detect(bitmap)
            if (deepResults.isNotEmpty()) return deepResults
            val opencvResults = OpenCVPanelDetector.detect(bitmap)
            if (opencvResults.isNotEmpty()) return opencvResults
            listOf(Rect(0, 0, bitmap.width, bitmap.height))
        } catch (_: Throwable) {
            listOf(Rect(0, 0, bitmap.width, bitmap.height))
        }
    }
}

