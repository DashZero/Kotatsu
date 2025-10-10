
package org.koitharu.kotatsu.panelview.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.databinding.BottomSheetPanelSettingsBinding
import org.koitharu.kotatsu.panelview.detection.DetectionMode
import org.koitharu.kotatsu.panelview.settings.PanelReadingOrder
import org.koitharu.kotatsu.panelview.settings.PanelViewSettings
import org.koitharu.kotatsu.panelview.settings.PanelDetectionOptions
import org.koitharu.kotatsu.panelview.settings.PanelEnhancementOptions

class PanelSettingsBottomSheet : BottomSheetDialogFragment() {

    interface OnSettingsChangedListener {
        fun onSettingsChanged(settings: PanelViewSettings)
    }

    private var _binding: BottomSheetPanelSettingsBinding? = null
    private val binding get() = _binding!!

    private var listener: OnSettingsChangedListener? = null
    private lateinit var currentSettings: PanelViewSettings

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as? OnSettingsChangedListener
            ?: context as? OnSettingsChangedListener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPanelSettingsBinding.inflate(inflater, container, false)
        currentSettings = arguments?.getParcelable(ARG_SETTINGS) ?: PanelViewSettings(
            detection = PanelDetectionOptions(true, true, DetectionMode.AUTO),
            readingOrder = PanelReadingOrder.STANDARD,
            enhancements = PanelEnhancementOptions(true, true, true, true, true, 0.35f)
        )
        setupUi()
        setupListeners()
        return binding.root
    }

    private fun setupUi() {
        binding.panelViewSwitch.isChecked = currentSettings.detection.enabled
        // Detection Mode
        binding.detectionModeGroup.check(
            when (currentSettings.detection.detectionMode) {
                DetectionMode.AUTO -> R.id.detection_mode_auto
                DetectionMode.MANGA -> R.id.detection_mode_manga
                DetectionMode.WESTERN -> R.id.detection_mode_western
                DetectionMode.STRIP -> R.id.detection_mode_strip
                DetectionMode.WEBTOON -> R.id.detection_mode_webtoon
            }
        )

        // Reading Order
        binding.readingOrderGroup.check(
            when (currentSettings.readingOrder) {
                PanelReadingOrder.STANDARD -> R.id.reading_order_standard
                PanelReadingOrder.MANGA -> R.id.reading_order_manga
                PanelReadingOrder.FOUR_KOMA -> R.id.reading_order_four_koma
            }
        )

        // Enhancements
        binding.overlaySwitch.isChecked = currentSettings.enhancements.overlayEnabled
        binding.zoomSwitch.isChecked = currentSettings.enhancements.zoomEnabled
        binding.overlayOpacitySlider.value = currentSettings.enhancements.borderOpacity.coerceIn(0f, 1f)
        updateOverlayOpacityLabel(currentSettings.enhancements.borderOpacity)
        updateEnabledStates()
        updateOverlayControls()
    }

    private fun setupListeners() {
        binding.panelViewSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateSettings(
                currentSettings.copy(
                    detection = currentSettings.detection.copy(enabled = isChecked)
                )
            )
        }

        binding.detectionModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.detection_mode_manga -> DetectionMode.MANGA
                R.id.detection_mode_western -> DetectionMode.WESTERN
                R.id.detection_mode_strip -> DetectionMode.STRIP
                R.id.detection_mode_webtoon -> DetectionMode.WEBTOON
                else -> DetectionMode.AUTO
            }
            updateSettings(
                currentSettings.copy(
                    detection = currentSettings.detection.copy(detectionMode = newMode)
                )
            )
        }

        binding.readingOrderGroup.setOnCheckedChangeListener { _, checkedId ->
            val newOrder = when (checkedId) {
                R.id.reading_order_manga -> PanelReadingOrder.MANGA
                R.id.reading_order_four_koma -> PanelReadingOrder.FOUR_KOMA
                else -> PanelReadingOrder.STANDARD
            }
            updateSettings(currentSettings.copy(readingOrder = newOrder))
        }

        binding.overlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            updateSettings(
                currentSettings.copy(
                    enhancements = currentSettings.enhancements.copy(overlayEnabled = isChecked)
                )
            )
            updateOverlayControls()
        }

        binding.overlayOpacitySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                updateSettings(
                    currentSettings.copy(
                        enhancements = currentSettings.enhancements.copy(borderOpacity = value)
                    )
                )
            }
        }

        binding.zoomSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateSettings(
                currentSettings.copy(
                    enhancements = currentSettings.enhancements.copy(zoomEnabled = isChecked)
                )
            )
        }
    }

    private fun updateSettings(settings: PanelViewSettings) {
        currentSettings = settings
        listener?.onSettingsChanged(currentSettings)
        updateEnabledStates()
        updateOverlayControls()
        updateOverlayOpacityLabel(currentSettings.enhancements.borderOpacity)
    }

    private fun updateEnabledStates() {
        val enabled = currentSettings.detection.enabled
        binding.detectionModeGroup.children.forEach { it.isEnabled = enabled }
        binding.readingOrderGroup.children.forEach { it.isEnabled = enabled }
        binding.overlaySwitch.isEnabled = enabled
        binding.zoomSwitch.isEnabled = enabled
    }

    private fun updateOverlayControls() {
        val enabled = currentSettings.detection.enabled && currentSettings.enhancements.overlayEnabled
        binding.overlayOpacityLabel.isEnabled = enabled
        binding.overlayOpacitySlider.isEnabled = enabled
    }

    private fun updateOverlayOpacityLabel(value: Float) {
        val percentage = (value * 100).toInt().coerceIn(0, 100)
        binding.overlayOpacityLabel.text = getString(R.string.panel_settings_overlay_opacity_value, percentage)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object {
        private const val ARG_SETTINGS = "settings"

        fun newInstance(settings: PanelViewSettings): PanelSettingsBottomSheet {
            return PanelSettingsBottomSheet().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_SETTINGS, settings)
                }
            }
        }
    }
}
