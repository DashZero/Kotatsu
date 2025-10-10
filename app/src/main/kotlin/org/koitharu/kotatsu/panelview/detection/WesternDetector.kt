package org.koitharu.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Rect
import org.koitharu.kotatsu.panelview.detection.PanelDetectionConstants.CANNY_THRESHOLD_HIGH
import org.koitharu.kotatsu.panelview.detection.PanelDetectionConstants.CANNY_THRESHOLD_LOW
import org.koitharu.kotatsu.panelview.detection.PanelDetectionConstants.MIN_PANEL_AREA_RATIO
import org.koitharu.kotatsu.panelview.detection.PanelDetectionConstants.MIN_PANEL_DIMENSION_PX
import org.koitharu.kotatsu.panelview.detection.PanelDetectionConstants.MORPH_KERNEL_SIZE
import org.koitharu.kotatsu.panelview.detection.PanelDetectionConstants.RECTANGULARITY_THRESHOLD
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.imgproc.Imgproc

class WesternDetector : PanelDetector {

    override fun detect(bitmap: Bitmap): List<Rect> {
        val page = Mat()
        Utils.bitmapToMat(bitmap, page)
        Imgproc.cvtColor(page, page, Imgproc.COLOR_BGR2GRAY)
        Imgproc.Canny(page, page, CANNY_THRESHOLD_LOW, CANNY_THRESHOLD_HIGH)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, MORPH_KERNEL_SIZE)
        Imgproc.morphologyEx(page, page, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.dilate(page, page, kernel)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            page,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL, // Find external contours
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        val panels = ArrayList<Rect>()
        val minArea = bitmap.width * bitmap.height * MIN_PANEL_AREA_RATIO
        for (contour in contours) {
            val rect = Imgproc.boundingRect(contour)
            val area = Imgproc.contourArea(contour)
            val rectArea = rect.width.toDouble() * rect.height
            val rectangularity = if (rectArea == 0.0) 0.0 else area / rectArea
            if (
                area >= minArea.toDouble() &&
                rect.width >= MIN_PANEL_DIMENSION_PX &&
                rect.height >= MIN_PANEL_DIMENSION_PX &&
                rectangularity >= RECTANGULARITY_THRESHOLD
            ) {
                panels.add(rect.toAndroidRect())
            }
            contour.release()
        }

        page.release()
        hierarchy.release()
        kernel.release()

        if (panels.isEmpty()) {
            return listOf(Rect(0, 0, bitmap.width, bitmap.height))
        }

        return panels
    }

    private fun org.opencv.core.Rect.toAndroidRect(): Rect {
        return Rect(x, y, x + width, y + height)
    }
}
