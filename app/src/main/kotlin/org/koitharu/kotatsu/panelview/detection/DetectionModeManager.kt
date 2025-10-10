package org.koitharu.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Color

object DetectionModeManager {

    private const val SATURATION_THRESHOLD = 0.1
    private const val STRIP_ASPECT_RATIO_THRESHOLD = 2.0
    private const val WEBTOON_ASPECT_RATIO_THRESHOLD = 0.5

    fun detectMode(bitmap: Bitmap): DetectionMode {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

        return when {
            aspectRatio > STRIP_ASPECT_RATIO_THRESHOLD -> DetectionMode.STRIP
            aspectRatio < WEBTOON_ASPECT_RATIO_THRESHOLD -> DetectionMode.WEBTOON
            else -> {
                val saturation = calculateAverageSaturation(bitmap)
                if (saturation < SATURATION_THRESHOLD) {
                    DetectionMode.MANGA
                } else {
                    DetectionMode.WESTERN
                }
            }
        }
    }

    private fun calculateAverageSaturation(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var totalSaturation = 0f
        val hsv = FloatArray(3)

        for (pixel in pixels) {
            Color.colorToHSV(pixel, hsv)
            totalSaturation += hsv[1]
        }

        return totalSaturation / pixels.size
    }
}

enum class DetectionMode {
    AUTO,
    MANGA,
    WESTERN,
    STRIP,
    WEBTOON,
}
