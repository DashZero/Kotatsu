package org.koitharu.kotatsu.reader.ui.pager.panel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toFile
import androidx.core.view.isVisible
import com.davemorrissey.labs.subscaleview.DefaultOnImageEventListener
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.os.NetworkState
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ReaderMode
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.databinding.FragmentReaderPanelBinding
import org.kotatsu.panelview.PanelOrder
import org.kotatsu.panelview.PanelReaderController
import org.kotatsu.panelview.PanelReaderState
import org.kotatsu.panelview.detection.PanelDetector
import org.kotatsu.panelview.settings.PanelReadingOrder
import org.kotatsu.panelview.settings.PanelViewSettings
import android.util.Log
import org.koitharu.kotatsu.reader.domain.PageLoader
import org.koitharu.kotatsu.reader.ui.ReaderState
import org.koitharu.kotatsu.reader.ui.pager.BaseReaderAdapter
import org.koitharu.kotatsu.reader.ui.pager.BaseReaderFragment
import org.koitharu.kotatsu.reader.ui.pager.ReaderPage
import org.koitharu.kotatsu.reader.ui.pager.standard.PagesAdapter
import javax.inject.Inject

@AndroidEntryPoint
class PanelReaderFragment : BaseReaderFragment<FragmentReaderPanelBinding>() {

    @Inject
    lateinit var pageLoader: PageLoader

    @Inject
    lateinit var settings: AppSettings

    // Use ExceptionResolver from BaseFragment via EntryPoint factory

    @Inject
    lateinit var networkState: NetworkState

    private val scope = CoroutineScope(Dispatchers.Main)
    private var loadJob: Job? = null

    private lateinit var panelSettings: PanelViewSettings
    private var currentReaderMode: ReaderMode? = null

    private var pages: List<ReaderPage> = emptyList()
    private var pageIndex: Int = 0
    private var panelIndex: Int = 0
    private var reduceAnimations: Boolean = false
    private var isRtlPanels: Boolean = false

    private var controller: PanelReaderController? = null

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val w = viewBinding?.gestureOverlay?.width ?: return false
            val third = w / 3f
            when {
                // left third => prev
                e.x < third -> prev()
                // right third => next / start panels
                e.x > w - third -> next()
                // center => toggle reader UI (toolbar & controls)
                else -> (activity as? org.koitharu.kotatsu.reader.ui.ReaderActivity)?.toggleUiVisibility()
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            toggleFullPage()
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            val dx = (e2.x) - (e1?.x ?: 0f)
            val absDx = kotlin.math.abs(dx)
            val absVy = kotlin.math.abs(velocityY)
            if (absDx > 80 && absVy < 3000f) {
                val forward = if (!isRtlPanels) dx < 0 else dx > 0
                if (forward) next() else prev()
                return true
            }
            return false
        }
    }

    private lateinit var gestureDetector: GestureDetector
    private var fullPageMode: Boolean = false

    private fun refreshPanelSettings(updateMask: Boolean = true) {
        panelSettings = settings.panelViewSettings
        reduceAnimations = settings.isPanelReduceAnimations
        updateReadingDirection()
        if (updateMask) {
            viewBinding?.maskOverlay?.setMaskOpacity(panelSettings.enhancements.borderOpacity)
        }
    }

    private fun updateReadingDirection(mode: ReaderMode? = currentReaderMode) {
        currentReaderMode = mode ?: currentReaderMode
        if (!this::panelSettings.isInitialized) {
            return
        }
        val activeMode = currentReaderMode
        isRtlPanels = when (panelSettings.readingOrder) {
            PanelReadingOrder.STANDARD -> activeMode == ReaderMode.REVERSED
            PanelReadingOrder.MANGA -> true
            PanelReadingOrder.FOUR_KOMA -> false
        }
    }

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentReaderPanelBinding {
        return FragmentReaderPanelBinding.inflate(inflater, container, false)
    }

    override fun onViewBindingCreated(binding: FragmentReaderPanelBinding, savedInstanceState: Bundle?) {
        super.onViewBindingCreated(binding, savedInstanceState)
        currentReaderMode = viewModel.readerMode.value
        refreshPanelSettings()
        viewModel.readerMode.observe(viewLifecycleOwner) { mode ->
            updateReadingDirection(mode)
        }

        gestureDetector = GestureDetector(binding.root.context, gestureListener)
        binding.gestureOverlay.isClickable = true
        binding.gestureOverlay.isFocusable = true
        binding.gestureOverlay.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true // consume so underlying views don't conflict
        }

        // basic SSIV defaults
        with(binding.ssiv) {
            setMinimumDpi(80)
            maxScale = 8f
            setDoubleTapZoomDpi(160)
            // Keep default pan/quick-scale behavior from the library
        }

        // apply background from reader settings
        viewModel.readerSettingsProducer.observe(viewLifecycleOwner) { producer ->
            producer.applyBackground(binding.root)
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewBinding != null) {
            refreshPanelSettings()
        }
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        super.onDestroyView()
    }

    override fun onCreateAdapter(): BaseReaderAdapter<*> = PagesAdapter(
        lifecycleOwner = viewLifecycleOwner,
        loader = pageLoader,
        readerSettingsProducer = viewModel.readerSettingsProducer,
        networkState = networkState,
        exceptionResolver = exceptionResolver,
    )

    override suspend fun onPagesChanged(pages: List<ReaderPage>, pendingState: ReaderState?) {
        this.pages = pages
        // keep adapter in sync for BaseReaderFragment book-keeping
        requireAdapter().setItems(pages)

        val targetIndex = pendingState?.let { state ->
            pages.indexOfFirst { it.chapterId == state.chapterId && it.index == state.page }.takeIf { it >= 0 }
        } ?: pageIndex.coerceIn(0, (pages.lastIndex).coerceAtLeast(0))

        showPage(targetIndex, restorePanelIndex = pendingState?.scroll ?: 0)
    }

    override fun getCurrentState(): ReaderState? {
        val page = pages.getOrNull(pageIndex) ?: return null
        return ReaderState(
            chapterId = page.chapterId,
            page = page.index,
            scroll = panelIndex,
        )
    }

    override fun switchPageBy(delta: Int) {
        val newIndex = (pageIndex + delta).coerceIn(0, pages.lastIndex)
        if (newIndex != pageIndex) {
            showPage(newIndex)
        }
    }

    override fun switchPageTo(position: Int, smooth: Boolean) {
        val newIndex = position.coerceIn(0, pages.lastIndex)
        if (newIndex != pageIndex) {
            showPage(newIndex)
        }
    }

    private fun next() {
        val c = controller
        if (c == null) return
        if (fullPageMode) {
            fullPageMode = false
            panelIndex = c.getState().currentIndex
            focusPanel(c.getState().currentPanel, animate = !reduceAnimations)
        } else if (c.nextPanel()) {
            panelIndex = c.getState().currentIndex
            focusPanel(c.getState().currentPanel, animate = !reduceAnimations)
        } else if (pageIndex < pages.lastIndex) {
            showPage(pageIndex + 1)
        }
    }

    private fun prev() {
        val c = controller
        if (c == null) return
        if (fullPageMode) {
            if (pageIndex > 0) {
                showPage(pageIndex - 1, restorePanelIndex = Int.MAX_VALUE)
            }
        } else if (c.prevPanel()) {
            panelIndex = c.getState().currentIndex
            focusPanel(c.getState().currentPanel, animate = !reduceAnimations)
        } else if (pageIndex > 0) {
            showPage(pageIndex - 1, restorePanelIndex = Int.MAX_VALUE)
        }
    }

    private fun toggleFullPage() {
        fullPageMode = !fullPageMode
        val ssiv = viewBinding?.ssiv ?: return
        val c = controller ?: return
        if (fullPageMode) {
            // fit center
            ssiv.minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
            ssiv.resetScaleAndCenter()
            viewBinding?.maskOverlay?.setPanelRect(null)
            viewBinding?.maskOverlay?.visibility = View.GONE
        } else {
            focusPanel(c.getState().currentPanel, animate = !reduceAnimations)
        }
    }

    private fun showPage(index: Int, restorePanelIndex: Int? = null) {
        val binding = viewBinding ?: return
        if (pages.isEmpty()) return
        pageIndex = index
        panelIndex = 0
        viewModel.onCurrentPageChanged(index, index)

        loadJob?.cancel()
        loadJob = scope.launch {
            binding.gestureOverlay.isVisible = false
            val page = pages[index]
            val uri = withContext(Dispatchers.Default) { pageLoader.loadPage(page.toMangaPage(), force = false) }
            // Decode downsized bitmap for detection
            val localUri = withContext(Dispatchers.Default) { pageLoader.convertBimap(uri) }
            refreshPanelSettings()
            val detection = withContext(Dispatchers.Default) { decodeBitmapForDetection(localUri) }
            val rects = PanelDetector.detectPanels(detection.bitmap, panelSettings)
            val scaledRects = rects.map { r ->
                Rect(
                    (r.left * detection.scaleX).roundToInt(),
                    (r.top * detection.scaleY).roundToInt(),
                    (r.right * detection.scaleX).roundToInt(),
                    (r.bottom * detection.scaleY).roundToInt(),
                )
            }
            val ordered = PanelOrder.order(scaledRects, panelSettings.readingOrder)
            // Fallback to full page in source coordinate space when no panels detected
            val fullW = (detection.bitmap.width * detection.scaleX).roundToInt()
            val fullH = (detection.bitmap.height * detection.scaleY).roundToInt()
            val panels = if (ordered.isNotEmpty()) ordered else listOf(Rect(0, 0, fullW, fullH))
            Log.d("PanelReader", "Detected panels: ${panels.size} on page ${page.index}")
            panelIndex = restorePanelIndex?.let { rpi ->
                when (rpi) {
                    Int.MAX_VALUE -> panels.lastIndex
                    else -> rpi.coerceIn(0, panels.lastIndex)
                }
            } ?: 0

            controller = PanelReaderController(
                // pageBitmap is not used by navigation; provide detection bitmap to keep state valid
                state = PanelReaderState(pageBitmap = detection.bitmap, panels = panels, currentIndex = panelIndex)
            )

            // show image
            binding.ssiv.setImage(ImageSource.uri(uri))
            binding.ssiv.addOnImageEventListener(object : DefaultOnImageEventListener {
                override fun onReady() {
                    binding.gestureOverlay.isVisible = true
                    // Start with full page view; first user action focuses the first panel
                    fullPageMode = true
                    val ssiv = binding.ssiv
                    ssiv.minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
                    ssiv.maxScale = 10f
                    ssiv.panLimit = if (panelSettings.enhancements.panBound) {
                        SubsamplingScaleImageView.PAN_LIMIT_INSIDE
                    } else {
                        SubsamplingScaleImageView.PAN_LIMIT_OUTSIDE
                    }
                    ssiv.resetScaleAndCenter()
                    // Attach dark mask overlay
                    binding.maskOverlay.attach(ssiv)
                    binding.maskOverlay.setMaskOpacity(panelSettings.enhancements.borderOpacity)
                    binding.maskOverlay.visibility = View.GONE
                }
            })
        }
    }

    private fun focusPanel(rect: Rect, animate: Boolean) {
        val binding = viewBinding ?: return
        val ssiv = binding.ssiv
        val mask = binding.maskOverlay
        // Add ~5% padding around panel for breathing room
        val pad = (minOf(rect.width(), rect.height()) * 0.05f).toInt()
        val padded = Rect(
            (rect.left - pad).coerceAtLeast(0),
            (rect.top - pad).coerceAtLeast(0),
            rect.right + pad,
            rect.bottom + pad
        )
        mask.setPanelRect(padded)
        mask.visibility = View.VISIBLE

        val vw = ssiv.width.toFloat().coerceAtLeast(1f)
        val vh = ssiv.height.toFloat().coerceAtLeast(1f)
        val rw = padded.width().toFloat().coerceAtLeast(1f)
        val rh = padded.height().toFloat().coerceAtLeast(1f)
        val scale = if (panelSettings.enhancements.fitToWidth) {
            vw / rw
        } else {
            minOf(vw / rw, vh / rh)
        }
        val center = PointF(padded.centerX().toFloat(), padded.centerY().toFloat())
        ssiv.minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CUSTOM
        ssiv.minScale = scale
        if (animate && isAnimationEnabled()) {
            ssiv.animateScaleAndCenter(scale, center)?.withDuration(
                ssiv.resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
            )?.start()
        } else {
            ssiv.setScaleAndCenter(scale, center)
        }
    }

    override fun onZoomIn() {
        // Panel reader uses tap navigation; zoom controls are no-ops here
    }

    override fun onZoomOut() {
        // Panel reader uses tap navigation; zoom controls are no-ops here
    }

    private data class DetectionBitmap(val bitmap: Bitmap, val scaleX: Float, val scaleY: Float)

    private suspend fun decodeBitmapForDetection(uri: Uri): DetectionBitmap = withContext(Dispatchers.IO) {
        // Try file path first
        runCatching {
            val file = uri.toFile()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val srcW = bounds.outWidth.coerceAtLeast(1)
            val srcH = bounds.outHeight.coerceAtLeast(1)
            var sample = 1
            var currentMax = maxOf(srcW, srcH)
            while (currentMax > 2560) { // keep under ~2.5k px on the longer side
                currentMax = (currentMax + 1) / 2
                sample *= 2
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw IllegalStateException("decodeFile returned null")
            DetectionBitmap(decoded, sample.toFloat(), sample.toFloat())
        }.getOrElse {
            // Fallback: decode via ContentResolver stream
            val cr = requireContext().contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val srcW = bounds.outWidth.coerceAtLeast(1)
            val srcH = bounds.outHeight.coerceAtLeast(1)
            var sample = 1
            var currentMax = maxOf(srcW, srcH)
            while (currentMax > 2560) {
                currentMax = (currentMax + 1) / 2
                sample *= 2
            }
            val decoded = cr.openInputStream(uri)?.use { inp ->
                BitmapFactory.decodeStream(inp, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            DetectionBitmap(decoded, sample.toFloat(), sample.toFloat())
        }
    }
}
