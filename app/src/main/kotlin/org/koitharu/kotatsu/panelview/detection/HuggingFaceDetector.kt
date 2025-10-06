package org.koitharu.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Rect

object HuggingFaceDetector {
    fun detect(@Suppress("UNUSED_PARAMETER") bitmap: Bitmap): List<Rect> {
        // TODO: load TFLite model converted from HuggingFace PyTorch model
        return emptyList()
    }
}

