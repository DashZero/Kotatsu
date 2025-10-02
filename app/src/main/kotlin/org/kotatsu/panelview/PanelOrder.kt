package org.kotatsu.panelview

import android.graphics.Rect
import org.kotatsu.panelview.settings.PanelReadingOrder

object PanelOrder {
    fun order(panels: List<Rect>, readingOrder: PanelReadingOrder): List<Rect> {
        val comparator = when (readingOrder) {
            PanelReadingOrder.STANDARD -> compareBy<Rect> { it.top }.thenBy { it.left }
            PanelReadingOrder.MANGA -> compareBy<Rect> { it.top }.thenByDescending { it.left }
            PanelReadingOrder.FOUR_KOMA -> compareBy<Rect> { it.left }.thenBy { it.top }
        }
        return panels.sortedWith(comparator)
    }
}
