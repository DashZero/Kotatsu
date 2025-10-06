package org.kotatsu.panelview.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class PanelOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(96, 33, 150, 243)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(160, 255, 193, 7)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = resources.displayMetrics.density * 2f
    }

    private var contentWidth = 1
    private var contentHeight = 1
    private var panels: List<Rect> = emptyList()
    private var highlightedIndex = -1

    var overlayOpacity: Float
        get() = overlayPaint.alpha / 255f
        set(value) {
            val alpha = (value.coerceIn(0f, 1f) * 255).toInt()
            overlayPaint.alpha = alpha
            invalidate()
        }

    fun setContentBounds(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        contentWidth = width
        contentHeight = height
        invalidate()
    }

    fun setPanels(rects: List<Rect>) {
        panels = rects.toList()
        if (highlightedIndex >= panels.size) {
            highlightedIndex = panels.lastIndex
        }
        invalidate()
    }

    fun highlight(index: Int) {
        highlightedIndex = if (index in panels.indices) index else -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (panels.isEmpty()) return
        val scaleX = width / contentWidth.toFloat()
        val scaleY = height / contentHeight.toFloat()
        panels.forEachIndexed { index, rect ->
            val scaled = Rect(
                (rect.left * scaleX).toInt(),
                (rect.top * scaleY).toInt(),
                (rect.right * scaleX).toInt(),
                (rect.bottom * scaleY).toInt(),
            )
            val paint = if (index == highlightedIndex) highlightPaint else overlayPaint
            canvas.drawRect(scaled, paint)
            canvas.drawRect(scaled, borderPaint)
        }
    }
}
