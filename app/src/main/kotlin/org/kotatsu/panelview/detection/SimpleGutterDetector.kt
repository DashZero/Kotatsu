package org.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight panel detector that finds horizontal/vertical white gutters and
 * partitions the page into rectangular panels. Pure Kotlin, no native deps.
 */
object SimpleGutterDetector {
    fun detect(bitmap: Bitmap): List<Rect> {
        if (bitmap.width < 2 || bitmap.height < 2) return emptyList()

        // Downscale for speed
        val maxSide = 1024
        val scale = minOf(maxSide.toFloat() / bitmap.width, maxSide.toFloat() / bitmap.height, 1f)
        val w = max(2, (bitmap.width * scale).toInt())
        val h = max(2, (bitmap.height * scale).toInt())
        val scaled = if (w != bitmap.width || h != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else bitmap

        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        // Compute luminance and row/col white fractions
        val hist = IntArray(256)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val yL = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)
            hist[yL]++
        }
        var cum = 0
        val target = (pixels.size * 0.90f).toInt()
        var thr = 245
        for (i in 0..255) {
            cum += hist[i]
            if (cum >= target) { thr = i; break }
        }
        thr = thr.coerceAtLeast(200)

        val rowWhite = FloatArray(h)
        val colWhite = FloatArray(w)
        for (y in 0 until h) {
            var rowWhiteCount = 0
            val base = y * w
            for (x in 0 until w) {
                val c = pixels[base + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val yL = (0.2126f * r + 0.7152f * g + 0.0722f * b)
                if (yL >= thr) {
                    rowWhiteCount++
                    colWhite[x] += 1f
                }
            }
            rowWhite[y] = rowWhiteCount / w.toFloat()
        }
        for (x in 0 until w) colWhite[x] = colWhite[x] / h.toFloat()

        // Heuristics: consider gutters as lines with high white fraction
        val horizGutters = findGutters(rowWhite, minLen = max(2, h / 200), threshold = 0.92f)
        val vertGutters = findGutters(colWhite, minLen = max(2, w / 200), threshold = 0.92f)

        // Add implicit borders
        val rows = mutableListOf(0) + horizGutters + listOf(h)
        val cols = mutableListOf(0) + vertGutters + listOf(w)

        // Build rectangles between consecutive gutters
        val minPanelW = max(40, w / 6)
        val minPanelH = max(40, h / 6)
        val rects = mutableListOf<Rect>()
        for (ri in 0 until rows.size - 1) {
            val top = rows[ri]
            val bottom = rows[ri + 1]
            for (ci in 0 until cols.size - 1) {
                val left = cols[ci]
                val right = cols[ci + 1]
                val rw = right - left
                val rh = bottom - top
                if (rw >= minPanelW && rh >= minPanelH) {
                    // Verify interior not dominated by white (avoid picking gutters area)
                    if (!isMostlyWhite(pixels, w, h, left, top, right, bottom, thr = thr, whiteThreshold = 0.9f)) {
                        rects.add(Rect(
                            (left / scale).toInt(),
                            (top / scale).toInt(),
                            (right / scale).toInt(),
                            (bottom / scale).toInt()
                        ))
                    }
                }
            }
        }

        return rects
    }

    private fun findGutters(profile: FloatArray, minLen: Int, threshold: Float): List<Int> {
        val cuts = mutableListOf<Int>()
        var runStart = -1
        for (i in profile.indices) {
            if (profile[i] >= threshold) {
                if (runStart == -1) runStart = i
            } else if (runStart != -1) {
                val len = i - runStart
                if (len >= minLen) {
                    // Use center of gutter band as boundary
                    cuts.add(runStart + len / 2)
                }
                runStart = -1
            }
        }
        if (runStart != -1) {
            val len = profile.size - runStart
            if (len >= minLen) cuts.add(runStart + len / 2)
        }
        // Deduplicate near-duplicates
        cuts.sort()
        val out = mutableListOf<Int>()
        for (c in cuts) {
            if (out.isEmpty() || c - out.last() > minLen) out.add(c)
        }
        return out
    }

    private fun isMostlyWhite(
        pixels: IntArray,
        w: Int,
        h: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        thr: Int,
        whiteThreshold: Float,
    ): Boolean {
        var white = 0
        var total = 0
        val l = max(0, min(w - 1, left))
        val t = max(0, min(h - 1, top))
        val r = max(1, min(w, right))
        val b = max(1, min(h, bottom))
        for (y in t until b) {
            val base = y * w
            for (x in l until r) {
                val c = pixels[base + x]
                val R = (c shr 16) and 0xFF
                val G = (c shr 8) and 0xFF
                val B = c and 0xFF
                val yL = (0.2126f * R + 0.7152f * G + 0.0722f * B)
                if (yL >= thr) white++
                total++
            }
        }
        return total > 0 && white / total.toFloat() >= whiteThreshold
    }
}
