package org.koitharu.kotatsu.reader.ui.pager.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

class PanelMaskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        alpha = 150 // ~60% opacity
        style = Paint.Style.FILL
    }

    private var ssiv: SubsamplingScaleImageView? = null
    private var panelRectSrc: Rect? = null

    fun attach(target: SubsamplingScaleImageView) {
        ssiv = target
        invalidate()
    }

    fun setPanelRect(rect: Rect?) {
        panelRectSrc = rect
        visibility = if (rect == null) GONE else VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = panelRectSrc ?: return
        val v = ssiv ?: return
        if (!v.isReady) return

        // Map source rect to view coordinates via two corner points
        val lt = v.sourceToViewCoord(PointF(r.left.toFloat(), r.top.toFloat())) ?: return
        val rb = v.sourceToViewCoord(PointF(r.right.toFloat(), r.bottom.toFloat())) ?: return

        val left = lt.x.coerceAtLeast(0f)
        val top = lt.y.coerceAtLeast(0f)
        val right = rb.x.coerceAtMost(width.toFloat())
        val bottom = rb.y.coerceAtMost(height.toFloat())

        // Draw four rectangles around the panel area to darken background
        // Top
        canvas.drawRect(0f, 0f, width.toFloat(), top, paint)
        // Bottom
        canvas.drawRect(0f, bottom, width.toFloat(), height.toFloat(), paint)
        // Left
        canvas.drawRect(0f, top, left, bottom, paint)
        // Right
        canvas.drawRect(right, top, width.toFloat(), bottom, paint)
    }
}
