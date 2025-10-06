package org.kotatsu.panelview.detection.mode

import android.graphics.Rect
import org.opencv.core.Mat

object StripDetector {
    private const val EXPECTED_PANELS = 3
    private const val MIN_PANEL_WIDTH_RATIO = 0.15

    fun detect(@Suppress("UNUSED_PARAMETER") page: Mat, w: Int, h: Int): List<Rect> {
        if (w <= 0 || h <= 0) {
            return emptyList()
        }
        val aspect = w.toFloat() / h.toFloat()
        val panelCount = when {
            aspect > 4.2f -> 5
            aspect > 3.2f -> 4
            else -> EXPECTED_PANELS
        }
        val sliceWidth = w / panelCount
        val minWidth = (w * MIN_PANEL_WIDTH_RATIO).toInt()
        if (sliceWidth < minWidth) {
            return listOf(Rect(0, 0, w, h))
        }

        val panels = ArrayList<Rect>(panelCount)
        var left = 0
        for (i in 0 until panelCount) {
            val right = if (i == panelCount - 1) w else left + sliceWidth
            panels += Rect(left, 0, right, h)
            left = right
        }
        return panels
    }
}
