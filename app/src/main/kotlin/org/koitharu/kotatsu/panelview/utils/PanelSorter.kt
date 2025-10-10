package org.koitharu.kotatsu.panelview.utils

import android.graphics.Rect
import org.koitharu.kotatsu.panelview.detection.DetectionMode

object PanelSorter {
    fun sortPanels(panels: List<Rect>, mode: DetectionMode): List<Rect> {
        return when (mode) {
            DetectionMode.MANGA -> panels.sortedWith(compareBy<Rect> { it.top }.thenByDescending { it.right })
            DetectionMode.WESTERN -> panels.sortedWith(compareBy<Rect> { it.top }.thenBy { it.left })
            DetectionMode.STRIP -> panels.sortedBy { it.left }
            DetectionMode.WEBTOON -> panels.sortedBy { it.top }
            DetectionMode.AUTO -> panels
        }
    }
}
