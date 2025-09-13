package org.kotatsu.panelview

import android.graphics.Rect

object PanelOrder {
    fun order(panels: List<Rect>, rtl: Boolean): List<Rect> {
        return panels.sortedWith(
            compareBy<Rect> { it.top }.thenBy { if (rtl) -it.left else it.left }
        )
    }
}

