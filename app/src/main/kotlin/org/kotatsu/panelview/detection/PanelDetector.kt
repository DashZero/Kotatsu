package org.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import org.kotatsu.panelview.settings.PanelScanType
import org.kotatsu.panelview.settings.PanelViewSettings

object PanelDetector {

    suspend fun detectPanels(bitmap: Bitmap, settings: PanelViewSettings): List<Rect> {
        if (settings.frameDetection.disableFrame) {
            return listOf(Rect(0, 0, bitmap.width, bitmap.height))
        }

        val panels = when (settings.scanType) {
            PanelScanType.REGULAR -> runRegularPipeline(bitmap)
            PanelScanType.IRREGULAR -> runIrregularPipeline(bitmap)
            PanelScanType.FOUR_QUADRANTS -> quadrants(bitmap)
            PanelScanType.WEBTOON -> webtoonSlices(bitmap, settings)
        }

        if (panels.size > 1) {
            return panels
        }

        if (settings.scanType == PanelScanType.REGULAR && settings.enhancements.autoSwitchIrregular) {
            val fallback = runIrregularPipeline(bitmap)
            if (fallback.size > 1) {
                return fallback
            }
            if (fallback.isNotEmpty()) {
                return fallback
            }
        }

        if (settings.frameDetection.inlineFrames && panels.size <= 1) {
            val inline = inlineFallback(bitmap, settings.scanType)
            if (inline.isNotEmpty()) {
                return inline
            }
        }

        return if (panels.isNotEmpty()) {
            panels
        } else {
            listOf(Rect(0, 0, bitmap.width, bitmap.height))
        }
    }

    private suspend fun runRegularPipeline(bitmap: Bitmap): List<Rect> {
        runCatching {
            val opencvResults = OpenCVPanelDetector.detect(bitmap)
            if (opencvResults.size > 1) {
                return opencvResults
            }
            if (opencvResults.isNotEmpty()) {
                return opencvResults
            }
        }.onFailure { Log.w("PanelDetector", "OpenCV pipeline failed", it) }

        val simpleResults = SimpleGutterDetector.detect(bitmap)
        if (simpleResults.isNotEmpty()) {
            return simpleResults
        }

        val deepResults = DeepPanelDetector.detect(bitmap)
        if (deepResults.isNotEmpty()) {
            return deepResults
        }

        return emptyList()
    }

    private suspend fun runIrregularPipeline(bitmap: Bitmap): List<Rect> {
        val simple = SimpleGutterDetector.detect(bitmap)
        if (simple.size > 1) {
            return simple
        }

        val openCv = runCatching { OpenCVPanelDetector.detect(bitmap) }
            .onFailure { Log.w("PanelDetector", "Irregular OpenCV pipeline failed", it) }
            .getOrNull()
        if (!openCv.isNullOrEmpty()) {
            return openCv
        }

        val deep = DeepPanelDetector.detect(bitmap)
        if (deep.isNotEmpty()) {
            return deep
        }

        return simple
    }

    private fun quadrants(bitmap: Bitmap): List<Rect> {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return emptyList()
        val midW = w / 2
        val midH = h / 2
        return listOf(
            Rect(0, 0, midW, midH),
            Rect(midW, 0, w, midH),
            Rect(0, midH, midW, h),
            Rect(midW, midH, w, h),
        )
    }

    private fun webtoonSlices(bitmap: Bitmap, settings: PanelViewSettings): List<Rect> {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return emptyList()
        val aspect = h / w.toFloat()
        val baseSlices = if (aspect < 2f) 2 else ceil(aspect).toInt().coerceAtLeast(3)
        val sliceCount = if (settings.frameDetection.inlineFrames) baseSlices + 1 else baseSlices
        val clampedSlices = sliceCount.coerceIn(2, 8)
        val step = max(1, h / clampedSlices)
        val rects = ArrayList<Rect>(clampedSlices)
        var top = 0
        while (top < h) {
            val bottom = min(h, top + step)
            rects.add(Rect(0, top, w, bottom))
            if (bottom == h) break
            top = bottom
        }
        return rects
    }

    private fun inlineFallback(bitmap: Bitmap, scanType: PanelScanType): List<Rect> {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return emptyList()

        val aspect = w / h.toFloat()
        val preferVertical = scanType == PanelScanType.WEBTOON || aspect < 0.8f
        val columns = when {
            preferVertical -> 1
            aspect > 1.35f -> 3
            aspect > 1.05f -> 2
            else -> 1
        }
        val rows = when {
            preferVertical -> max(2, ceil(h / (w * 0.9f)).toInt())
            aspect < 0.8f -> 3
            else -> 2
        }

        val rects = ArrayList<Rect>(rows * columns)
        var top = 0
        for (row in 0 until rows) {
            val bottom = if (row == rows - 1) h else min(h, ((row + 1) * h) / rows)
            var left = 0
            for (col in 0 until columns) {
                val right = if (col == columns - 1) w else min(w, ((col + 1) * w) / columns)
                rects.add(Rect(left, top, right, bottom))
                left = right
            }
            top = bottom
        }
        return rects
    }
}
