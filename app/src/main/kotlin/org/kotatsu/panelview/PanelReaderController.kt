package org.kotatsu.panelview

class PanelReaderController(
    private var state: PanelReaderState,
    private val onRequestNextPage: (() -> PanelReaderState?)? = null,
    private val onRequestPrevPage: (() -> PanelReaderState?)? = null
) {
    fun getState(): PanelReaderState = state

    fun nextPanel(): Boolean {
        return if (state.currentIndex < state.panels.lastIndex) {
            state = state.copy(currentIndex = state.currentIndex + 1)
            true
        } else {
            onRequestNextPage?.invoke()?.let { newState ->
                state = newState
                true
            } ?: false
        }
    }

    fun prevPanel(): Boolean {
        return if (state.currentIndex > 0) {
            state = state.copy(currentIndex = state.currentIndex - 1)
            true
        } else {
            onRequestPrevPage?.invoke()?.let { newState ->
                state = newState
                true
            } ?: false
        }
    }

    fun isAtLastPanel(): Boolean = state.currentIndex == state.panels.lastIndex
    fun isAtFirstPanel(): Boolean = state.currentIndex == 0
}

