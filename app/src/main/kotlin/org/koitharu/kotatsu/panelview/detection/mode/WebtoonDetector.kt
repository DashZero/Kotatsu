package org.koitharu.kotatsu.panelview.detection.mode

import android.graphics.Rect
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object WebtoonDetector {
    private const val GAP_THRESHOLD = 0.10
    private const val MIN_GAP_HEIGHT = 15
    private const val ASPECT_RATIO_TRIGGER = 2.2

    fun detect(page: Mat, w: Int, h: Int): List<Rect> {
        if (w <= 0 || h <= 0 || page.empty()) {
            return emptyList()
        }
        val aspect = h.toFloat() / w.toFloat()
        if (aspect < ASPECT_RATIO_TRIGGER) {
            return listOf(Rect(0, 0, w, h))
        }

        val gray = Mat()
        val bin = Mat()
        val blurred = Mat()
        val row = Mat(1, w, CvType.CV_8UC1)
        return try {
            toGray(page, gray)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.threshold(
                blurred,
                bin,
                0.0,
                255.0,
                Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU,
            )
            val projection = FloatArray(h)
            for (y in 0 until h) {
                bin.row(y).copyTo(row)
                val active = Core.countNonZero(row)
                projection[y] = active / w.toFloat()
            }
            val cuts = findGaps(projection, h)
            if (cuts.isEmpty()) {
                listOf(Rect(0, 0, w, h))
            } else {
                sliceByCuts(cuts, w, h)
            }
        } finally {
            gray.release()
            bin.release()
            blurred.release()
            row.release()
        }
    }

    private fun toGray(src: Mat, dst: Mat) {
        if (src.channels() == 1) {
            src.copyTo(dst)
        } else {
            val conversion = when (src.channels()) {
                3 -> Imgproc.COLOR_BGR2GRAY
                4 -> Imgproc.COLOR_RGBA2GRAY
                else -> Imgproc.COLOR_BGR2GRAY
            }
            Imgproc.cvtColor(src, dst, conversion)
        }
    }

    private fun findGaps(profile: FloatArray, height: Int): List<Int> {
        val cuts = mutableListOf<Int>()
        var runStart = -1
        for (y in 0 until height) {
            val isGap = profile[y] < GAP_THRESHOLD
            if (isGap) {
                if (runStart == -1) {
                    runStart = y
                }
            } else if (runStart != -1) {
                val runLength = y - runStart
                if (runLength >= MIN_GAP_HEIGHT) {
                    cuts += runStart + runLength / 2
                }
                runStart = -1
            }
        }
        if (runStart != -1) {
            val runLength = height - runStart
            if (runLength >= MIN_GAP_HEIGHT) {
                cuts += runStart + runLength / 2
            }
        }
        return cuts.distinct().sorted()
    }

    private fun sliceByCuts(cuts: List<Int>, w: Int, h: Int): List<Rect> {
        val panels = mutableListOf<Rect>()
        var top = 0
        for (cut in cuts) {
            val clamped = cut.coerceIn(top + 1, h - 1)
            panels += Rect(0, top, w, clamped)
            top = clamped
        }
        if (top < h) {
            panels += Rect(0, top, w, h)
        }
        return panels.filter { it.height() > MIN_GAP_HEIGHT }
    }
}

