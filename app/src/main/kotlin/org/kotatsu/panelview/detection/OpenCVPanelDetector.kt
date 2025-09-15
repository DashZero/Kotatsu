package org.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Rect
import org.opencv.android.Utils
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max

object OpenCVPanelDetector {
    fun detect(bitmap: Bitmap): List<Rect> {
        return runCatching {
            // Ensure native OpenCV libs are loaded
            if (!OpenCVLoader.initDebug()) return emptyList()
            val src = Mat()
            Utils.bitmapToMat(bitmap, src)

            // 1) Grayscale and blur to reduce noise
            val gray = Mat()
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)

            // 2) Adaptive/Otsu threshold to get strong edges/white gutters
            val bin = Mat()
            Imgproc.threshold(gray, bin, 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)

            // 3) Morphological close to merge content within panels
            val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(15.0, 15.0))
            Imgproc.morphologyEx(bin, bin, Imgproc.MORPH_CLOSE, closeKernel)

            // 4) Slight erosion to separate adjacent panels if merged
            val erodeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.erode(bin, bin, erodeKernel)

            // 5) Contour detection of content blobs (panels likely grouped)
            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(bin, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            val imgW = src.width().coerceAtLeast(1)
            val imgH = src.height().coerceAtLeast(1)
            val minArea = (imgW * imgH * 0.03).toInt() // drop tiny fragments (<3%)
            val maxArea = (imgW * imgH * 0.98).toInt()

            // 6) Build candidate rects
            val boxes = contours.map { c -> Imgproc.boundingRect(c) }
                .filter { r -> r.width > 40 && r.height > 40 && (r.width * r.height) in minArea..maxArea }
                .map { r -> Rect(r.x, r.y, r.x + r.width, r.y + r.height) }
                .toMutableList()

            // 7) Merge overlapping/contained boxes
            mergeOverlaps(boxes)

            // 8) If not enough panels found, try alternate pipeline focused on gutters
            if (boxes.size <= 1) {
                val alt = detectByGutters(src)
                if (alt.isNotEmpty()) return alt
            }

            // 9) Ensure at least full page fallback
            if (boxes.isEmpty()) listOf(Rect(0, 0, imgW, imgH)) else boxes
        }.getOrElse { emptyList() }
    }

    private fun detectByGutters(src: Mat): List<Rect> {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)

        // Threshold to keep background/gutters white, content black
        val bin = Mat()
        Imgproc.threshold(gray, bin, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

        // Emphasize gutters (white lines) by opening to remove thin black content lines
        val openKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        Imgproc.morphologyEx(bin, bin, Imgproc.MORPH_OPEN, openKernel)

        // Invert: panels interior become white blobs
        val inv = Mat()
        Core.bitwise_not(bin, inv)

        // Find white blobs as panels
        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(inv, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val imgW = src.width().coerceAtLeast(1)
        val imgH = src.height().coerceAtLeast(1)
        val minArea = (imgW * imgH * 0.03).toInt()
        val maxArea = (imgW * imgH * 0.98).toInt()
        val boxes = contours.map { c -> Imgproc.boundingRect(c) }
            .filter { r -> r.width > 40 && r.height > 40 && (r.width * r.height) in minArea..maxArea }
            .map { r -> Rect(r.x, r.y, r.x + r.width, r.y + r.height) }
            .toMutableList()

        mergeOverlaps(boxes)
        return boxes
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

    private fun contains(a: Rect, b: Rect): Boolean = a.left <= b.left && a.top <= b.top && a.right >= b.right && a.bottom >= b.bottom
    private fun Rect.area(): Int = max(0, width()) * max(0, height())
}

