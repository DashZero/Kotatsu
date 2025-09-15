package org.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log

object PanelDetector {
    suspend fun detectPanels(bitmap: Bitmap): List<Rect> {
        return try {
            // 1) Try OpenCV (fast, accurate on digital pages)
            val opencvResults = OpenCVPanelDetector.detect(bitmap)
            if (opencvResults.size > 1) return opencvResults

            // 2) Try simple gutter-based Kotlin detector (no native deps)
            val simpleResults = SimpleGutterDetector.detect(bitmap)
            if (simpleResults.size > 1) return simpleResults

            // 3) Try ML fallback (optional; currently stubbed to empty)
            val deepResults = DeepPanelDetector.detect(bitmap)
            if (deepResults.size > 1) return deepResults

            // 4) If all failed, return whatever we have (could be 1), else full page
            val result = when {
                opencvResults.isNotEmpty() -> opencvResults
                simpleResults.isNotEmpty() -> simpleResults
                deepResults.isNotEmpty() -> deepResults
                else -> listOf(Rect(0, 0, bitmap.width, bitmap.height))
            }
            Log.d("PanelDetector", "Detected panels: ${result.size}")
            result
        } catch (t: Throwable) {
            Log.w("PanelDetector", "Detection failed", t)
            listOf(Rect(0, 0, bitmap.width, bitmap.height))
        }
    }
}

