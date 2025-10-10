
package org.koitharu.kotatsu.panelview.settings

import android.os.Parcelable
import androidx.annotation.FloatRange
import kotlinx.parcelize.Parcelize
import org.koitharu.kotatsu.panelview.detection.DetectionMode

@Parcelize
enum class PanelReadingOrder : Parcelable {
    STANDARD,
    MANGA,
    FOUR_KOMA,
}

@Parcelize
data class PanelDetectionOptions(
    val enabled: Boolean,
    val smartSplitting: Boolean,
    val detectionMode: DetectionMode,
) : Parcelable

@Parcelize
data class PanelEnhancementOptions(
    val autoSwitchIrregular: Boolean,
    val fitToWidth: Boolean,
    val panBound: Boolean,
    val overlayEnabled: Boolean,
    val zoomEnabled: Boolean,
    @FloatRange(from = 0.0, to = 1.0)
    val borderOpacity: Float,
) : Parcelable

@Parcelize
data class PanelViewSettings(
    val detection: PanelDetectionOptions,
    val readingOrder: PanelReadingOrder,
    val enhancements: PanelEnhancementOptions,
) : Parcelable
