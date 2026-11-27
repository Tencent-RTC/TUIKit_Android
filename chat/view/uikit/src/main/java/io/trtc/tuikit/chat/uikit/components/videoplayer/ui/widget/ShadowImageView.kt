package io.trtc.tuikit.chat.uikit.components.videoplayer.ui.widget
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView that draws its drawable with a soft drop shadow so that white
 * icons stay legible on bright video frames. The shadow is rendered with
 * Paint.setShadowLayer on a software-accelerated layer; we cache an off-screen
 * bitmap of the icon and composite it onto the canvas with the shadow paint.
 */
class ShadowImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        setShadowLayer(DEFAULT_RADIUS_DP * density, 0f, DEFAULT_DY_DP * density, DEFAULT_COLOR)
    }
    private var iconBitmap: Bitmap? = null
    private var iconCanvas: Canvas? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setShadow(radiusDp: Float, dyDp: Float, color: Int) {
        shadowPaint.setShadowLayer(radiusDp * density, 0f, dyDp * density, color)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        iconBitmap?.recycle()
        iconBitmap = if (w > 0 && h > 0) {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } else {
            null
        }
        iconCanvas = iconBitmap?.let { Canvas(it) }
    }

    override fun onDraw(canvas: Canvas) {
        val source = drawable
        val bitmap = iconBitmap
        val tempCanvas = iconCanvas
        if (source == null || bitmap == null || tempCanvas == null || width <= 0 || height <= 0) {
            super.onDraw(canvas)
            return
        }
        bitmap.eraseColor(Color.TRANSPARENT)
        val saveCount = tempCanvas.save()
        val left = paddingLeft
        val top = paddingTop
        val right = width - paddingRight
        val bottom = height - paddingBottom
        val intrinsicW = source.intrinsicWidth
        val intrinsicH = source.intrinsicHeight
        if (intrinsicW > 0 && intrinsicH > 0) {
            val available = (right - left).coerceAtLeast(0)
            val availableH = (bottom - top).coerceAtLeast(0)
            val scale = minOf(
                available.toFloat() / intrinsicW,
                availableH.toFloat() / intrinsicH
            )
            val drawW = (intrinsicW * scale).toInt()
            val drawH = (intrinsicH * scale).toInt()
            val offsetX = left + (available - drawW) / 2
            val offsetY = top + (availableH - drawH) / 2
            source.setBounds(offsetX, offsetY, offsetX + drawW, offsetY + drawH)
        } else {
            source.setBounds(left, top, right, bottom)
        }
        source.draw(tempCanvas)
        tempCanvas.restoreToCount(saveCount)
        canvas.drawBitmap(bitmap, 0f, 0f, shadowPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        iconBitmap?.recycle()
        iconBitmap = null
        iconCanvas = null
    }

    companion object {
        private const val DEFAULT_RADIUS_DP = 5f
        private const val DEFAULT_DY_DP = 1f
        private val DEFAULT_COLOR = Color.argb(140, 0, 0, 0)
    }
}
