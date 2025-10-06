package org.koitharu.kotatsu.panelview

import android.graphics.Rect

data class PanelReaderState(
    val contentWidth: Int,
    val contentHeight: Int,
    val panels: List<Rect>,
    val currentIndex: Int = 0,
) {
    val currentPanel: Rect
        get() = panels[currentIndex]
}
