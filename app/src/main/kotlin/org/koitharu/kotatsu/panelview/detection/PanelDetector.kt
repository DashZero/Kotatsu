
package org.koitharu.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Rect

interface PanelDetector {
    fun detect(bitmap: Bitmap): List<Rect>
}
