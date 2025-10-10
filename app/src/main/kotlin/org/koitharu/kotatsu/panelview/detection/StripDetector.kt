package org.koitharu.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.roundToInt

class StripDetector : PanelDetector {

    override fun detect(bitmap: Bitmap): List<Rect> {
        val panelCount = resolvePanelCount(bitmap)

        val panelWidth = bitmap.width.toFloat() / panelCount
        val panels = ArrayList<Rect>()

        for (i in 0 until panelCount) {
            val left = (i * panelWidth).roundToInt()
            val right = ((i + 1) * panelWidth).roundToInt().coerceAtMost(bitmap.width)
            panels.add(Rect(left, 0, right, bitmap.height))
        }

        return panels
    }

    private fun resolvePanelCount(bitmap: Bitmap): Int {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        return when {
            aspectRatio >= 3.8f -> 5
            aspectRatio >= 3.0f -> 4
            else -> 3
        }
    }
}
