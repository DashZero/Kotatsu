package org.koitharu.kotatsu.settings.panelview

import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.settings.utils.PercentSummaryProvider
import org.koitharu.kotatsu.settings.utils.SliderPreference
import org.kotatsu.panelview.settings.PanelReadingOrder

class PanelViewSettingsBinder(private val fragment: PreferenceFragmentCompat) {

    fun initialise(settings: AppSettings) {
        fragment.findPreference<SliderPreference>(AppSettings.KEY_PANEL_BORDER_OPACITY)?.apply {
            summaryProvider = PercentSummaryProvider()
        }

        val readingOrderPref = fragment.findPreference<ListPreference>(AppSettings.KEY_PANEL_READING_ORDER)
        val autoSwitchPref = fragment.findPreference<SwitchPreferenceCompat>(AppSettings.KEY_PANEL_AUTO_SWITCH_IRREGULAR)

        readingOrderPref?.setOnPreferenceChangeListener { _, newValue ->
            val order = runCatching { PanelReadingOrder.valueOf(newValue as String) }.getOrNull()
            if (order == PanelReadingOrder.MANGA && autoSwitchPref?.isChecked == false) {
                autoSwitchPref.isChecked = true
            }
            true
        }

        if (settings.panelReadingOrder == PanelReadingOrder.MANGA && autoSwitchPref?.isChecked == false) {
            autoSwitchPref.isChecked = true
        }

        setVisible(settings.isPanelViewEnabled)
    }

    fun setVisible(isVisible: Boolean) {
        (CATEGORY_KEYS + PANEL_PREF_KEYS).forEach { key ->
            fragment.findPreference<Preference>(key)?.isVisible = isVisible
        }
    }

    companion object {
        private val CATEGORY_KEYS = arrayOf(
            PANEL_CATEGORY_FRAME,
            PANEL_CATEGORY_SCAN,
            PANEL_CATEGORY_READING,
            PANEL_CATEGORY_ENHANCEMENTS,
        )

        private val PANEL_PREF_KEYS = arrayOf(
            AppSettings.KEY_PANEL_DISABLE_FRAME,
            AppSettings.KEY_PANEL_INLINE_FRAMES,
            AppSettings.KEY_PANEL_SCAN_TYPE,
            AppSettings.KEY_PANEL_READING_ORDER,
            AppSettings.KEY_PANEL_AUTO_SWITCH_IRREGULAR,
            AppSettings.KEY_PANEL_FIT_TO_WIDTH,
            AppSettings.KEY_PANEL_PAN_BOUND,
            AppSettings.KEY_PANEL_BORDER_OPACITY,
            AppSettings.KEY_PANEL_REDUCE_ANIMATIONS,
        )

        const val PANEL_CATEGORY_FRAME = "panel_frame_detection_category"
        const val PANEL_CATEGORY_SCAN = "panel_scan_type_category"
        const val PANEL_CATEGORY_READING = "panel_reading_order_category"
        const val PANEL_CATEGORY_ENHANCEMENTS = "panel_enhancements_category"
    }
}