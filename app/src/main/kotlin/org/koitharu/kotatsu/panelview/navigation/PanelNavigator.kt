package org.koitharu.kotatsu.panelview.navigation

import android.graphics.Rect

class PanelNavigator(panels: List<Rect>) {
    private var currentIndex = 0
    private var internalPanels: List<Rect> = panels.toList()

    val size: Int
        get() = internalPanels.size

    fun current(): Rect? = internalPanels.getOrNull(currentIndex)

    fun hasNext(): Boolean = currentIndex < internalPanels.lastIndex

    fun hasPrevious(): Boolean = currentIndex > 0

    fun next(): Rect? {
        if (hasNext()) {
            currentIndex++
        }
        return current()
    }

    fun previous(): Rect? {
        if (hasPrevious()) {
            currentIndex--
        }
        return current()
    }

    fun jumpTo(index: Int): Rect? {
        currentIndex = index.coerceIn(0, internalPanels.lastIndex.coerceAtLeast(0))
        return current()
    }

    fun update(panels: List<Rect>, anchor: Rect? = current()) {
        internalPanels = panels.toList()
        currentIndex = when {
            internalPanels.isEmpty() -> 0
            anchor == null -> 0
            else -> internalPanels.indexOfFirst { it == anchor }.takeIf { it >= 0 } ?: 0
        }
    }
}
