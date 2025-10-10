package org.koitharu.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlin.math.max
import kotlin.math.min
import org.koitharu.kotatsu.panelview.PanelOrder
import org.koitharu.kotatsu.panelview.settings.PanelDetectionOptions
import org.koitharu.kotatsu.panelview.settings.PanelReadingOrder
import org.koitharu.kotatsu.panelview.settings.PanelViewSettings
import org.koitharu.kotatsu.panelview.utils.PanelSorter

class PanelDetectorImpl(
    private val cacheDir: File,
    private val detectionModeManager: DetectionModeManager,
    private val detectors: Map<DetectionMode, PanelDetector> = defaultDetectors(),
) {

    private val gson = Gson()

    fun detect(bitmap: Bitmap, settings: PanelViewSettings): List<Rect> {
        if (!settings.detection.enabled) {
            return listOf(Rect(0, 0, bitmap.width, bitmap.height))
        }

        val mode = resolveMode(bitmap, settings.detection)
        val cacheFile = File(cacheDir, cacheKey(bitmap, mode, settings.readingOrder))

        loadFromCache(cacheFile)?.let { cached ->
            return cached
        }

        val detector = detectors[mode] ?: detectors.getValue(DetectionMode.MANGA)
        val rawPanels = detector.detect(bitmap)
        val processed = PanelPostProcessor.process(
            rawPanels,
            bitmap.width,
            bitmap.height,
            mode,
            settings.readingOrder,
        )

        saveToCache(cacheFile, processed)
        return processed
    }

    private fun resolveMode(bitmap: Bitmap, options: PanelDetectionOptions): DetectionMode {
        return when (val requested = options.detectionMode) {
            DetectionMode.AUTO -> detectionModeManager.detectMode(bitmap)
            else -> requested
        }
    }

    private fun cacheKey(
        bitmap: Bitmap,
        mode: DetectionMode,
        readingOrder: PanelReadingOrder,
    ): String = buildString {
        append("v")
        append(CACHE_VERSION)
        append('_')
        append(bitmap.width)
        append('x')
        append(bitmap.height)
        append('_')
        append(bitmap.hashCode())
        append('_')
        append(mode.name)
        append('_')
        append(readingOrder.name)
    }

    private fun loadFromCache(file: File): List<Rect>? {
        if (!file.exists()) {
            return null
        }
        return runCatching {
            val type = object : TypeToken<List<Rect>>() {}.type
            gson.fromJson<List<Rect>>(file.readText(), type)
        }.getOrElse {
            file.delete()
            null
        }
    }

    private fun saveToCache(file: File, panels: List<Rect>) {
        runCatching {
            if (!file.parentFile.exists()) {
                file.parentFile.mkdirs()
            }
            file.writeText(gson.toJson(panels))
        }
    }

    private object PanelPostProcessor {

        fun process(
            panels: List<Rect>,
            imageWidth: Int,
            imageHeight: Int,
            mode: DetectionMode,
            readingOrder: PanelReadingOrder,
        ): List<Rect> {
            val imageArea = imageWidth * imageHeight.toDouble()
            val filtered = panels
                .mapNotNull { clampToImage(it, imageWidth, imageHeight) }
                .filter { meetsDimension(it) && meetsArea(it, imageArea) }
            val merged = mergeOverlaps(filtered)
            val fallback = if (merged.isEmpty()) {
                listOf(Rect(0, 0, imageWidth, imageHeight))
            } else {
                merged
            }
            val styleSorted = PanelSorter.sortPanels(fallback, mode)
            return PanelOrder.order(styleSorted, readingOrder)
        }

        private fun clampToImage(rect: Rect, width: Int, height: Int): Rect? {
            val clamped = Rect(
                rect.left.coerceIn(0, width),
                rect.top.coerceIn(0, height),
                rect.right.coerceIn(0, width),
                rect.bottom.coerceIn(0, height),
            )
            return if (clamped.width() > 0 && clamped.height() > 0) clamped else null
        }

        private fun meetsDimension(rect: Rect): Boolean {
            return rect.width() >= PanelDetectionConstants.MIN_PANEL_DIMENSION_PX &&
                rect.height() >= PanelDetectionConstants.MIN_PANEL_DIMENSION_PX
        }

        private fun meetsArea(rect: Rect, imageArea: Double): Boolean {
            val rectArea = rect.width().toDouble() * rect.height().toDouble()
            return rectArea >= imageArea * PanelDetectionConstants.MIN_PANEL_AREA_RATIO
        }

        private fun mergeOverlaps(panels: List<Rect>): List<Rect> {
            if (panels.size <= 1) {
                return panels
            }
            val result = panels.map { Rect(it) }.toMutableList()
            var i = 0
            while (i < result.size) {
                var j = i + 1
                while (j < result.size) {
                    val a = result[i]
                    val b = result[j]
                    if (shouldMerge(a, b)) {
                        a.union(b)
                        result.removeAt(j)
                        j = i + 1
                    } else {
                        j++
                    }
                }
                i++
            }
            return result
        }

        private fun shouldMerge(a: Rect, b: Rect): Boolean {
            if (!Rect.intersects(a, b)) {
                return false
            }
            val intersectionLeft = max(a.left, b.left)
            val intersectionTop = max(a.top, b.top)
            val intersectionRight = min(a.right, b.right)
            val intersectionBottom = min(a.bottom, b.bottom)
            if (intersectionLeft >= intersectionRight || intersectionTop >= intersectionBottom) {
                return false
            }

            val intersectionArea =
                (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
            if (intersectionArea <= 0) {
                return false
            }
            val areaA = a.width() * a.height()
            val areaB = b.width() * b.height()
            if (areaA == 0 || areaB == 0) {
                return false
            }
            val overlapFraction = intersectionArea.toFloat() / min(areaA, areaB).toFloat()
            return overlapFraction >= PanelDetectionConstants.MERGE_OVERLAP_FRACTION
        }
    }

    companion object {
        private const val CACHE_VERSION = 1

        private fun defaultDetectors(): Map<DetectionMode, PanelDetector> = mapOf(
            DetectionMode.MANGA to MangaDetector(),
            DetectionMode.WESTERN to WesternDetector(),
            DetectionMode.STRIP to StripDetector(),
            DetectionMode.WEBTOON to WebtoonDetector(),
        )
    }
}
