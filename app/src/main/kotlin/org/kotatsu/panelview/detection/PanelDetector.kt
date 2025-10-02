package org.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import java.util.ArrayList
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import org.kotatsu.panelview.settings.PanelScanType
import org.kotatsu.panelview.settings.PanelViewSettings

private const val MIN_PANEL_SIZE = 48
private const val MAX_TRIM_RATIO = 0.25f
private const val WHITE_THRESHOLD = 230

object PanelDetector {

    suspend fun detectPanels(bitmap: Bitmap, settings: PanelViewSettings): List<Rect> {
        if (settings.frameDetection.disableFrame) {
            return listOf(Rect(0, 0, bitmap.width, bitmap.height))
        }

        var panels = when (settings.scanType) {
            PanelScanType.REGULAR -> runRegularPipeline(bitmap)
            PanelScanType.IRREGULAR -> runIrregularPipeline(bitmap)
            PanelScanType.FOUR_QUADRANTS -> quadrants(bitmap)
            PanelScanType.WEBTOON -> webtoonSlices(bitmap, settings)
        }

        if (panels.size <= 1 && settings.scanType == PanelScanType.REGULAR && settings.enhancements.autoSwitchIrregular) {
            val irregularFallback = runIrregularPipeline(bitmap)
            if (irregularFallback.isNotEmpty()) {
                panels = irregularFallback
            }
        }

        if (settings.frameDetection.inlineFrames) {
            val refined = refineInlinePanels(bitmap, panels)
            if (refined.isNotEmpty()) {
                panels = refined
            }

            if (panels.size <= 1) {
                val inlineDefault = inlineFallback(bitmap, settings.scanType)
                if (inlineDefault.isNotEmpty()) {
                    panels = inlineDefault
                }
            }
        }

        if (panels.isEmpty()) {
            panels = listOf(Rect(0, 0, bitmap.width, bitmap.height))
        }

        return panels
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

    private fun refineInlinePanels(bitmap: Bitmap, panels: List<Rect>): List<Rect> {
        if (panels.isEmpty()) {
            return emptyList()
        }
        val refined = ArrayList<Rect>(panels.size)
        panels.forEach { sourceRect ->
            val clipped = sourceRect.clampedToBitmap(bitmap) ?: run {
                refined += sourceRect
                return@forEach
            }
            val trimmed = bitmap.trimWhitespace(clipped)
            if (trimmed.width() < MIN_PANEL_SIZE || trimmed.height() < MIN_PANEL_SIZE) {
                refined += trimmed
                return@forEach
            }

            val subset = runCatching {
                Bitmap.createBitmap(bitmap, trimmed.left, trimmed.top, trimmed.width(), trimmed.height())
            }.getOrNull()
            if (subset == null) {
                refined += trimmed
                return@forEach
            }

            val detected = SimpleGutterDetector.detect(subset)
                .map { child ->
                    Rect(
                        child.left + trimmed.left,
                        child.top + trimmed.top,
                        child.right + trimmed.left,
                        child.bottom + trimmed.top,
                    )
                }
                .filter { child -> child.width() >= MIN_PANEL_SIZE && child.height() >= MIN_PANEL_SIZE }

            subset.recycle()

            if (detected.size > 1) {

                refined += detected
            } else {
                refined += trimmed
            }
        }
        return refined
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

    private fun Rect.clampedToBitmap(bitmap: Bitmap): Rect? {
        val leftBound = left.coerceIn(0, bitmap.width)
        val topBound = top.coerceIn(0, bitmap.height)
        val rightBound = right.coerceIn(leftBound + 1, bitmap.width)
        val bottomBound = bottom.coerceIn(topBound + 1, bitmap.height)
        return if (leftBound < rightBound && topBound < bottomBound) {
            Rect(leftBound, topBound, rightBound, bottomBound)
        } else {
            null
        }
    }

    private fun Bitmap.trimWhitespace(rect: Rect): Rect {
        var left = rect.left
        var right = rect.right
        var top = rect.top
        var bottom = rect.bottom

        val maxHorizontalTrim = max(1, ((rect.width() * MAX_TRIM_RATIO).toInt()))
        val maxVerticalTrim = max(1, ((rect.height() * MAX_TRIM_RATIO).toInt()))
        val sampleStepX = max(1, rect.width() / 96)
        val sampleStepY = max(1, rect.height() / 96)

        var trimmed = 0
        while (trimmed < maxVerticalTrim && top < bottom) {
            if (!isRowMostlyWhite(top, left, right, sampleStepX)) break
            top++
            trimmed++
        }

        trimmed = 0
        while (trimmed < maxVerticalTrim && bottom > top) {
            if (!isRowMostlyWhite(bottom - 1, left, right, sampleStepX)) break
            bottom--
            trimmed++
        }

        trimmed = 0
        while (trimmed < maxHorizontalTrim && left < right) {
            if (!isColumnMostlyWhite(left, top, bottom, sampleStepY)) break
            left++
            trimmed++
        }

        trimmed = 0
        while (trimmed < maxHorizontalTrim && right > left) {
            if (!isColumnMostlyWhite(right - 1, top, bottom, sampleStepY)) break
            right--
            trimmed++
        }

        return if (left < right && top < bottom) {
            Rect(left, top, right, bottom)
        } else {
            rect
        }
    }

    private fun Bitmap.isRowMostlyWhite(y: Int, left: Int, right: Int, step: Int): Boolean {
        var x = left
        while (x < right) {
            if (!isNearWhite(getPixel(x, y))) {
                return false
            }
            x += step
        }
        return true
    }

    private fun Bitmap.isColumnMostlyWhite(x: Int, top: Int, bottom: Int, step: Int): Boolean {
        var y = top
        while (y < bottom) {
            if (!isNearWhite(getPixel(x, y))) {
                return false
            }
            y += step
        }
        return true
    }

    private fun isNearWhite(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val a = Color.alpha(color)
        return a > 200 && r > WHITE_THRESHOLD && g > WHITE_THRESHOLD && b > WHITE_THRESHOLD
    }
}
