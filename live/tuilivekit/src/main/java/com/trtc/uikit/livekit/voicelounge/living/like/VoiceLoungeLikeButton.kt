package com.trtc.uikit.livekit.voicelounge.living.like

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import com.trtc.uikit.livekit.voicelounge.dp

internal class VoiceLoungeLikeButton(context: Context, private val interactive: Boolean) : FrameLayout(context) {
    var onLike: (() -> Unit)? = null

    private val pressWrapper = FrameLayout(context)
    private val shadowView = ShadowCircleView(context)
    private val discView = GlassDiscView(context)
    private val iconView = ThumbIconView(context)

    init {
        isClickable = interactive
        isFocusable = interactive
        clipChildren = false
        clipToPadding = false
        pressWrapper.clipChildren = false
        pressWrapper.clipToPadding = false

        val sizePx = dp(SIZE_DP).toInt()
        val iconSizePx = dp(ICON_SIZE_DP).toInt()
        val shadowPx = sizePx + dp(ShadowCircleView.BLEED_DP * 2).toInt()

        addView(pressWrapper, LayoutParams(sizePx, sizePx))
        pressWrapper.addView(shadowView, LayoutParams(shadowPx, shadowPx, android.view.Gravity.CENTER))
        pressWrapper.addView(discView, LayoutParams(sizePx, sizePx))
        discView.addView(
            iconView,
            LayoutParams(iconSizePx, iconSizePx, android.view.Gravity.CENTER),
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> animateScale(0.88f, 120L)
            MotionEvent.ACTION_UP -> {
                animateScale(1f, 180L)
                performClick()
            }

            MotionEvent.ACTION_CANCEL -> animateScale(1f, 180L)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        pulse()
        onLike?.invoke()
        return true
    }

    fun pulse() {
        val anim = AnimatorSet()
        val sx = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 1.34f, 0.92f, 1f)
        val sy = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 1.34f, 0.92f, 1f)
        sx.duration = 450L
        sy.duration = 450L
        sx.interpolator = PathInterpolator(0.2f, 0.8f, 0.3f, 1f)
        sy.interpolator = PathInterpolator(0.2f, 0.8f, 0.3f, 1f)
        anim.playTogether(sx, sy)
        anim.start()
    }

    fun heartCenter(root: View): FloatArray {
        val buttonLocation = IntArray(2)
        discView.getLocationInWindow(buttonLocation)
        val rootLocation = IntArray(2)
        root.getLocationInWindow(rootLocation)
        val cx = (buttonLocation[0] - rootLocation[0]) + discView.width / 2f
        val cy = (buttonLocation[1] - rootLocation[1]) + discView.height / 2f
        return floatArrayOf(cx, cy)
    }

    private fun animateScale(target: Float, duration: Long) {
        pressWrapper.animate().cancel()
        pressWrapper.animate().scaleX(target).scaleY(target).setDuration(duration).start()
    }

    companion object {
        const val SIZE_DP = 52f
        private const val ICON_SIZE_DP = 24f

        val gradientColors: IntArray = intArrayOf(
            Color.rgb(217, 126, 217),
            Color.rgb(174, 122, 239),
            Color.rgb(130, 152, 239),
        )
    }
}

private class ShadowCircleView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = VoiceLoungeLikeButton.gradientColors[1]
        val shadowColor = 0x8C000000.toInt() or (VoiceLoungeLikeButton.gradientColors[1] and 0x00FFFFFF)
        setShadowLayer(dpF(SHADOW_RADIUS_DP), 0f, dpF(5f), shadowColor)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val radius = dpF(VoiceLoungeLikeButton.SIZE_DP) / 2f
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)
    }

    companion object {
        private const val SHADOW_RADIUS_DP = 24f
        const val BLEED_DP = 48f
    }
}

private class GlassDiscView(context: Context) : FrameLayout(context) {
    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(242, 255, 255, 255)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(128, 255, 255, 255)
    }
    private val clipPath = Path()

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val wf = w.toFloat()
        val hf = h.toFloat()
        liquidPaint.shader = LinearGradient(
            0.15f * wf, 0.1f * hf, 0.85f * wf, 0.95f * hf,
            VoiceLoungeLikeButton.gradientColors,
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        edgePaint.shader = RadialGradient(
            0.5f * wf, 0.5f * hf, 0.75f * wf,
            intArrayOf(Color.TRANSPARENT, Color.argb(71, 0, 0, 0)),
            floatArrayOf(0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        glossPaint.shader = RadialGradient(
            0.32f * wf, 0.22f * hf, 0.45f * wf,
            intArrayOf(Color.argb(217, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        borderPaint.strokeWidth = dpF(0.8f)
        clipPath.reset()
        clipPath.addCircle(wf / 2f, hf / 2f, wf / 2f, Path.Direction.CW)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawPaint(liquidPaint)
        canvas.drawPaint(edgePaint)
        canvas.drawPaint(glossPaint)
        val w = width.toFloat()
        val h = height.toFloat()
        val ellipseW = w * 0.2f
        val ellipseH = h * 0.12f
        val ellipseX = w * 0.22f
        val ellipseY = h * 0.17f
        canvas.drawOval(ellipseX, ellipseY, ellipseX + ellipseW, ellipseY + ellipseH, specularPaint)
        canvas.restoreToCount(saved)
        super.dispatchDraw(canvas)
        canvas.drawCircle(w / 2f, h / 2f, w / 2f - borderPaint.strokeWidth / 2f, borderPaint)
    }
}

private class ThumbIconView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val path = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildPath(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    private fun buildPath(w: Float, h: Float) {
        path.reset()
        val sx = w / 24f
        val sy = h / 24f
        fun x(v: Float) = v * sx
        fun y(v: Float) = v * sy

        val r = 1.2f * sx
        path.addRoundRect(x(2.5f), y(9f), x(7f), y(21f), r, r, Path.Direction.CW)

        val hand = Path()
        hand.moveTo(x(9f), y(19.4f))
        hand.lineTo(x(9f), y(9.9f))
        hand.cubicTo(x(9f), y(9.4f), x(9.2f), y(8.92f), x(9.56f), y(8.57f))
        hand.lineTo(x(14.66f), y(3.55f))
        hand.cubicTo(x(15.26f), y(2.95f), x(16.26f), y(3.13f), x(16.64f), y(3.89f))
        hand.cubicTo(x(16.84f), y(4.29f), x(16.88f), y(4.75f), x(16.76f), y(5.19f))
        hand.lineTo(x(15.9f), y(9f))
        hand.lineTo(x(20.2f), y(9f))
        hand.cubicTo(x(21.52f), y(9f), x(22.48f), y(10.25f), x(22.14f), y(11.52f))
        hand.lineTo(x(20.28f), y(18.42f))
        hand.quadTo(x(19.85f), y(20f), x(18.16f), y(20f))
        hand.lineTo(x(10.6f), y(20f))
        hand.cubicTo(x(9.72f), y(20f), x(9f), y(19.73f), x(9f), y(19.4f))
        hand.close()
        path.op(hand, Path.Op.UNION)
    }
}

private fun View.dpF(value: Float): Float = value * resources.displayMetrics.density
