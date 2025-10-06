package org.koitharu.kotatsu.panelview.ui.pager

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.decoder.SkiaPooledImageRegionDecoder
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.exceptions.resolve.ExceptionResolver
import org.koitharu.kotatsu.core.os.NetworkState
import org.koitharu.kotatsu.databinding.ItemPageBinding
import org.koitharu.kotatsu.panelview.PanelOrder
import org.koitharu.kotatsu.panelview.PanelReaderState
import org.koitharu.kotatsu.panelview.detection.PanelDetector
import org.koitharu.kotatsu.panelview.overlay.PanelOverlayView
import org.koitharu.kotatsu.panelview.settings.PanelViewSettings
import org.koitharu.kotatsu.reader.domain.PageLoader
import org.koitharu.kotatsu.reader.ui.config.ReaderSettings
import org.koitharu.kotatsu.reader.ui.pager.ReaderPage
import org.koitharu.kotatsu.reader.ui.pager.standard.PageHolder
import org.koitharu.kotatsu.reader.ui.pager.vm.PageState

class PanelPageHolder(
    owner: LifecycleOwner,
    binding: ItemPageBinding,
    loader: PageLoader,
    readerSettingsProducer: ReaderSettings.Producer,
    networkState: NetworkState,
    exceptionResolver: ExceptionResolver,
    private val panelSettings: PanelViewSettings,
    private val listener: Listener,
) : PageHolder(
    owner = owner,
    binding = binding,
    loader = loader,
    readerSettingsProducer = readerSettingsProducer,
    networkState = networkState,
    exceptionResolver = exceptionResolver,
) {

    interface Listener {
        fun onPanelStateReady(page: ReaderPage, state: PanelReaderState)
    }

    private val overlay: PanelOverlayView = binding.panelOverlay
    private var detectionJob: Job? = null
    private var pendingFocus: FocusRequest? = null

    init {
        overlay.overlayOpacity = panelSettings.enhancements.borderOpacity
    }

    override fun onStateChanged(state: PageState) {
        super.onStateChanged(state)
        when (state) {
            is PageState.Loaded -> startDetection(state.source)
            is PageState.Error, PageState.Empty -> clearOverlay()
            else -> Unit
        }
    }

    override fun onReady() {
        super.onReady()
        pendingFocus?.let { focusState(it.state, it.animate) }
    }

    fun render(state: PanelReaderState, animate: Boolean) {
        overlay.isVisible = state.panels.isNotEmpty()
        overlay.setContentBounds(state.contentWidth, state.contentHeight)
        overlay.setPanels(state.panels)
        overlay.highlight(state.currentIndex)
        focusState(state, animate)
    }

    override fun onRecycled() {
        detectionJob?.cancel()
        detectionJob = null
        pendingFocus = null
        clearOverlay()
        super.onRecycled()
    }

    private fun clearOverlay() {
        overlay.setPanels(emptyList())
        overlay.highlight(-1)
        overlay.isVisible = false
    }

    private fun startDetection(source: ImageSource) {
        val page = boundData ?: return
        detectionJob?.cancel()
        detectionJob = lifecycleScope.launch(Dispatchers.Default) {
            val state = runCatching { detectPanels(source) }.getOrNull()
            withContext(Dispatchers.Main) {
                val currentPage = boundData
                if (currentPage == null || currentPage.id != page.id || state == null) {
                    return@withContext
                }
                overlay.isVisible = true
                overlay.setContentBounds(state.contentWidth, state.contentHeight)
                overlay.setPanels(state.panels)
                overlay.highlight(-1)
                listener.onPanelStateReady(currentPage, state)
            }
        }
    }

    private suspend fun detectPanels(source: ImageSource): PanelReaderState = withContext(Dispatchers.IO) {
        val decoder = SkiaPooledImageRegionDecoder(Bitmap.Config.ARGB_8888)
        try {
            val size: Point = decoder.init(itemView.context, source)
            val sampleSize = calculateSample(size)
            val bitmap = decoder.decodeRegion(Rect(0, 0, size.x, size.y), sampleSize)
            try {
                ensureActive()
                val rawPanels = PanelDetector.detectPanels(bitmap, panelSettings)
                val scaleX = size.x / bitmap.width.toFloat()
                val scaleY = size.y / bitmap.height.toFloat()
                val scaledPanels = rawPanels.map { rect ->
                    Rect(
                        (rect.left * scaleX).roundToInt(),
                        (rect.top * scaleY).roundToInt(),
                        (rect.right * scaleX).roundToInt(),
                        (rect.bottom * scaleY).roundToInt(),
                    )
                }
                val ordered = PanelOrder.order(scaledPanels, panelSettings.readingOrder)
                val panels = ordered.takeIf { it.isNotEmpty() }
                    ?: listOf(Rect(0, 0, size.x, size.y))
                PanelReaderState(size.x, size.y, panels)
            } finally {
                bitmap.recycle()
            }
        } finally {
            decoder.recycle()
        }
    }

    private fun calculateSample(size: Point): Int {
        val maxSide = max(size.x, size.y).toFloat()
        val ratio = maxSide / MAX_BITMAP_SIDE
        return max(1, ceil(ratio).toInt())
    }

    private fun focusState(state: PanelReaderState, animate: Boolean) {
        val ssiv = binding.ssiv
        if (!ssiv.isReady || ssiv.width == 0 || ssiv.height == 0) {
            pendingFocus = FocusRequest(state, animate)
            return
        }
        pendingFocus = null
        val rect = state.currentPanel
        if (rect.width() <= 0 || rect.height() <= 0) {
            return
        }
        ssiv.minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CUSTOM
        val widthScale = ssiv.width / rect.width().toFloat()
        val heightScale = ssiv.height / rect.height().toFloat()
        val targetScale = if (panelSettings.enhancements.fitToWidth) {
            widthScale
        } else {
            max(widthScale, heightScale)
        }
        val scale = max(targetScale, MIN_SCALE)
        val center = PointF(rect.centerX().toFloat(), rect.centerY().toFloat())
        if (animate) {
            ssiv.animateScaleAndCenter(scale, center)?.start()
        } else {
            ssiv.setScaleAndCenter(scale, center)
        }
    }

    private data class FocusRequest(val state: PanelReaderState, val animate: Boolean)

    companion object {
        private const val MAX_BITMAP_SIDE = 2000f
        private const val MIN_SCALE = 0.01f
    }
}
