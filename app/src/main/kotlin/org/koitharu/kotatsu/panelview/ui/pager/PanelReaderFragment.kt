package org.koitharu.kotatsu.panelview.ui.pager

import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlin.collections.set
import kotlin.math.sign
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ReaderMode
import org.koitharu.kotatsu.core.util.ext.findCurrentViewHolder
import org.koitharu.kotatsu.panelview.PanelReaderController
import org.koitharu.kotatsu.panelview.PanelReaderState
import org.koitharu.kotatsu.panelview.settings.PanelDetectionOptions
import org.koitharu.kotatsu.panelview.settings.PanelEnhancementOptions
import org.koitharu.kotatsu.panelview.settings.PanelReadingOrder
import org.koitharu.kotatsu.panelview.settings.PanelScanType
import org.koitharu.kotatsu.panelview.settings.PanelViewSettings
import org.koitharu.kotatsu.reader.ui.pager.BasePagerReaderFragment
import org.koitharu.kotatsu.reader.ui.pager.BaseReaderAdapter
import org.koitharu.kotatsu.reader.ui.pager.ReaderPage

@AndroidEntryPoint
class PanelReaderFragment : BasePagerReaderFragment(), PanelPageHolder.Listener {

    @Inject
    lateinit var appSettings: AppSettings

    private lateinit var panelSettings: PanelViewSettings
    private var controller: PanelReaderController? = null
    private val panelStates = object : LinkedHashMap<Long, PanelReaderState>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, PanelReaderState>?): Boolean {
            return size > MAX_CACHED_PAGES
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        panelSettings = buildPanelSettings()
    }

    override fun onCreateAdapter(): BaseReaderAdapter<*> = PanelPagesAdapter(
        lifecycleOwner = viewLifecycleOwner,
        loader = pageLoader,
        readerSettingsProducer = viewModel.readerSettingsProducer,
        networkState = networkState,
        exceptionResolver = exceptionResolver,
        panelSettings = panelSettings,
        listener = this,
    )

    override fun switchPageBy(delta: Int) {
        if (delta == 0) {
            return
        }
        val steps = abs(delta)
        val direction = delta.sign
        repeat(steps) {
            val handled = if (direction > 0) {
                controller?.let { ctrl ->
                    if (ctrl.isAtLastPanel()) {
                        false
                    } else {
                        ctrl.nextPanel()
                        applyCurrentPanel(animate = true)
                        true
                    }
                } ?: false
            } else {
                controller?.let { ctrl ->
                    if (ctrl.isAtFirstPanel()) {
                        false
                    } else {
                        ctrl.prevPanel()
                        applyCurrentPanel(animate = true)
                        true
                    }
                } ?: false
            }
            if (!handled) {
                super.switchPageBy(direction)
                return
            }
        }
    }

    override fun notifyPageChanged(page: Int) {
        super.notifyPageChanged(page)
        updateControllerFor(page)
    }

    override fun onPanelStateReady(page: ReaderPage, state: PanelReaderState) {
        val existing = panelStates[page.id]
        val currentIndex = when {
            isCurrentPage(page) && controller != null -> controller!!.getState().currentIndex
            existing != null -> existing.currentIndex
            else -> 0
        }
        val clampedIndex = if (state.panels.isNotEmpty()) {
            currentIndex.coerceIn(0, state.panels.lastIndex)
        } else {
            0
        }
        val mergedState = state.copy(currentIndex = clampedIndex)
        panelStates[page.id] = mergedState
        if (isCurrentPage(page)) {
            controller = PanelReaderController(mergedState)
            applyCurrentPanel(animate = false)
        }
    }

    private fun applyCurrentPanel(animate: Boolean) {
        val currentPage = currentReaderPage() ?: return
        val ctrl = controller ?: return
        val state = ctrl.getState()
        panelStates[currentPage.id] = state
        (viewBinding?.pager?.findCurrentViewHolder() as? PanelPageHolder)?.render(state, animate)
    }

    private fun updateControllerFor(position: Int) {
        val adapter = requireAdapter() as PanelPagesAdapter
        val page = adapter.getItemOrNull(position)
        if (page == null) {
            controller = null
            return
        }
        val state = panelStates[page.id]
        controller = state?.let { PanelReaderController(it) }
        if (state != null) {
            (viewBinding?.pager?.findCurrentViewHolder() as? PanelPageHolder)?.render(state, animate = false)
        }
    }

    private fun currentReaderPage(): ReaderPage? {
        val pager = viewBinding?.pager ?: return null
        val adapter = requireAdapter() as PanelPagesAdapter
        return adapter.getItemOrNull(pager.currentItem)
    }

    private fun isCurrentPage(page: ReaderPage): Boolean {
        val current = currentReaderPage() ?: return false
        return current.id == page.id
    }

    private fun buildPanelSettings(): PanelViewSettings {
        val readingOrder = when {
            appSettings.defaultReaderMode == ReaderMode.REVERSED && !appSettings.isReaderControlAlwaysLTR -> PanelReadingOrder.MANGA
            else -> PanelReadingOrder.STANDARD
        }
        return PanelViewSettings(
            detection = PanelDetectionOptions(
                enabled = true,
                smartSplitting = true,
            ),
            scanType = PanelScanType.REGULAR,
            readingOrder = readingOrder,
            enhancements = PanelEnhancementOptions(
                autoSwitchIrregular = true,
                fitToWidth = true,
                panBound = true,
                borderOpacity = DEFAULT_BORDER_OPACITY,
            ),
        )
    }

    companion object {
        private const val DEFAULT_BORDER_OPACITY = 0.35f
        private const val MAX_CACHED_PAGES = 12
    }
}

