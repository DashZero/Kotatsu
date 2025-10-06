package org.kotatsu.panelview

import android.graphics.Rect
import org.kotatsu.panelview.settings.PanelReadingOrder

object PanelOrder {
    private const val ROW_OVERLAP_THRESHOLD = 0.35f

    fun order(panels: List<Rect>, readingOrder: PanelReadingOrder): List<Rect> {
        return when (readingOrder) {
            PanelReadingOrder.STANDARD -> orderByRows(panels, reverseHorizontal = false)
            PanelReadingOrder.MANGA -> orderByRows(panels, reverseHorizontal = true)
            PanelReadingOrder.FOUR_KOMA -> panels.sortedWith(compareBy<Rect> { it.left }.thenBy { it.top })
        }
    }

    private fun orderByRows(panels: List<Rect>, reverseHorizontal: Boolean): List<Rect> {
        if (panels.isEmpty()) return panels
        val sorted = panels.sortedBy { it.top }
        val rows = mutableListOf<PanelRow>()

        for (rect in sorted) {
            val existing = rows.firstOrNull { overlapsVertically(it, rect) }
            if (existing != null) {
                existing.rects += rect
                existing.top = minOf(existing.top, rect.top)
                existing.bottom = maxOf(existing.bottom, rect.bottom)
            } else {
                rows += PanelRow(rect.top, rect.bottom, mutableListOf(rect))
            }
        }

        val horizontalComparator = if (reverseHorizontal) {
            compareByDescending<Rect> { it.left }
        } else {
            compareBy<Rect> { it.left }
        }

        return rows
            .sortedBy { it.top }
            .flatMap { row -> row.rects.sortedWith(horizontalComparator) }
    }

    private fun overlapsVertically(row: PanelRow, rect: Rect): Boolean {
        val intersection = minOf(row.bottom, rect.bottom) - maxOf(row.top, rect.top)
        if (intersection <= 0) return false
        val minHeight = minOf(row.bottom - row.top, rect.height())
        if (minHeight <= 0) return false
        return intersection >= minHeight * ROW_OVERLAP_THRESHOLD
    }

    private data class PanelRow(var top: Int, var bottom: Int, val rects: MutableList<Rect>)
}
