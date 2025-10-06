package org.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import java.util.ArrayList
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.kotatsu.panelview.detection.mode.MangaDetector
import org.kotatsu.panelview.detection.mode.StripDetector
import org.kotatsu.panelview.detection.mode.WebtoonDetector
import org.kotatsu.panelview.detection.mode.WesternDetector
import org.kotatsu.panelview.settings.PanelReadingOrder
import org.kotatsu.panelview.settings.PanelScanType
import org.kotatsu.panelview.settings.PanelViewSettings
import org.kotatsu.panelview.utils.DetectionMode
import org.kotatsu.panelview.utils.PanelSorter
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat

private const val MIN_PANEL_SIZE = 48
private const val MAX_TRIM_RATIO = 0.25f
private const val WHITE_THRESHOLD = 230
private const val INLINE_WHITESPACE_THRESHOLD = 0.88f
private const val INLINE_MAX_SIDE = 960
private const val INLINE_MIN_GUTTER_DENOM = 180

object PanelDetector {

    suspend fun detectPanels(bitmap: Bitmap, settings: PanelViewSettings): List<Rect> {
        if (!settings.detection.enabled) {
            return listOf(Rect(0, 0, bitmap.width, bitmap.height))
        }

        val requestedMode = resolveDetectionMode(settings)
        val modeResult = when (settings.scanType) {
            PanelScanType.FOUR_QUADRANTS -> quadrants(bitmap) to null
            PanelScanType.WEBTOON -> runModePipelineWithFallback(
                bitmap,
                DetectionMode.WEBTOON,
                { webtoonSlices(bitmap, settings) },
                fallbackWhen = { it.size <= 1 },
            )
            PanelScanType.IRREGULAR -> runModePipelineWithFallback(
                bitmap,
                DetectionMode.WESTERN,
                { runIrregularPipeline(bitmap) },
            )
            PanelScanType.REGULAR -> runModePipelineWithFallback(
                bitmap,
                requestedMode,
                { runRegularPipeline(bitmap) },
            )
        }
        var panels = modeResult.first
        val detectionMode = modeResult.second
        if (detectionMode != null) {
            panels = PanelSorter.sortPanels(panels, detectionMode)
        }

        if (panels.size <= 1 && settings.scanType == PanelScanType.REGULAR && settings.enhancements.autoSwitchIrregular) {
            val irregularFallback = runIrregularPipeline(bitmap)
            if (irregularFallback.isNotEmpty()) {
                panels = irregularFallback
            }
        }

        if (settings.detection.smartSplitting) {
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
        val sliceCount = if (settings.detection.smartSplitting) baseSlices + 1 else baseSlices
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
        val refined = ArrayList<Rect>()
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

            val children = mutableListOf<Rect>()

            children += runCatching { SimpleGutterDetector.detect(subset) }
                .onFailure { Log.d("PanelDetector", "Inline simple detector failed", it) }
                .getOrDefault(emptyList())
                .mapNotNull { it.offsetAndClamp(trimmed) }

            children += runCatching { OpenCVPanelDetector.detect(subset) }
                .onFailure { Log.d("PanelDetector", "Inline OpenCV detector failed", it) }
                .getOrDefault(emptyList())
                .mapNotNull { it.offsetAndClamp(trimmed) }

            children += detectInlineByProjection(subset, trimmed)

            subset.recycle()

            val normalized = mergeRectangles(children)
                .filter { it.width() >= MIN_PANEL_SIZE && it.height() >= MIN_PANEL_SIZE }
                .sortedWith(compareBy<Rect> { it.top }.thenBy { it.left })

            if (normalized.size > 1) {
                refined += normalized
            } else {
                val candidate = normalized.firstOrNull()
                if (candidate != null && candidate.area() < trimmed.area() * 0.98f) {
                    refined += candidate
                } else {
                    refined += trimmed
                }
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

    private fun Rect.offsetAndClamp(base: Rect): Rect? {
        val absolute = Rect(this)
        absolute.offset(base.left, base.top)
        return if (absolute.intersect(base)) absolute else null
    }

    private fun Rect.area(): Int = max(0, width()) * max(0, height())

    private fun mergeRectangles(rects: List<Rect>): List<Rect> {
        if (rects.isEmpty()) return emptyList()
        val merged = mutableListOf<Rect>()
        rects.sortedWith(compareBy<Rect> { it.top }.thenBy { it.left }).forEach { rect ->
            var consumed = false
            for (i in merged.indices) {
                val existing = merged[i]
                if (shouldMerge(existing, rect)) {
                    merged[i] = Rect(
                        min(existing.left, rect.left),
                        min(existing.top, rect.top),
                        max(existing.right, rect.right),
                        max(existing.bottom, rect.bottom),
                    )
                    consumed = true
                    break
                }
            }
            if (!consumed) {
                merged += Rect(rect)
            }
        }
        return merged
    }

    private fun shouldMerge(a: Rect, b: Rect): Boolean {
        if (Rect.intersects(a, b) || a.contains(b) || b.contains(a)) {
            return true
        }
        val overlap = intersectionArea(a, b)
        if (overlap == 0) {
            return false
        }
        val minArea = min(a.area(), b.area()).coerceAtLeast(1)
        return overlap / minArea.toFloat() >= 0.75f
    }

    private fun intersectionArea(a: Rect, b: Rect): Int {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        return if (right > left && bottom > top) (right - left) * (bottom - top) else 0
    }

    private fun detectInlineByProjection(subset: Bitmap, baseRect: Rect): List<Rect> {
        if (subset.width < MIN_PANEL_SIZE || subset.height < MIN_PANEL_SIZE) {
            return emptyList()
        }
        val scale = minOf(
            INLINE_MAX_SIDE.toFloat() / subset.width,
            INLINE_MAX_SIDE.toFloat() / subset.height,
            1f,
        )
        val scaled = if (scale < 0.999f) {
            Bitmap.createScaledBitmap(
                subset,
                max(2, (subset.width * scale).roundToInt()),
                max(2, (subset.height * scale).roundToInt()),
                true,
            )
        } else subset

        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val hist = IntArray(256)
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val y = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)
            hist[y]++
        }
        var cumulative = 0
        val target = (pixels.size * 0.88f).toInt()
        var luminanceThreshold = 235
        for (i in 0..255) {
            cumulative += hist[i]
            if (cumulative >= target) {
                luminanceThreshold = i
                break
            }
        }
        luminanceThreshold = luminanceThreshold.coerceIn(190, 250)

        val rowWhite = FloatArray(h)
        val colWhite = FloatArray(w)
        for (y in 0 until h) {
            var whiteCount = 0
            val rowOffset = y * w
            for (x in 0 until w) {
                val pixel = pixels[rowOffset + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val yL = (0.2126f * r + 0.7152f * g + 0.0722f * b)
                if (yL >= luminanceThreshold) {
                    whiteCount++
                    colWhite[x] += 1f
                }
            }
            rowWhite[y] = whiteCount / w.toFloat()
        }
        for (x in 0 until w) {
            colWhite[x] = colWhite[x] / h.toFloat()
        }

        val minRowGutter = max(2, h / INLINE_MIN_GUTTER_DENOM)
        val minColGutter = max(2, w / INLINE_MIN_GUTTER_DENOM)
        val horizontalCuts = findAdaptiveGutters(rowWhite, minRowGutter, INLINE_WHITESPACE_THRESHOLD)
        val verticalCuts = findAdaptiveGutters(colWhite, minColGutter, INLINE_WHITESPACE_THRESHOLD)

        val rows = mutableListOf(0)
        rows.addAll(horizontalCuts)
        rows += h
        val cols = mutableListOf(0)
        cols.addAll(verticalCuts)
        cols += w

        val invScale = if (scale <= 0f) 1f else 1f / scale
        val candidates = mutableListOf<Rect>()
        for (ri in 0 until rows.lastIndex) {
            val top = rows[ri]
            val bottom = rows[ri + 1]
            for (ci in 0 until cols.lastIndex) {
                val left = cols[ci]
                val right = cols[ci + 1]
                if (bottom <= top || right <= left) continue
                val absLeft = (left * invScale).roundToInt() + baseRect.left
                val absTop = (top * invScale).roundToInt() + baseRect.top
                val absRight = (right * invScale).roundToInt() + baseRect.left
                val absBottom = (bottom * invScale).roundToInt() + baseRect.top
                val rect = Rect(absLeft, absTop, absRight, absBottom)
                if (rect.width() < MIN_PANEL_SIZE || rect.height() < MIN_PANEL_SIZE) continue
                if (!isRegionMostlyWhite(pixels, w, h, left, top, right, bottom, luminanceThreshold)) {
                    if (rect.intersect(baseRect) && !rect.isEmpty) {
                        candidates += rect
                    }
                }
            }
        }

        if (scaled !== subset) {
            scaled.recycle()
        }
        return candidates
    }

    private fun findAdaptiveGutters(profile: FloatArray, minLen: Int, threshold: Float): List<Int> {
        if (profile.isEmpty()) return emptyList()
        val cuts = mutableListOf<Int>()
        var runStart = -1
        for (index in profile.indices) {
            val value = profile[index]
            if (value >= threshold) {
                if (runStart == -1) runStart = index
            } else if (runStart != -1) {
                val len = index - runStart
                if (len >= minLen) {
                    cuts += runStart + len / 2
                }
                runStart = -1
            }
        }
        if (runStart != -1) {
            val len = profile.size - runStart
            if (len >= minLen) {
                cuts += runStart + len / 2
            }
        }
        cuts.sort()
        val deduped = mutableListOf<Int>()
        for (cut in cuts) {
            if (deduped.isEmpty() || cut - deduped.last() > minLen) {
                deduped += cut
            }
        }
        return deduped
    }

    private fun isRegionMostlyWhite(
        pixels: IntArray,
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        threshold: Int,
        whiteThreshold: Float = 0.9f,
    ): Boolean {
        val clampedLeft = left.coerceIn(0, width - 1)
        val clampedTop = top.coerceIn(0, height - 1)
        val clampedRight = right.coerceIn(clampedLeft + 1, width)
        val clampedBottom = bottom.coerceIn(clampedTop + 1, height)
        var white = 0
        var total = 0
        for (y in clampedTop until clampedBottom) {
            val rowOffset = y * width
            for (x in clampedLeft until clampedRight) {
                val pixel = pixels[rowOffset + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val yL = (0.2126f * r + 0.7152f * g + 0.0722f * b)
                if (yL >= threshold) white++
                total++
            }
        }
        return total > 0 && white / total.toFloat() >= whiteThreshold
    }

    private fun Bitmap.trimWhitespace(rect: Rect): Rect {
        var left = rect.left
        var right = rect.right
        var top = rect.top
        var bottom = rect.bottom

        val maxHorizontalTrim = max(1, (rect.width() * MAX_TRIM_RATIO).toInt())
        val maxVerticalTrim = max(1, (rect.height() * MAX_TRIM_RATIO).toInt())
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

    private fun resolveDetectionMode(settings: PanelViewSettings): DetectionMode {
        return when (settings.scanType) {
            PanelScanType.WEBTOON -> DetectionMode.WEBTOON
            PanelScanType.IRREGULAR -> DetectionMode.WESTERN
            PanelScanType.FOUR_QUADRANTS -> DetectionMode.STRIP
            PanelScanType.REGULAR -> when (settings.readingOrder) {
                PanelReadingOrder.MANGA -> DetectionMode.MANGA
                PanelReadingOrder.FOUR_KOMA -> DetectionMode.STRIP
                else -> DetectionMode.AUTO
            }
        }
    }

    private suspend fun runModePipelineWithFallback(
        bitmap: Bitmap,
        requestedMode: DetectionMode,
        fallback: suspend () -> List<Rect>,
        fallbackWhen: (List<Rect>) -> Boolean = { it.isEmpty() },
    ): Pair<List<Rect>, DetectionMode?> {
        val modeResult = runModePipeline(bitmap, requestedMode)
        if (modeResult != null) {
            val (rects, mode) = modeResult
            if (!fallbackWhen(rects)) {
                return rects to mode
            }
        }
        return fallback() to null
    }

    private fun runModePipeline(bitmap: Bitmap, requestedMode: DetectionMode): Pair<List<Rect>, DetectionMode>? {
        if (!OpenCVLoader.initDebug()) {
            return null
        }
        val page = Mat()
        return try {
            Utils.bitmapToMat(bitmap, page)
            val actualMode = if (requestedMode == DetectionMode.AUTO) {
                DetectionModeManager.detectMode(bitmap)
            } else {
                requestedMode
            }
            val rects = detectPanels(page, bitmap.width, bitmap.height, actualMode)
            rects to actualMode
        } catch (t: Throwable) {
            Log.w("PanelDetector", "Mode pipeline failed for $requestedMode", t)
            null
        } finally {
            page.release()
        }
    }

    fun detectPanels(page: Mat, w: Int, h: Int, mode: DetectionMode): List<Rect> {
        return when (mode) {
            DetectionMode.MANGA -> MangaDetector.detect(page, w, h)
            DetectionMode.WESTERN -> WesternDetector.detect(page, w, h)
            DetectionMode.STRIP -> StripDetector.detect(page, w, h)
            DetectionMode.WEBTOON -> WebtoonDetector.detect(page, w, h)
            DetectionMode.AUTO -> MangaDetector.detect(page, w, h)
        }
    }

}

