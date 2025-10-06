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

data class PanelDetectionOptions(
    val enabled: Boolean,
    val smartSplitting: Boolean,
)

data class PanelEnhancementOptions(
    val autoSwitchIrregular: Boolean,
    val fitToWidth: Boolean,
    val panBound: Boolean,
    @FloatRange(from = 0.0, to = 1.0)
    val borderOpacity: Float,
)

data class PanelViewSettings(
    val detection: PanelDetectionOptions,
    val scanType: PanelScanType,
    val readingOrder: PanelReadingOrder,
    val enhancements: PanelEnhancementOptions,
)
