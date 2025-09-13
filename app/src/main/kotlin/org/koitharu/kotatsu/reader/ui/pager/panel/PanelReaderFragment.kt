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
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.exceptions.resolve.ExceptionResolver
import org.koitharu.kotatsu.core.os.NetworkState
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ReaderMode
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.databinding.FragmentReaderPanelBinding
import org.koitharu.kotatsu.panelview.PanelOrder
import org.koitharu.kotatsu.panelview.PanelReaderController
import org.koitharu.kotatsu.panelview.PanelReaderState
import org.koitharu.kotatsu.panelview.detection.PanelDetector
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

    @Inject
    lateinit var exceptionResolver: ExceptionResolver

    @Inject
    lateinit var networkState: NetworkState

    private val scope = CoroutineScope(Dispatchers.Main)
    private var loadJob: Job? = null

    private var pages: List<ReaderPage> = emptyList()
    private var pageIndex: Int = 0
    private var panelIndex: Int = 0
    private var reduceAnimations: Boolean = false
    private var isRtlPanels: Boolean = false

    private var controller: PanelReaderController? = null

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val w = viewBinding?.gestureOverlay?.width ?: return false
            val forward = if (!isRtlPanels) e.x > w / 2f else e.x < w / 2f
            if (forward) {
                next()
            } else {
                prev()
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            toggleFullPage()
            return true
        }
    }

    private lateinit var gestureDetector: GestureDetector
    private var fullPageMode: Boolean = false

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentReaderPanelBinding {
        return FragmentReaderPanelBinding.inflate(inflater, container, false)
    }

    override fun onViewBindingCreated(binding: FragmentReaderPanelBinding, savedInstanceState: Bundle?) {
        super.onViewBindingCreated(binding, savedInstanceState)
        reduceAnimations = settings.isPanelReduceAnimations
        // respect RTL if user selected reversed mode
        isRtlPanels = (viewModel.readerMode.value == ReaderMode.REVERSED)
        viewModel.readerMode.observe(viewLifecycleOwner) { mode ->
            isRtlPanels = mode == ReaderMode.REVERSED
        }

        gestureDetector = GestureDetector(binding.root.context, gestureListener)
        binding.gestureOverlay.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }

        // basic SSIV defaults
        with(binding.ssiv) {
            setMinimumDpi(80)
            maxScale = 8f
            setDoubleTapZoomDpi(160)
            setQuickScaleEnabled(false)
            setPanEnabled(false) // we drive panning ourselves for guided view
        }

        // apply background from reader settings
        viewModel.readerSettingsProducer.observe(viewLifecycleOwner) { producer ->
            producer.applyBackground(binding.root)
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
        if (c.nextPanel()) {
            panelIndex = c.getState().currentIndex
            focusPanel(c.getState().currentPanel, animate = !reduceAnimations)
        } else if (pageIndex < pages.lastIndex) {
            showPage(pageIndex + 1)
        }
    }

    private fun prev() {
        val c = controller
        if (c == null) return
        if (c.prevPanel()) {
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
            val bitmap = withContext(Dispatchers.Default) { decodeBitmapForDetection(localUri) }
            val rects = PanelDetector.detectPanels(bitmap)
            val ordered = PanelOrder.order(rects, rtl = isRtlPanels)
            val panels = if (ordered.isNotEmpty()) ordered else listOf(Rect(0, 0, bitmap.width, bitmap.height))
            panelIndex = restorePanelIndex?.let { rpi ->
                when (rpi) {
                    Int.MAX_VALUE -> panels.lastIndex
                    else -> rpi.coerceIn(0, panels.lastIndex)
                }
            } ?: 0

            controller = PanelReaderController(
                state = PanelReaderState(pageBitmap = bitmap, panels = panels, currentIndex = panelIndex)
            )

            // show image
            binding.ssiv.setImage(ImageSource.uri(uri))
            binding.ssiv.setOnImageEventListener(object : SubsamplingScaleImageView.OnImageEventListener {
                override fun onReady() {
                    binding.gestureOverlay.isVisible = true
                    fullPageMode = false
                    focusPanel(panels[panelIndex], animate = !reduceAnimations)
                }

                override fun onImageLoaded() {}
                override fun onPreviewLoadError(e: Exception) {}
                override fun onImageLoadError(e: Exception) {}
                override fun onTileLoadError(e: Exception) {}
                override fun onPreviewReleased() {}
            })
        }
    }

    private fun focusPanel(rect: Rect, animate: Boolean) {
        val ssiv = viewBinding?.ssiv ?: return
        val vw = ssiv.width.toFloat().coerceAtLeast(1f)
        val vh = ssiv.height.toFloat().coerceAtLeast(1f)
        val rw = rect.width().toFloat().coerceAtLeast(1f)
        val rh = rect.height().toFloat().coerceAtLeast(1f)
        val scale = minOf(vw / rw, vh / rh)
        val center = PointF(rect.centerX().toFloat(), rect.centerY().toFloat())
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

    private suspend fun decodeBitmapForDetection(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val file = uri.toFile()
        val opts = BitmapFactory.Options()
        opts.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val maxDim = maxOf(opts.outWidth, opts.outHeight).coerceAtLeast(1)
        var sample = 1
        var current = maxDim
        while (current > 2560) { // keep under ~2.5k px for memory
            current /= 2
            sample *= 2
        }
        val real = BitmapFactory.Options().apply { inSampleSize = sample }
        return@withContext BitmapFactory.decodeFile(file.absolutePath, real)
            ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    }
}
