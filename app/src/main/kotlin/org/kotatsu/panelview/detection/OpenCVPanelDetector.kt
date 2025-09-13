package org.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Rect
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.imgproc.Imgproc

object OpenCVPanelDetector {
    fun detect(bitmap: Bitmap): List<Rect> {
        return runCatching {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY)
            Imgproc.threshold(mat, mat, 240.0, 255.0, Imgproc.THRESH_BINARY)
            Core.bitwise_not(mat, mat)

            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(mat, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            contours.map { Imgproc.boundingRect(it) }
                .filter { it.width * it.height > 10_000 }
                .map { Rect(it.x, it.y, it.x + it.width, it.y + it.height) }
        }.getOrElse { emptyList() }
    }
}

