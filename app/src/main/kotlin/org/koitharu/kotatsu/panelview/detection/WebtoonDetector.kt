package org.koitharu.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Rect
import org.koitharu.kotatsu.panelview.detection.PanelDetectionConstants.MORPH_KERNEL_SIZE
import org.koitharu.kotatsu.panelview.detection.PanelDetectionConstants.WEBTOON_GAP_ROW_DENSITY
import org.koitharu.kotatsu.panelview.detection.PanelDetectionConstants.WEBTOON_MIN_GAP_PX
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class WebtoonDetector : PanelDetector {

    override fun detect(bitmap: Bitmap): List<Rect> {
        val page = Mat()
        Utils.bitmapToMat(bitmap, page)
        Imgproc.cvtColor(page, page, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(page, page, MORPH_KERNEL_SIZE, 0.0)

        val binary = Mat()
        Imgproc.threshold(
            page,
            binary,
            0.0,
            255.0,
            Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU
        )

        val projection = Mat()
        Core.reduce(binary, projection, 1, Core.REDUCE_SUM, binary.type())

        val panels = mutableListOf<Rect>()
        var lastCut = 0
        var gapStart = -1
        val rowNormalizer = bitmap.width * 255.0

        for (y in 0 until projection.rows()) {
            val rowValue = projection.get(y, 0)[0]
            val rowDensity = rowValue / rowNormalizer
            val isGapRow = rowDensity < WEBTOON_GAP_ROW_DENSITY

            if (isGapRow) {
                if (gapStart == -1) {
                    gapStart = y
                }
            } else if (gapStart != -1) {
                val gapHeight = y - gapStart
                if (gapHeight >= WEBTOON_MIN_GAP_PX) {
                    val top = lastCut
                    val bottom = gapStart
                    if (bottom > top) {
                        panels.add(Rect(0, top, bitmap.width, bottom))
                    }
                    lastCut = y
                }
                gapStart = -1
            }
        }

        if (lastCut < bitmap.height) {
            panels.add(Rect(0, lastCut, bitmap.width, bitmap.height))
        }

        page.release()
        binary.release()
        projection.release()

        if (panels.isEmpty()) {
            return listOf(Rect(0, 0, bitmap.width, bitmap.height))
        }

        return panels
    }
}
