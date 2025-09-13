package org.kotatsu.panelview

import android.graphics.Bitmap
import android.graphics.Rect

data class PanelReaderState(
    val pageBitmap: Bitmap,
    val panels: List<Rect>,
    val currentIndex: Int = 0
) {
    val currentPanel: Rect
        get() = panels[currentIndex]
}

