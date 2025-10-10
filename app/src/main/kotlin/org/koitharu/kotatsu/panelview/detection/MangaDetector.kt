package org.koitharu.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Rect
import org.koitharu.kotatsu.panelview.detection.PanelDetectionConstants.MIN_PANEL_AREA_RATIO
import org.koitharu.kotatsu.panelview.detection.PanelDetectionConstants.MIN_PANEL_DIMENSION_PX
import org.koitharu.kotatsu.panelview.detection.PanelDetectionConstants.RECTANGULARITY_THRESHOLD
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class MangaDetector : PanelDetector {

    override fun detect(bitmap: Bitmap): List<Rect> {
        val page = Mat()
        Utils.bitmapToMat(bitmap, page)

        // Determine if the image is color or B&W by checking saturation
        val hsv = Mat()
        Imgproc.cvtColor(page, hsv, Imgproc.COLOR_BGR2HSV)
        val channels = ArrayList<Mat>()
        Core.split(hsv, channels)
        val saturation = channels[1]
        val avgSaturation = Core.mean(saturation).`val`[0]
        hsv.release()
        channels.forEachIndexed { index, mat ->
            if (index != 1) {
                mat.release()
            }
        }
        saturation.release()

        Imgproc.cvtColor(page, page, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(page, page, Size(3.0, 3.0), 0.0)

        if (avgSaturation > 25) { // Color image
            Imgproc.adaptiveThreshold(
                page,
                page,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                11,
                2.0
            )
        } else { // B&W image
            Imgproc.threshold(page, page, 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)
        }

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            page,
            contours,
            hierarchy,
            Imgproc.RETR_TREE,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        val minArea = bitmap.width * bitmap.height * MIN_PANEL_AREA_RATIO
        val panels = ArrayList<Rect>()
        for (i in contours.indices) {
            // Check if it's a top-level contour
            if (hierarchy.get(0, i)[3] < 0) {
                val contour = contours[i]
                val rect = Imgproc.boundingRect(contour)
                val area = Imgproc.contourArea(contour)
                if (rect.width > 0 && rect.height > 0) {
                    val rectArea = (rect.width * rect.height).toDouble()
                    val rectangularity = area / rectArea

                    // Filter based on size and shape
                    if (
                        rectangularity >= RECTANGULARITY_THRESHOLD &&
                        area >= minArea &&
                        rect.width >= MIN_PANEL_DIMENSION_PX &&
                        rect.height >= MIN_PANEL_DIMENSION_PX
                    ) {
                        panels.add(rect.toAndroidRect())
                    }
                }
                contour.release()
            }
        }

        page.release()
        hierarchy.release()

        if (panels.isEmpty()) {
            return listOf(Rect(0, 0, bitmap.width, bitmap.height))
        }

        return panels.sortedWith(compareBy({ it.top }, { it.left }))
    }

    private fun org.opencv.core.Rect.toAndroidRect(): Rect {
        return Rect(x, y, x + width, y + height)
    }
}
