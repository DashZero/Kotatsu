
package org.koitharu.kotatsu.panelview.ui.pager

import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.appcompat.widget.PopupMenu
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlin.collections.set
import kotlin.math.sign
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ReaderMode
import org.koitharu.kotatsu.core.util.ext.findCurrentViewHolder
import org.koitharu.kotatsu.databinding.FragmentReaderPagerBinding
import org.koitharu.kotatsu.panelview.PanelReaderController
import org.koitharu.kotatsu.panelview.PanelReaderState
import org.koitharu.kotatsu.panelview.detection.DetectionMode
import org.koitharu.kotatsu.panelview.settings.PanelDetectionOptions
import org.koitharu.kotatsu.panelview.settings.PanelEnhancementOptions
import org.koitharu.kotatsu.panelview.settings.PanelReadingOrder
import org.koitharu.kotatsu.panelview.settings.PanelViewSettings
import org.koitharu.kotatsu.panelview.ui.settings.PanelSettingsBottomSheet
import org.koitharu.kotatsu.reader.ui.pager.BasePagerReaderFragment
import org.koitharu.kotatsu.reader.ui.pager.BaseReaderAdapter
import org.koitharu.kotatsu.reader.ui.pager.ReaderPage

@AndroidEntryPoint
class PanelReaderFragment : BasePagerReaderFragment(), PanelPageHolder.Listener,
    PanelSettingsBottomSheet.OnSettingsChangedListener {

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

    override fun onViewBindingCreated(binding: FragmentReaderPagerBinding, savedInstanceState: Bundle?) {
        super.onViewBindingCreated(binding, savedInstanceState)
        binding.panelSettingsButton.setOnClickListener {
            PanelSettingsBottomSheet.newInstance(panelSettings).show(childFragmentManager, "panel_settings")
        }
        binding.panelQuickToggleButton.setOnClickListener { anchor ->
            showQuickToggleMenu(anchor)
        }
        updateQuickToggleLabel()
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

    override fun onSettingsChanged(settings: PanelViewSettings) {
        val previous = panelSettings
        panelSettings = settings
        if (previous.detection.enabled != settings.detection.enabled ||
            previous.detection.detectionMode != settings.detection.detectionMode ||
            previous.readingOrder != settings.readingOrder
        ) {
            panelStates.clear()
            controller = null
        }
        (requireViewBinding().pager.findCurrentViewHolder() as? PanelPageHolder)?.updateSettings(settings)
        updateQuickToggleLabel()
    }

    override fun switchPageBy(delta: Int) {
        if (!panelSettings.detection.enabled) {
            super.switchPageBy(delta)
            return
        }
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
        if (!panelSettings.detection.enabled) {
            controller = null
            panelStates[page.id] = state.copy(currentIndex = 0)
            return
        }
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
        if (!panelSettings.detection.enabled) {
            return
        }
        val currentPage = currentReaderPage() ?: return
        val ctrl = controller ?: return
        val state = ctrl.getState()
        panelStates[currentPage.id] = state
        (requireViewBinding().pager.findCurrentViewHolder() as? PanelPageHolder)?.render(state, animate)
    }

    private fun updateControllerFor(position: Int) {
        if (!panelSettings.detection.enabled) {
            controller = null
            return
        }
        val adapter = requireAdapter() as PanelPagesAdapter
        val page = adapter.getItemOrNull(position)
        if (page == null) {
            controller = null
            return
        }
        val state = panelStates[page.id]
        controller = state?.let { PanelReaderController(it) }
        if (state != null) {
            (requireViewBinding().pager.findCurrentViewHolder() as? PanelPageHolder)?.render(state, animate = false)
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

    private fun showQuickToggleMenu(anchor: View) {
        val popup = PopupMenu(anchor.context, anchor)
        val menu = popup.menu

        val toggleTitle = if (panelSettings.detection.enabled) {
            getString(R.string.panel_quick_toggle_disable)
        } else {
            getString(R.string.panel_quick_toggle_enable)
        }
        menu.add(MENU_GROUP_MISC, MENU_ITEM_TOGGLE_PANEL, MENU_ORDER_TOGGLE, toggleTitle)

        val detectionHeader = menu.add(
            Menu.NONE,
            MENU_ITEM_HEADER_MODE,
            MENU_ORDER_HEADER_MODE,
            getString(R.string.panel_settings_detection_mode),
        )
        detectionHeader.isEnabled = false

        val detectionModes = listOf(
            DetectionMode.AUTO,
            DetectionMode.MANGA,
            DetectionMode.WESTERN,
            DetectionMode.STRIP,
            DetectionMode.WEBTOON,
        )
        detectionModes.forEachIndexed { index, mode ->
            val item = menu.add(
                MENU_GROUP_MODE,
                MENU_ITEM_MODE_BASE + index,
                MENU_ORDER_MODE + index,
                detectionModeLabel(mode),
            )
            item.isCheckable = true
            item.isEnabled = panelSettings.detection.enabled
            val currentMode = panelSettings.detection.detectionMode
            item.isChecked = when (mode) {
                DetectionMode.AUTO -> currentMode == DetectionMode.AUTO
                else -> currentMode == mode
            }
        }

        val readingOrders = listOf(
            PanelReadingOrder.STANDARD to R.string.panel_settings_reading_order_standard,
            PanelReadingOrder.MANGA to R.string.panel_settings_reading_order_manga,
            PanelReadingOrder.FOUR_KOMA to R.string.panel_settings_reading_order_four_koma,
        )

        val orderHeader = menu.add(
            Menu.NONE,
            MENU_ITEM_HEADER_ORDER,
            MENU_ORDER_HEADER_ORDER,
            getString(R.string.panel_settings_reading_order),
        )
        orderHeader.isEnabled = false

        readingOrders.forEachIndexed { index, (order, labelRes) ->
            val item = menu.add(
                MENU_GROUP_ORDER,
                MENU_ITEM_ORDER_BASE + index,
                MENU_ORDER_ORDER + index,
                getString(labelRes),
            )
            item.isCheckable = true
            item.isEnabled = panelSettings.detection.enabled
            item.isChecked = panelSettings.readingOrder == order
        }

        menu.setGroupCheckable(MENU_GROUP_MODE, true, true)
        menu.setGroupCheckable(MENU_GROUP_ORDER, true, true)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_ITEM_TOGGLE_PANEL -> {
                    val toggled = !panelSettings.detection.enabled
                    val newSettings = panelSettings.copy(
                        detection = panelSettings.detection.copy(enabled = toggled),
                    )
                    onSettingsChanged(newSettings)
                    true
                }

                in MENU_ITEM_MODE_BASE until MENU_ITEM_MODE_BASE + detectionModes.size -> {
                    val index = item.itemId - MENU_ITEM_MODE_BASE
                    val selectedMode = detectionModes.getOrNull(index) ?: return@setOnMenuItemClickListener false
                    val newSettings = panelSettings.copy(
                        detection = panelSettings.detection.copy(
                            detectionMode = selectedMode,
                            enabled = true,
                        ),
                    )
                    onSettingsChanged(newSettings)
                    true
                }

                in MENU_ITEM_ORDER_BASE until MENU_ITEM_ORDER_BASE + readingOrders.size -> {
                    val index = item.itemId - MENU_ITEM_ORDER_BASE
                    val selectedOrder = readingOrders.getOrNull(index)?.first ?: return@setOnMenuItemClickListener false
                    val newSettings = panelSettings.copy(readingOrder = selectedOrder)
                    onSettingsChanged(newSettings)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun updateQuickToggleLabel() {
        viewBinding?.panelQuickToggleButton?.text = if (!panelSettings.detection.enabled) {
            getString(R.string.panel_quick_toggle_disabled)
        } else {
            val detection = detectionModeLabel(panelSettings.detection.detectionMode)
            val order = readingOrderLabel(panelSettings.readingOrder)
            getString(R.string.panel_quick_toggle_label_pattern, detection, order)
        }
    }

    private fun detectionModeLabel(mode: DetectionMode): String = when (mode) {
        DetectionMode.AUTO -> getString(R.string.panel_detection_mode_auto)
        DetectionMode.MANGA -> getString(R.string.panel_detection_mode_manga)
        DetectionMode.WESTERN -> getString(R.string.panel_detection_mode_western)
        DetectionMode.STRIP -> getString(R.string.panel_detection_mode_strip)
        DetectionMode.WEBTOON -> getString(R.string.panel_detection_mode_webtoon)
    }

    private fun readingOrderLabel(order: PanelReadingOrder): String = when (order) {
        PanelReadingOrder.STANDARD -> getString(R.string.panel_settings_reading_order_standard)
        PanelReadingOrder.MANGA -> getString(R.string.panel_settings_reading_order_manga)
        PanelReadingOrder.FOUR_KOMA -> getString(R.string.panel_settings_reading_order_four_koma)
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
                detectionMode = DetectionMode.AUTO,
            ),
            readingOrder = readingOrder,
            enhancements = PanelEnhancementOptions(
                autoSwitchIrregular = true,
                fitToWidth = true,
                panBound = true,
                overlayEnabled = true,
                zoomEnabled = true,
                borderOpacity = DEFAULT_BORDER_OPACITY,
            ),
        )
    }

    companion object {
        private const val DEFAULT_BORDER_OPACITY = 0.35f
        private const val MAX_CACHED_PAGES = 12
        private const val MENU_GROUP_MISC = 0
        private const val MENU_GROUP_MODE = 1
        private const val MENU_GROUP_ORDER = 2
        private const val MENU_ITEM_TOGGLE_PANEL = 10
        private const val MENU_ITEM_MODE_BASE = 100
        private const val MENU_ITEM_ORDER_BASE = 200
        private const val MENU_ITEM_HEADER_MODE = 1000
        private const val MENU_ITEM_HEADER_ORDER = 1001
        private const val MENU_ORDER_TOGGLE = 0
        private const val MENU_ORDER_HEADER_MODE = 1
        private const val MENU_ORDER_MODE = 2
        private const val MENU_ORDER_HEADER_ORDER = 50
        private const val MENU_ORDER_ORDER = 51
    }
}
