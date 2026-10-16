package com.trtc.uikit.livekit.common.gift.panel.gift.viewholder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.trtc.uikit.livekit.R

class ComboButtonView(context: Context) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private val trackSizePx = (62f * density).toInt()

    private val progressView: ComboProgressRingView
    private val faceView: ComboFaceView
    private val countView: TextView
    private val rippleContainer: FrameLayout

    private val handler = Handler(Looper.getMainLooper())
    private var holdDelayRunnable: Runnable? = null
    private var holdRepeatRunnable: Runnable? = null
    private var countdownRunnable: Runnable? = null
    private var pointerId = -1

    var onSend: (() -> Unit)? = null
    var onExpire: (() -> Unit)? = null

    init {
        clipChildren = false
        clipToPadding = false
        LayoutInflater.from(context).inflate(R.layout.livekit_gift_layout_combo_button, this, true)

        rippleContainer = findViewById(R.id.fl_ripple_container)
        progressView = findViewById(R.id.v_combo_progress)
        faceView = findViewById(R.id.v_combo_face)
        countView = findViewById(R.id.tv_combo_count)

        setOnTouchListener { _, event -> handleTouch(event) }
    }

    private var comboCount = 0

    fun startCombo() {
        comboCount = 1
        countView.text = comboCount.toString()
        countView.visibility = VISIBLE
        faceView.playEnterAnimation()
        emitRipple()
        restartCountdown()
    }

    fun bumpCombo() {
        comboCount += 1
        countView.text = comboCount.toString()
        countView.animate().cancel()
        countView.scaleX = 1f
        countView.scaleY = 1f
        val animator = ValueAnimator.ofFloat(1f, 1.18f, 1f).apply {
            duration = 180
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                countView.scaleX = v
                countView.scaleY = v
            }
        }
        animator.start()
        emitRipple()
        restartCountdown()
    }

    fun stopCombo() {
        cancelHoldTimers()
        cancelCountdown()
        pointerId = -1
        faceView.setPressing(false)
        faceView.resetVisualState()
        comboCount = 0
        countView.visibility = INVISIBLE
        progressView.stopCountdown()
        rippleContainer.removeAllViews()
    }

    private fun emitRipple() {
        val ripple = ComboRippleView(context)
        val lp = LayoutParams(trackSizePx, trackSizePx).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        rippleContainer.addView(ripple, lp)
        ripple.start {
            rippleContainer.removeView(ripple)
        }
    }

    private fun restartCountdown() {
        cancelCountdown()
        progressView.startCountdown(COMBO_DURATION_MS)
        countdownRunnable = Runnable { onExpire?.invoke() }
        handler.postDelayed(countdownRunnable!!, COMBO_DURATION_MS)
    }

    private fun cancelCountdown() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
    }

    private fun cancelHoldTimers() {
        holdDelayRunnable?.let { handler.removeCallbacks(it) }
        holdRepeatRunnable?.let { handler.removeCallbacks(it) }
        holdDelayRunnable = null
        holdRepeatRunnable = null
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                faceView.setPressing(true)
                onSend?.invoke()
                scheduleHoldRepeat()
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_OUTSIDE -> {
                cancelHoldTimers()
                faceView.setPressing(false)
                pointerId = -1
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return false
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun scheduleHoldRepeat() {
        holdDelayRunnable = Runnable {
            holdRepeatRunnable = object : Runnable {
                override fun run() {
                    onSend?.invoke()
                    handler.postDelayed(this, HOLD_REPEAT_INTERVAL_MS)
                }
            }
            handler.postDelayed(holdRepeatRunnable!!, HOLD_REPEAT_INTERVAL_MS)
        }
        handler.postDelayed(holdDelayRunnable!!, HOLD_REPEAT_DELAY_MS)
    }

    override fun onDetachedFromWindow() {
        cancelHoldTimers()
        cancelCountdown()
        progressView.stopCountdown()
        countView.animate().cancel()
        faceView.resetVisualState()
        for (i in rippleContainer.childCount - 1 downTo 0) {
            val child = rippleContainer.getChildAt(i)
            if (child is ComboRippleView) {
                child.detachAnimator()
            }
        }
        rippleContainer.removeAllViews()
        super.onDetachedFromWindow()
    }

    companion object {
        const val COMBO_DURATION_MS = 2000L
        const val HOLD_REPEAT_DELAY_MS = 300L
        const val HOLD_REPEAT_INTERVAL_MS = 300L
    }
}

internal class ComboTrackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density
    private val borderWidth = 3f * density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#050812")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
        color = Color.argb((255 * 0.9f).toInt(), 5, 6, 12)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f
        canvas.drawCircle(cx, cy, r - borderWidth / 2f, fillPaint)
        canvas.drawCircle(cx, cy, r - borderWidth / 2f, borderPaint)
    }
}

internal class ComboProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val brand2 = "#4A9EFF".toColorInt()
    private val density = context.resources.displayMetrics.density
    private val strokeWidth = 3f * density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@ComboProgressRingView.strokeWidth
        color = Color.argb((255 * 0.28f).toInt(), Color.red(brand2), Color.green(brand2), Color.blue(brand2))
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@ComboProgressRingView.strokeWidth
        strokeCap = Paint.Cap.BUTT
        color = brand2
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@ComboProgressRingView.strokeWidth + 2f * density
        strokeCap = Paint.Cap.BUTT
        color = Color.argb((255 * 0.5f).toInt(), Color.red(brand2), Color.green(brand2), Color.blue(brand2))
        maskFilter = BlurMaskFilter(4f * density, BlurMaskFilter.Blur.NORMAL)
    }
    private val rectF = RectF()
    private var progress = 1f
    private var animator: ValueAnimator? = null
    private val dashAngle = 8f
    private val gapAngle = 4f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val inset = (2f * density) + strokeWidth / 2f
        rectF.set(inset, inset, width - inset, height - inset)
        val r = (minOf(width, height) - 2 * inset) / 2f
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, r, bgPaint)
        if (progress <= 0f) return
        val sweepTotal = 360f * progress
        var drawn = 0f
        val step = dashAngle + gapAngle
        var start = -90f
        while (drawn < sweepTotal) {
            val current = minOf(dashAngle, sweepTotal - drawn)
            canvas.drawArc(rectF, start, -current, false, glowPaint)
            canvas.drawArc(rectF, start, -current, false, paint)
            start -= step
            drawn += step
        }
    }

    fun startCountdown(duration: Long) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(1f, 0f).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
        }
        animator?.start()
    }

    fun stopCountdown() {
        animator?.cancel()
        progress = 1f
        invalidate()
    }
}

@SuppressLint("SetTextI18n")
internal class ComboFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val brand2 = "#4A9EFF".toColorInt()
    private val brand1 = "#0052D9".toColorInt()
    private val density = context.resources.displayMetrics.density
    private var shaderSizeW = 0
    private var shaderSizeH = 0
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = brand2
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }

    private var enterAnimator: AnimatorSet? = null
    private var pressAnimator: AnimatorSet? = null
    private var pressScale = 1f

    init {
        setWillNotDraw(false)
        val label = TextView(context).apply {
            text = context.getString(R.string.common_gift_combo_long_press) +
                    "\n" +
                    context.getString(R.string.common_gift_combo)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 0.9f)
            setShadowLayer(2f * density, 0f, 1f * density, Color.argb((255 * 0.48f).toInt(), 0, 0, 0))
        }
        addView(
            label,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER)
        )
        elevation = 6f * density
    }

    private fun ensureShaders() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        if (w == shaderSizeW && h == shaderSizeH && bgPaint.shader != null && highlightPaint.shader != null) return
        shaderSizeW = w
        shaderSizeH = h
        bgPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(brand2, brand2, brand1),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        highlightPaint.shader = RadialGradient(
            w / 2f, h * 0.15f, w * 0.55f,
            Color.argb((255 * 0.28f).toInt(), 255, 255, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        ensureShaders()
        val r = minOf(width, height) / 2f
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, r, bgPaint)
        canvas.drawCircle(cx, cy, r, highlightPaint)
    }

    fun setPressing(pressing: Boolean) {
        pressAnimator?.cancel()
        val from = pressScale
        val target = if (pressing) 0.95f else 1f
        val anim = ValueAnimator.ofFloat(from, target).apply {
            duration = 120
            addUpdateListener {
                val v = it.animatedValue as Float
                pressScale = v
                applyCombinedScale()
            }
        }
        pressAnimator = AnimatorSet().apply {
            play(anim)
            start()
        }
    }

    fun playEnterAnimation() {
        enterAnimator?.cancel()
        pressAnimator?.cancel()
        pressScale = 1f
        alpha = 0f
        val baseScale = 0.82f
        scaleX = baseScale
        scaleY = baseScale
        val alphaAnim = ObjectAnimator.ofFloat(this, "alpha", 0f, 1f)
        val scaleAnim = ValueAnimator.ofFloat(baseScale, 1f).apply {
            addUpdateListener {
                val v = it.animatedValue as Float
                scaleX = v * pressScale
                scaleY = v * pressScale
            }
        }
        enterAnimator = AnimatorSet().apply {
            duration = 230
            playTogether(alphaAnim, scaleAnim)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    alpha = 1f
                    applyCombinedScale()
                }
            })
            start()
        }
    }

    fun resetVisualState() {
        enterAnimator?.cancel()
        pressAnimator?.cancel()
        enterAnimator = null
        pressAnimator = null
        pressScale = 1f
        alpha = 1f
        scaleX = 1f
        scaleY = 1f
    }

    private fun applyCombinedScale() {
        if (enterAnimator?.isRunning == true) return
        scaleX = pressScale
        scaleY = pressScale
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        enterAnimator?.cancel()
        pressAnimator?.cancel()
        enterAnimator = null
        pressAnimator = null
    }
}

internal class ComboRippleView(context: Context) : View(context) {

    private val brand2 = "#4A9EFF".toColorInt()
    private val density = context.resources.displayMetrics.density
    private val strokeWidthPx = 1f * density
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var currentAlpha = 0f
    private var currentScale = 0.96f
    private var animator: ValueAnimator? = null

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - strokeWidthPx
        strokePaint.color = Color.argb(
            (255 * 0.48f * currentAlpha).toInt().coerceIn(0, 255),
            Color.red(brand2), Color.green(brand2), Color.blue(brand2)
        )
        fillPaint.color = Color.argb(
            (255 * 0.10f * currentAlpha).toInt().coerceIn(0, 255),
            Color.red(brand2), Color.green(brand2), Color.blue(brand2)
        )
        canvas.save()
        canvas.scale(currentScale, currentScale, cx, cy)
        canvas.drawCircle(cx, cy, r, fillPaint)
        canvas.drawCircle(cx, cy, r, strokePaint)
        canvas.restore()
    }

    fun start(onEnd: () -> Unit) {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            interpolator = PathInterpolator(0.16f, 0.58f, 0.3f, 1f)
            addUpdateListener {
                val t = it.animatedValue as Float
                currentScale = 0.96f + (1.52f - 0.96f) * t
                currentAlpha = when {
                    t < 0.16f -> t / 0.16f * 0.36f
                    t < 0.42f -> 0.36f + (0.2f - 0.36f) * ((t - 0.16f) / (0.42f - 0.16f))
                    else -> 0.2f * (1f - (t - 0.42f) / (1f - 0.42f))
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            })
        }
        animator?.start()
    }

    fun detachAnimator() {
        animator?.removeAllListeners()
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        animator = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.removeAllListeners()
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        animator = null
    }
}