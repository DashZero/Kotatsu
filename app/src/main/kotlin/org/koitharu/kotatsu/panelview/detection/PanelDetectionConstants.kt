package org.koitharu.kotatsu.panelview.detection

import org.opencv.core.Size

object PanelDetectionConstants {
    const val MIN_PANEL_DIMENSION_PX = 40
    const val MIN_PANEL_AREA_RATIO = 0.05f
    const val RECTANGULARITY_THRESHOLD = 0.75
    const val MERGE_OVERLAP_FRACTION = 0.15f
    const val CANNY_THRESHOLD_LOW = 50.0
    const val CANNY_THRESHOLD_HIGH = 150.0
    val MORPH_KERNEL_SIZE: Size = Size(3.0, 3.0)
    const val WEBTOON_GAP_ROW_DENSITY = 0.10f
    const val WEBTOON_MIN_GAP_PX = 20
}
