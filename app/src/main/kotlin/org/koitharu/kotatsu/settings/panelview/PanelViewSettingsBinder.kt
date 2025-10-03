package org.koitharu.kotatsu.settings.panelview

import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ReaderMode
import org.koitharu.kotatsu.settings.utils.PercentSummaryProvider
import org.koitharu.kotatsu.settings.utils.SliderPreference
import org.kotatsu.panelview.settings.PanelReadingOrder

class PanelViewSettingsBinder(private val fragment: PreferenceFragmentCompat) {

    fun initialise(settings: AppSettings, readerMode: ReaderMode) {
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

        updateVisibility(readerMode)
    }

    fun updateVisibility(readerMode: ReaderMode) {
        val visible = readerMode == ReaderMode.PANEL
        PANEL_KEYS.forEach { key ->
            fragment.findPreference<Preference>(key)?.isVisible = visible
        }
    }

    companion object {
        private const val PANEL_VIEW_CATEGORY_KEY = "panel_view_settings_group"

        private val PANEL_KEYS = arrayOf(
            PANEL_VIEW_CATEGORY_KEY,
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
    }
}
