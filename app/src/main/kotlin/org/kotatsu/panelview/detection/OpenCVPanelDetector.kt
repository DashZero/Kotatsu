package org.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Rect
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

object OpenCVPanelDetector {

    private const val GAUSSIAN_BLUR_SIZE = 3.0
    private const val CLOSE_KERNEL_SIZE = 15.0
    private const val ERODE_KERNEL_SIZE = 3.0
    private const val OPEN_KERNEL_SIZE = 9.0
    private const val EDGE_KERNEL_SIZE = 3.0
    private const val MIN_PANEL_DIMENSION = 40
    private const val MIN_PANEL_AREA_RATIO = 0.03
    private const val MAX_PANEL_AREA_RATIO = 0.98
    private const val MAX_SPLIT_DEPTH = 4
    private const val GUTTER_INTENSITY_THRESHOLD = 0.18
    private const val MIN_GUTTER_FRACTION = 0.012
    private const val GUTTER_REPLACE_COVERAGE = 0.3
    private const val MIN_CHILD_AREA_RATIO = 0.08
    private const val EDGE_PAD_FRACTION = 0.025
    private const val EDGE_MIN_COVERAGE = 0.05

    fun detect(bitmap: Bitmap): List<Rect> {
        return runCatching {
            if (!OpenCVLoader.initDebug()) return emptyList()

            val src = Mat()
            val gray = Mat()
            val bin = Mat()
            val panelMask = Mat()
            val gutterLines = Mat()
            val closeKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(CLOSE_KERNEL_SIZE, CLOSE_KERNEL_SIZE),
            )
            val erodeKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(ERODE_KERNEL_SIZE, ERODE_KERNEL_SIZE),
            )
            val openKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(OPEN_KERNEL_SIZE, OPEN_KERNEL_SIZE),
            )
            val edgeKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(EDGE_KERNEL_SIZE, EDGE_KERNEL_SIZE),
            )
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()

            try {
                Utils.bitmapToMat(bitmap, src)

                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.GaussianBlur(gray, gray, Size(GAUSSIAN_BLUR_SIZE, GAUSSIAN_BLUR_SIZE), 0.0)

                Imgproc.threshold(
                    gray,
                    bin,
                    0.0,
                    255.0,
                    Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU,
                )

                buildGutterMasks(gray, openKernel, edgeKernel, panelMask, gutterLines)

                Imgproc.morphologyEx(bin, bin, Imgproc.MORPH_CLOSE, closeKernel)
                Imgproc.erode(bin, bin, erodeKernel)

                Imgproc.findContours(
                    bin,
                    contours,
                    hierarchy,
                    Imgproc.RETR_EXTERNAL,
                    Imgproc.CHAIN_APPROX_SIMPLE,
                )

                val imgW = src.width().coerceAtLeast(1)
                val imgH = src.height().coerceAtLeast(1)
                val pageArea = imgW * imgH
                val minArea = (pageArea * MIN_PANEL_AREA_RATIO).toInt()
                val maxArea = (pageArea * MAX_PANEL_AREA_RATIO).toInt()

                val boxes = contours
                    .map { c -> Imgproc.boundingRect(c) }
                    .filter { r ->
                        r.width > MIN_PANEL_DIMENSION &&
                            r.height > MIN_PANEL_DIMENSION &&
                            (r.width * r.height) in minArea..maxArea
                    }
                    .map { r -> Rect(r.x, r.y, r.x + r.width, r.y + r.height) }
                    .toMutableList()

                mergeOverlaps(boxes)

                val gutterBoxes = detectGutterBoxes(panelMask, minArea, maxArea)
                val refined = refineWithGutters(boxes.toList(), gutterBoxes)

                var panels = splitPanels(panelMask, gutterLines, refined, imgW, imgH, minArea)
                if (panels.isEmpty()) {
                    panels = splitPanels(panelMask, gutterLines, gutterBoxes, imgW, imgH, minArea)
                }
                if (panels.isEmpty()) {
                    panels = gutterBoxes.toMutableList()
                }

                if (panels.isEmpty()) {
                    return listOf(Rect(0, 0, imgW, imgH))
                }

                mergeOverlaps(panels)

                panels
                    .filter { rect ->
                        val area = rect.width() * rect.height()
                        rect.width() >= MIN_PANEL_DIMENSION &&
                            rect.height() >= MIN_PANEL_DIMENSION &&
                            area in minArea..maxArea
                    }
                    .ifEmpty { listOf(Rect(0, 0, imgW, imgH)) }
            } finally {
                src.release()
                gray.release()
                bin.release()
                panelMask.release()
                gutterLines.release()
                closeKernel.release()
                erodeKernel.release()
                openKernel.release()
                edgeKernel.release()
                hierarchy.release()
                contours.forEach { it.release() }
            }
        }.getOrElse { emptyList() }
    }

    private fun buildGutterMasks(
        gray: Mat,
        openKernel: Mat,
        edgeKernel: Mat,
        panelMask: Mat,
        gutterLinesOut: Mat,
    ) {
        val brightThresh = Mat()
        val darkThresh = Mat()
        val edges = Mat()
        val brightEdges = Mat()
        val darkEdges = Mat()
        val interior = Mat()
        try {
            Imgproc.adaptiveThreshold(
                gray,
                brightThresh,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                35,
                -5.0,
            )
            Imgproc.adaptiveThreshold(
                gray,
                darkThresh,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                35,
                5.0,
            )

            Imgproc.Canny(gray, edges, 50.0, 150.0)
            Imgproc.dilate(edges, edges, edgeKernel)
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, openKernel)

            Core.bitwise_and(brightThresh, edges, brightEdges)
            Core.bitwise_and(darkThresh, edges, darkEdges)
            Core.bitwise_or(brightEdges, darkEdges, gutterLinesOut)
            Imgproc.morphologyEx(gutterLinesOut, gutterLinesOut, Imgproc.MORPH_CLOSE, openKernel)

            Core.bitwise_or(brightThresh, darkThresh, interior)
            Imgproc.morphologyEx(interior, interior, Imgproc.MORPH_CLOSE, openKernel)
            Core.bitwise_not(gutterLinesOut, panelMask)
            Core.bitwise_and(panelMask, interior, panelMask)
        } finally {
            brightThresh.release()
            darkThresh.release()
            edges.release()
            brightEdges.release()
            darkEdges.release()
            interior.release()
        }
    }

    private fun detectGutterBoxes(panelMask: Mat, minArea: Int, maxArea: Int): List<Rect> {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        val maskForContours = panelMask.clone()
        return try {
            Imgproc.findContours(
                maskForContours,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE,
            )

            val boxes = contours.map { c -> Imgproc.boundingRect(c) }
                .filter { r ->
                    r.width > MIN_PANEL_DIMENSION &&
                        r.height > MIN_PANEL_DIMENSION &&
                        (r.width * r.height) in minArea..maxArea
                }
                .map { r -> Rect(r.x, r.y, r.x + r.width, r.y + r.height) }
                .toMutableList()

            mergeOverlaps(boxes)
            boxes
        } finally {
            maskForContours.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    private fun refineWithGutters(primary: List<Rect>, secondary: List<Rect>): MutableList<Rect> {
        if (primary.isEmpty() && secondary.isEmpty()) return mutableListOf()
        if (primary.isEmpty()) return secondary.toMutableList()
        if (secondary.isEmpty()) return primary.toMutableList()

        val result = mutableListOf<Rect>()
        val remainingSecondary = secondary.toMutableList()

        for (candidate in primary) {
            val contained = remainingSecondary.filter { contains(candidate, it) }
            val coverage = if (contained.isEmpty()) 0.0 else contained
                .sumOf { it.area().toLong() }
                .toDouble() / max(1, candidate.area())

            if (contained.size >= 2 && coverage >= GUTTER_REPLACE_COVERAGE) {
                result.addAll(contained)
                contained.forEach { remainingSecondary.remove(it) }
            } else {
                result.add(candidate)
            }
        }

        remainingSecondary.forEach { extra ->
            val overlaps = result.any { overlapRatio(it, extra) > 0.7 }
            if (!overlaps) result.add(extra)
        }

        return result
    }

    private fun splitPanels(
        panelMask: Mat,
        edgeMask: Mat,
        candidates: List<Rect>,
        imgW: Int,
        imgH: Int,
        minArea: Int,
    ): MutableList<Rect> {
        if (candidates.isEmpty()) return mutableListOf()
        val result = mutableListOf<Rect>()
        candidates.forEach { rect ->
            splitRecursive(panelMask, edgeMask, rect, 0, minArea).forEach { split ->
                clampRect(split, imgW, imgH)?.let { result.add(it) }
            }
        }
        return result
    }

    private fun splitRecursive(
        panelMask: Mat,
        edgeMask: Mat,
        rect: Rect,
        depth: Int,
        minArea: Int,
    ): List<Rect> {
        val clipped = clampRect(rect, panelMask.cols(), panelMask.rows()) ?: return emptyList()
        if (depth >= MAX_SPLIT_DEPTH) return listOf(rect)

        val currentArea = rect.width() * rect.height()
        if (currentArea <= minArea * 1.1) return listOf(rect)
        if (clipped.width() <= MIN_PANEL_DIMENSION * 1.15 || clipped.height() <= MIN_PANEL_DIMENSION * 1.15) {
            return listOf(rect)
        }

        findProminentGutter(panelMask, edgeMask, rect, clipped, vertical = true, minArea = minArea)?.let { gutter ->
            val left = Rect(rect.left, rect.top, rect.left + gutter.first, rect.bottom)
            val right = Rect(rect.left + gutter.second, rect.top, rect.right, rect.bottom)
            val children = listOf(left, right).filter { child ->
                child.width() >= MIN_PANEL_DIMENSION &&
                    child.height() >= MIN_PANEL_DIMENSION &&
                    child.width() * child.height() >= minArea * MIN_CHILD_AREA_RATIO
            }
            if (children.size == 2) {
                return children.flatMap { splitRecursive(panelMask, edgeMask, it, depth + 1, minArea) }
            }
        }

        findProminentGutter(panelMask, edgeMask, rect, clipped, vertical = false, minArea = minArea)?.let { gutter ->
            val topRect = Rect(rect.left, rect.top, rect.right, rect.top + gutter.first)
            val bottomRect = Rect(rect.left, rect.top + gutter.second, rect.right, rect.bottom)
            val children = listOf(topRect, bottomRect).filter { child ->
                child.width() >= MIN_PANEL_DIMENSION &&
                    child.height() >= MIN_PANEL_DIMENSION &&
                    child.width() * child.height() >= minArea * MIN_CHILD_AREA_RATIO
            }
            if (children.size == 2) {
                return children.flatMap { splitRecursive(panelMask, edgeMask, it, depth + 1, minArea) }
            }
        }

        return listOf(rect)
    }

    private fun findProminentGutter(
        panelMask: Mat,
        edgeMask: Mat,
        originalRect: Rect,
        clipped: Rect,
        vertical: Boolean,
        minArea: Int,
    ): Pair<Int, Int>? {
        val roi = panelMask.submat(clipped.top, clipped.bottom, clipped.left, clipped.right)
        val projection = Mat()
        return try {
            val axis = if (vertical) 0 else 1
            Core.reduce(roi, projection, axis, Core.REDUCE_SUM, CvType.CV_32S)
            val length = if (vertical) projection.cols() else projection.rows()
            if (length <= 0) return null

            val maxIntensity = 255.0 * if (vertical) roi.rows().coerceAtLeast(1) else roi.cols().coerceAtLeast(1)
            if (maxIntensity == 0.0) return null

            val values = DoubleArray(length) { index ->
                val raw = if (vertical) projection.get(0, index)[0] else projection.get(index, 0)[0]
                raw / maxIntensity
            }

            val minThickness = max(2, (length * MIN_GUTTER_FRACTION).toInt())
            val edgePad = max(2, (length * EDGE_PAD_FRACTION).toInt())

            var start = -1
            var bestRange: Pair<Int, Int>? = null
            var bestScore = Double.NEGATIVE_INFINITY

            fun considerRange(endExclusive: Int) {
                if (start == -1) return
                val width = endExclusive - start
                if (width < minThickness || start <= edgePad || endExclusive >= length - edgePad) {
                    start = -1
                    return
                }
                val mean = values.sliceArray(start until endExclusive).average()
                val score = (-mean) * width
                if (score > bestScore) {
                    val offset = if (vertical) clipped.left - originalRect.left else clipped.top - originalRect.top
                    val rangeStart = start + offset
                    val rangeEnd = endExclusive + offset
                    val leftOrTopSize = rangeStart
                    val rightOrBottomSize = (if (vertical) originalRect.width() else originalRect.height()) - rangeEnd
                    val leftRightMin = min(leftOrTopSize, rightOrBottomSize)
                    val baseDimension = if (vertical) originalRect.height() else originalRect.width()
                    if (leftRightMin > MIN_PANEL_DIMENSION / 2 && baseDimension > MIN_PANEL_DIMENSION) {
                        bestScore = score
                        bestRange = rangeStart to rangeEnd
                    }
                }
                start = -1
            }

            for (i in 0 until length) {
                val value = values[i]
                if (value <= GUTTER_INTENSITY_THRESHOLD) {
                    if (start == -1) start = i
                } else {
                    considerRange(i)
                }
            }
            considerRange(length)

            val range = bestRange ?: return null
            val available = if (vertical) originalRect.width() else originalRect.height()
            if (available <= 0) return null
            val normalizedStart = range.first.coerceIn(0, available)
            val normalizedEnd = range.second.coerceIn(normalizedStart + 1, available)
            val splitWidth = normalizedEnd - normalizedStart
            if (splitWidth <= 0 || available - splitWidth <= MIN_PANEL_DIMENSION) return null

            val rangeStartAbs: Int
            val rangeEndAbs: Int
            if (vertical) {
                val limit = panelMask.cols()
                rangeStartAbs = (originalRect.left + normalizedStart).coerceIn(0, limit)
                rangeEndAbs = (originalRect.left + normalizedEnd).coerceIn(rangeStartAbs + 1, limit)
            } else {
                val limit = panelMask.rows()
                rangeStartAbs = (originalRect.top + normalizedStart).coerceIn(0, limit)
                rangeEndAbs = (originalRect.top + normalizedEnd).coerceIn(rangeStartAbs + 1, limit)
            }

            val edgeRoi = if (vertical) {
                edgeMask.submat(clipped.top, clipped.bottom, rangeStartAbs, rangeEndAbs)
            } else {
                edgeMask.submat(rangeStartAbs, rangeEndAbs, clipped.left, clipped.right)
            }
            val required = edgeRoi.rows() * edgeRoi.cols() * EDGE_MIN_COVERAGE
            val coverage = Core.countNonZero(edgeRoi).toDouble()
            edgeRoi.release()
            if (coverage < required) return null

            if (vertical) {
                val leftArea = normalizedStart * originalRect.height()
                val rightArea = (available - normalizedEnd) * originalRect.height()
                if (leftArea < minArea * MIN_CHILD_AREA_RATIO || rightArea < minArea * MIN_CHILD_AREA_RATIO) return null
            } else {
                val topArea = originalRect.width() * normalizedStart
                val bottomArea = originalRect.width() * (available - normalizedEnd)
                if (topArea < minArea * MIN_CHILD_AREA_RATIO || bottomArea < minArea * MIN_CHILD_AREA_RATIO) return null
            }

            normalizedStart to normalizedEnd
        } finally {
            roi.release()
            projection.release()
        }
    }

    private fun clampRect(rect: Rect, maxWidth: Int, maxHeight: Int): Rect? {
        val left = rect.left.coerceIn(0, maxWidth)
        val top = rect.top.coerceIn(0, maxHeight)
        val right = rect.right.coerceIn(0, maxWidth)
        val bottom = rect.bottom.coerceIn(0, maxHeight)
        return if (right - left > 1 && bottom - top > 1) Rect(left, top, right, bottom) else null
    }

    private fun mergeOverlaps(boxes: MutableList<Rect>) {
        var merged = true
        while (merged) {
            merged = false
            outer@ for (i in 0 until boxes.size) {
                for (j in i + 1 until boxes.size) {
                    val a = boxes[i]
                    val b = boxes[j]
                    if (Rect.intersects(a, b) || contains(a, b) || contains(b, a)) {
                        val union = Rect(
                            minOf(a.left, b.left),
                            minOf(a.top, b.top),
                            maxOf(a.right, b.right),
                            maxOf(a.bottom, b.bottom),
                        )
                        boxes[i] = union
                        boxes.removeAt(j)
                        merged = true
                        break@outer
                    }
                }
            }
        }
    }

    private fun overlapRatio(a: Rect, b: Rect): Double {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        if (right <= left || bottom <= top) return 0.0
        val intersection = (right - left) * (bottom - top)
        val minArea = max(1, minOf(a.area(), b.area()))
        return intersection.toDouble() / minArea
    }

    private fun contains(a: Rect, b: Rect): Boolean =
        a.left <= b.left && a.top <= b.top && a.right >= b.right && a.bottom >= b.bottom

    private fun Rect.area(): Int = max(0, width()) * max(0, height())
}
