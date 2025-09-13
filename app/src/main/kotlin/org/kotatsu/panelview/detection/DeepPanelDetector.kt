package org.kotatsu.panelview.detection

import android.graphics.Bitmap
import android.graphics.Rect
import com.github.pedrovgs.deeppanel.DeepPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DeepPanelDetector {
    suspend fun detect(bitmap: Bitmap): List<Rect> = withContext(Dispatchers.Default) {
        return@withContext runCatching {
            val results = DeepPanel().extractPanelsInfo(bitmap)
            results.map {
                Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
            }
        }.getOrElse { emptyList() }
    }
}

