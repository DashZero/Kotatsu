package org.koitharu.kotatsu.panelview.detection.mode

import android.graphics.Rect
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect as CvRect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object MangaDetector {
    private const val GUTTER_THRESHOLD = 0.20
    private const val MIN_CHILD_AREA_RATIO = 0.22
    private const val GUTTER_REPLACE_COVERAGE = 0.60
    private const val EDGE_PAD_FRACTION = 0.05
    private const val EDGE_MIN_COVERAGE = 0.12

    fun detect(page: Mat, w: Int, h: Int): List<Rect> {
        if (w <= 0 || h <= 0 || page.empty()) {
            return emptyList()
        }

        val gray = Mat()
        val bin = Mat()
        val blurred = Mat()
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()

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
            Imgproc.findContours(bin, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE)
            val topLevel = extractTopLevelRects(contours, hierarchy, w, h)
            if (topLevel.isEmpty()) listOf(Rect(0, 0, w, h)) else mergeOverlaps(topLevel)
        } finally {
            gray.release()
            bin.release()
            blurred.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    private fun toGray(src: Mat, dst: Mat) {
        if (src.channels() == 1) {
            src.copyTo(dst)
        } else {
            val colorCode = when (src.channels()) {
                3 -> Imgproc.COLOR_BGR2GRAY
                4 -> Imgproc.COLOR_RGBA2GRAY
                else -> Imgproc.COLOR_BGR2GRAY
            }
            Imgproc.cvtColor(src, dst, colorCode)
        }
    }

    private fun extractTopLevelRects(
        contours: List<MatOfPoint>,
        hierarchy: Mat,
        w: Int,
        h: Int,
    ): MutableList<Rect> {
        val results = mutableListOf<Rect>()
        if (hierarchy.empty() || hierarchy.rows() == 0) {
            return results
        }
        val totalArea = (w * h).toDouble()
        for (i in contours.indices) {
            val hierarchyValues = hierarchy.get(0, i) ?: continue
            val parent = hierarchyValues[3].toInt()
            if (parent != -1) continue
            val contour = contours[i]
            val cvRect = Imgproc.boundingRect(contour)
            if (!passesEdgeGuards(cvRect, w, h)) continue
            val rectArea = cvRect.width.toDouble() * cvRect.height.toDouble()
            if (rectArea < totalArea * MIN_CHILD_AREA_RATIO) continue
            val contourArea = Imgproc.contourArea(contour)
            if (contourArea <= 0.0) continue
            val rectangularity = contourArea / rectArea
            if (rectangularity < 0.65) continue
            results += cvRect.toAndroidRect()
        }
        return results
    }

    private fun passesEdgeGuards(rect: CvRect, w: Int, h: Int): Boolean {
        val edgePadX = (w * EDGE_PAD_FRACTION).toInt()
        val edgePadY = (h * EDGE_PAD_FRACTION).toInt()
        val leftCoverage = rect.x / w.toDouble()
        val topCoverage = rect.y / h.toDouble()
        val rightCoverage = (rect.x + rect.width) / w.toDouble()
        val bottomCoverage = (rect.y + rect.height) / h.toDouble()
        val touchesLeft = rect.x <= edgePadX
        val touchesRight = rect.x + rect.width >= w - edgePadX
        val touchesTop = rect.y <= edgePadY
        val touchesBottom = rect.y + rect.height >= h - edgePadY
        val edgeCoverage = listOf(leftCoverage, topCoverage, 1 - rightCoverage, 1 - bottomCoverage)
            .count { it <= EDGE_MIN_COVERAGE }
        return !(edgeCoverage > 2 && ((touchesLeft && touchesRight) || (touchesTop && touchesBottom)))
    }

    private fun mergeOverlaps(rects: List<Rect>): List<Rect> {
        if (rects.size <= 1) return rects
        val sorted = rects.sortedWith(compareBy<Rect> { it.left }.thenBy { it.top })
        val merged = mutableListOf<Rect>()
        for (rect in sorted) {
            val last = merged.lastOrNull()
            if (last == null) {
                merged += Rect(rect)
                continue
            }
            if (Rect.intersects(last, rect) || shouldMerge(last, rect)) {
                last.union(rect)
            } else {
                merged += Rect(rect)
            }
        }
        return merged
    }

    private fun shouldMerge(a: Rect, b: Rect): Boolean {
        val overlapWidth = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val minWidth = minOf(a.width(), b.width())
        if (overlapWidth > 0 && overlapWidth >= minWidth * GUTTER_THRESHOLD) {
            return true
        }
        val overlapHeight = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        val minHeight = minOf(a.height(), b.height())
        return overlapHeight > 0 && overlapHeight >= minHeight * GUTTER_REPLACE_COVERAGE
    }

    private fun CvRect.toAndroidRect(): Rect = Rect(x, y, x + width, y + height)
}
