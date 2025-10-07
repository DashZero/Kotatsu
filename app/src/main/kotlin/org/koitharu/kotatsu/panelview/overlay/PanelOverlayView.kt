package org.koitharu.kotatsu.panelview.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

class PanelOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(48, 33, 150, 243)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(96, 255, 213, 79)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = resources.displayMetrics.density * 1.5f
    }

    private var imageView: SubsamplingScaleImageView? = null
    private var panels: List<Rect> = emptyList()
    private var highlightedIndex = -1

    private val tmpRectF = RectF()
    private val tmpPointTL = PointF()
    private val tmpPointBR = PointF()

    var overlayOpacity: Float
        get() = overlayPaint.alpha / 255f
        set(value) {
            val alpha = (value.coerceIn(0f, 1f) * 255).toInt()
            overlayPaint.alpha = alpha
            invalidate()
        }

    fun attachTo(imageView: SubsamplingScaleImageView) {
        if (this.imageView === imageView) return
        this.imageView = imageView
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
        val ssiv = imageView ?: return
        if (!ssiv.isReady || panels.isEmpty()) {
            return
        }
        panels.forEachIndexed { index, rect ->
            if (!rect.isEmpty) {
                val tl = ssiv.sourceToViewCoord(rect.left.toFloat(), rect.top.toFloat(), tmpPointTL)
                val br = ssiv.sourceToViewCoord(rect.right.toFloat(), rect.bottom.toFloat(), tmpPointBR)
                if (tl != null && br != null) {
                    tmpRectF.set(tl.x, tl.y, br.x, br.y)
                    val paint = if (index == highlightedIndex) highlightPaint else overlayPaint
                    canvas.drawRect(tmpRectF, paint)
                    canvas.drawRect(tmpRectF, borderPaint)
                }
            }
        }
    }
}
