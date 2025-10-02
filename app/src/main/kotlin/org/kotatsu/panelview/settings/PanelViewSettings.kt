package org.kotatsu.panelview.settings

import androidx.annotation.FloatRange

enum class PanelScanType {
    REGULAR,
    IRREGULAR,
    FOUR_QUADRANTS,
    WEBTOON,
}

enum class PanelReadingOrder {
    STANDARD,
    MANGA,
    FOUR_KOMA,
}

data class PanelFrameDetectionOptions(
    val disableFrame: Boolean,
    val inlineFrames: Boolean,
)

data class PanelEnhancementOptions(
    val autoSwitchIrregular: Boolean,
    val fitToWidth: Boolean,
    val panBound: Boolean,
    @FloatRange(from = 0.0, to = 1.0)
    val borderOpacity: Float,
)

data class PanelViewSettings(
    val frameDetection: PanelFrameDetectionOptions,
    val scanType: PanelScanType,
    val readingOrder: PanelReadingOrder,
    val enhancements: PanelEnhancementOptions,
)
