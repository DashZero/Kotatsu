package org.koitharu.kotatsu.reader.ui.pager.panel

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

class PanelMaskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        alpha = 150 // ~60% opacity by default
        style = Paint.Style.FILL
    }

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
    }

    private val punchOutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private var ssiv: SubsamplingScaleImageView? = null
    private var panelRectSrc: Rect? = null
    private val panelRectView = RectF()

    init {
        // Enable hardware layer so CLEAR mode erases the interior correctly.
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun attach(target: SubsamplingScaleImageView) {
        ssiv = target
        invalidate()
    }

    fun setMaskOpacity(fraction: Float) {
        val alpha = (fraction.coerceIn(0f, 1f) * 255f).toInt()
        if (overlayPaint.alpha != alpha) {
            overlayPaint.alpha = alpha
            invalidate()
        }
    }

    fun setPanelRect(rect: Rect?) {
        panelRectSrc = rect
        visibility = if (rect == null) GONE else VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = panelRectSrc ?: return
        val view = ssiv ?: return
        if (!view.isReady) return

        val lt = view.sourceToViewCoord(PointF(r.left.toFloat(), r.top.toFloat())) ?: return
        val rb = view.sourceToViewCoord(PointF(r.right.toFloat(), r.bottom.toFloat())) ?: return

        val left = lt.x.coerceAtMost(rb.x).coerceAtLeast(0f)
        val right = rb.x.coerceAtLeast(lt.x).coerceAtMost(width.toFloat())
        val top = lt.y.coerceAtMost(rb.y).coerceAtLeast(0f)
        val bottom = rb.y.coerceAtLeast(lt.y).coerceAtMost(height.toFloat())

        if (right <= left || bottom <= top) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
            return
        }

        panelRectView.set(left, top, right, bottom)

        val checkpoint = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        canvas.drawRect(panelRectView, punchOutPaint)
        canvas.restoreToCount(checkpoint)

        canvas.drawRect(panelRectView, framePaint)
    }
}