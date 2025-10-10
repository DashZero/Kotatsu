package org.koitharu.kotatsu.panelview.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
        color = Color.rgb(255, 213, 79) // Amber
        strokeWidth = resources.displayMetrics.density * 2f
    }

    private val dimPaint = Paint().apply {
        color = Color.BLACK
        alpha = 128
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private var imageView: SubsamplingScaleImageView? = null
    private var panels: List<Rect> = emptyList()
    private var highlightedIndex = -1

    private val tmpRectF = RectF()
    private val tmpPointTL = PointF()
    private val tmpPointBR = PointF()

    var overlayOpacity: Float
        get() = dimPaint.alpha / 255f
        set(value) {
            val alpha = (value.coerceIn(0f, 1f) * 255).toInt()
            dimPaint.alpha = alpha
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
        if (!ssiv.isReady || panels.isEmpty() || highlightedIndex == -1) {
            return
        }

        val highlightedRect = panels[highlightedIndex]
        if (highlightedRect.isEmpty) {
            return
        }

        val tl = ssiv.sourceToViewCoord(highlightedRect.left.toFloat(), highlightedRect.top.toFloat(), tmpPointTL)
        val br = ssiv.sourceToViewCoord(highlightedRect.right.toFloat(), highlightedRect.bottom.toFloat(), tmpPointBR)

        if (tl != null && br != null) {
            tmpRectF.set(tl.x, tl.y, br.x, br.y)

            // Draw a semi-transparent overlay over the entire view
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

            // Cut a hole in the overlay for the highlighted panel
            canvas.drawRect(tmpRectF, clearPaint)

            // Draw a border around the highlighted panel
            canvas.drawRect(tmpRectF, borderPaint)
        }
    }
}
