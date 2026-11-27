package io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.widget.FrameLayout
import kotlin.math.min

internal class BubbleMenuLayout(context: Context) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int): Float = value * density

    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val cornerRadius = dp(7)
    private val arrowWidth = dp(12)
    private val arrowHeightPx = dp(6)
    private val shadowBlur = dp(8)
    private val shadowOffsetY = dp(2)
    private val shadowColor = 0x33000000
    private val shadowPadH = dp(8).toInt()
    private val shadowPadTop = dp(6).toInt()
    private val shadowPadBottom = dp(10).toInt()
    private val bubbleRect = RectF()

    private var bubbleColor: Int = 0
    private var isArrowOnTop: Boolean = true
    private var arrowCenterXInBubble: Float = 0f

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setBubbleStyle(
        bubbleColor: Int,
        isArrowOnTop: Boolean,
        arrowCenterX: Float
    ) {
        this.bubbleColor = bubbleColor
        this.isArrowOnTop = isArrowOnTop
        this.arrowCenterXInBubble = arrowCenterX + shadowPadH
        val topPadding = shadowPadTop + if (isArrowOnTop) arrowHeightPx.toInt() else 0
        val bottomPadding = shadowPadBottom + if (isArrowOnTop) 0 else arrowHeightPx.toInt()
        setPadding(shadowPadH, topPadding, shadowPadH, bottomPadding)
        invalidate()
    }

    fun getShadowPadH(): Int = shadowPadH

    fun getShadowPadTop(): Int = shadowPadTop

    fun getShadowPadBottom(): Int = shadowPadBottom

    fun getArrowHeight(): Int = arrowHeightPx.toInt()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = shadowPadH.toFloat()
        val top = shadowPadTop.toFloat() + if (isArrowOnTop) arrowHeightPx else 0f
        val right = width - shadowPadH.toFloat()
        val bubbleBottom = height - shadowPadBottom.toFloat() - if (isArrowOnTop) 0f else arrowHeightPx
        bubbleRect.set(left, top, right, bubbleBottom)
        val combinedPath = createBubbleWithArrowPath()
        bubblePaint.color = bubbleColor
        bubblePaint.setShadowLayer(shadowBlur, 0f, shadowOffsetY, shadowColor)
        canvas.drawPath(combinedPath, bubblePaint)
        bubblePaint.clearShadowLayer()
        canvas.drawPath(combinedPath, bubblePaint)
    }

    private fun createBubbleWithArrowPath(): Path {
        val availableWidth = bubbleRect.width().coerceAtLeast(0f)
        val availableHeight = bubbleRect.height().coerceAtLeast(0f)
        val safeCornerRadius = min(cornerRadius, min(availableWidth, availableHeight) / 2f)
        val safeArrowHalfWidth = min(arrowWidth / 2f, (availableWidth / 2f - safeCornerRadius).coerceAtLeast(0f))
        val centerMin = bubbleRect.left + safeCornerRadius + safeArrowHalfWidth
        val centerMax = bubbleRect.right - safeCornerRadius - safeArrowHalfWidth
        val centerX = if (centerMin <= centerMax) {
            arrowCenterXInBubble.coerceIn(centerMin, centerMax)
        } else {
            bubbleRect.centerX()
        }
        val tipY = if (isArrowOnTop) {
            bubbleRect.top - arrowHeightPx
        } else {
            bubbleRect.bottom + arrowHeightPx
        }
        val l = bubbleRect.left
        val t = bubbleRect.top
        val r = bubbleRect.right
        val b = bubbleRect.bottom
        val cr = safeCornerRadius
        return Path().apply {
            if (isArrowOnTop) {
                moveTo(l + cr, t)
                lineTo(centerX - safeArrowHalfWidth, t)
                lineTo(centerX, tipY)
                lineTo(centerX + safeArrowHalfWidth, t)
                lineTo(r - cr, t)
                arcTo(r - 2 * cr, t, r, t + 2 * cr, 270f, 90f, false)
                lineTo(r, b - cr)
                arcTo(r - 2 * cr, b - 2 * cr, r, b, 0f, 90f, false)
                lineTo(l + cr, b)
                arcTo(l, b - 2 * cr, l + 2 * cr, b, 90f, 90f, false)
                lineTo(l, t + cr)
                arcTo(l, t, l + 2 * cr, t + 2 * cr, 180f, 90f, false)
            } else {
                moveTo(l + cr, t)
                lineTo(r - cr, t)
                arcTo(r - 2 * cr, t, r, t + 2 * cr, 270f, 90f, false)
                lineTo(r, b - cr)
                arcTo(r - 2 * cr, b - 2 * cr, r, b, 0f, 90f, false)
                lineTo(centerX + safeArrowHalfWidth, b)
                lineTo(centerX, tipY)
                lineTo(centerX - safeArrowHalfWidth, b)
                lineTo(l + cr, b)
                arcTo(l, b - 2 * cr, l + 2 * cr, b, 90f, 90f, false)
                lineTo(l, t + cr)
                arcTo(l, t, l + 2 * cr, t + 2 * cr, 180f, 90f, false)
            }
            close()
        }
    }
}
