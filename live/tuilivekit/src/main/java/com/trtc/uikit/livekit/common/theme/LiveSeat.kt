package com.trtc.uikit.livekit.common.theme

import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveText
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.common.theme.imageloader.ImageLoader
import kotlin.math.min

/** Shared seat visuals. Entry subclasses own SDK projection and role decisions. */
abstract class LiveSeat protected constructor(context: Context, emptyTitle: String) : FrameLayout(context) {

    companion object {
        /**
         * 音浪显示阈值：当前策略为 volume 达到该值即显示，后续调整只改这里。
         */
        const val VOLUME_WAVE_THRESHOLD: Int = 0
    }

    private var microphoneOn = false
    private val noVideoView = LiveSeatNoVideoView(context)
    private val overlayView = LiveSeatOverlayView(context)
    private val emptyView = LiveSeatEmptyView(context, emptyTitle)

    init {
        isClickable = false
        isFocusable = false
        clipChildren = true
        listOf(noVideoView, overlayView, emptyView).forEach {
            addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    fun updateVolume(volume: Int) {
        noVideoView.updateVolume(if (microphoneOn) volume.coerceIn(0, 100) else 0)
    }

    protected fun updateContent(name: String, avatarURL: String, microphoneOn: Boolean) {
        this.microphoneOn = microphoneOn
        if (!microphoneOn) noVideoView.updateVolume(0)
        noVideoView.update(avatarURL)
        overlayView.update(name, microphoneOn)
    }

    protected fun updateVisibility(isEmpty: Boolean, showsNoVideo: Boolean, showsOverlay: Boolean) {
        emptyView.visibility = if (isEmpty) VISIBLE else INVISIBLE
        noVideoView.visibility = if (!isEmpty && showsNoVideo) VISIBLE else INVISIBLE
        overlayView.visibility = if (!isEmpty && showsOverlay) VISIBLE else INVISIBLE
    }
}

private class LiveSeatEmptyView(context: Context, title: String) : FrameLayout(context) {

    private val contentView = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
    }
    private val plusView = AppCompatTextView(context).apply {
        text = "+"
        gravity = Gravity.CENTER
        setTextColor(Color.argb(117, 255, 255, 255))
        includeFontPadding = false
    }
    private val invitationView = LiveText(context).apply {
        role = LiveTheme.TextRole.METADATA
        tone = LiveTheme.TextTone.TERTIARY
        text = title
        gravity = Gravity.CENTER
        includeFontPadding = false
    }

    init {
        isClickable = false
        isFocusable = false
        setBackgroundColor(Color.TRANSPARENT)
        val inset = dp(1f).toInt()
        addView(
            contentView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                setMargins(inset, inset, inset, inset)
            },
        )
        contentView.addView(
            plusView,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(32f).toInt()),
        )
        contentView.addView(
            invitationView,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(14f).toInt()).apply {
                topMargin = dp(3f).toInt()
            },
        )
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val contentWidth = (width - dp(2f)).coerceAtLeast(0f)
        val contentHeight = (height - dp(2f)).coerceAtLeast(0f)
        val compact = min(contentWidth, contentHeight) <= dp(110f)
        contentView.background = ColorDrawable(
            Color.argb(if (compact) 14 else 9, 255, 255, 255),
        )
        plusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 29f else 26f)
        plusView.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)

        val plusHeight = dp(if (compact) 32f else 30f).toInt()
        val spacing = dp(3f).toInt()
        val invitationHeight = dp(14f).toInt()
        val showsInvitation = contentHeight >= plusHeight + spacing + invitationHeight
        invitationView.visibility = if (showsInvitation) VISIBLE else GONE
        plusView.layoutParams = (plusView.layoutParams as LinearLayout.LayoutParams).apply {
            this.height = plusHeight
        }
        invitationView.layoutParams = (invitationView.layoutParams as LinearLayout.LayoutParams).apply {
            this.height = invitationHeight
            topMargin = spacing
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

private class LiveSeatNoVideoView(
    context: Context,
) : FrameLayout(context) {

    private val avatarView = AppCompatImageView(context)
    private val firstWaveRing = LiveSeatWaveRingView(context)
    private val secondWaveRing = LiveSeatWaveRingView(context)
    private var avatarURL: String? = null
    private var volume: Int = 0
    private var isWaveActive = false
    private val waveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = WAVE_DURATION_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = PathInterpolator(0.16f, 0.5f, 0.4f, 1f)
        addUpdateListener { animation ->
            val progress = animation.animatedValue as Float
            firstWaveRing.applyWaveProgress(progress)
            secondWaveRing.applyWaveProgress((progress + 0.5f) % 1f)
        }
    }

    init {
        clipChildren = true
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                ContextCompat.getColor(context, R.color.livekit_pusher_coguest_no_video_start),
                ContextCompat.getColor(context, R.color.livekit_pusher_coguest_no_video_end)
            ),
        )
        addView(firstWaveRing)
        addView(secondWaveRing)
        addView(avatarView)
    }

    fun update(avatarURL: String) {
        val url = avatarURL.trim()
        if (this.avatarURL == url) return
        this.avatarURL = url
        ImageLoader.loadAvatar(avatarView, url, R.drawable.livekit_livelist_default_avatar)
    }

    fun updateVolume(volume: Int) {
        if (this.volume == volume) return
        this.volume = volume
        applyWaveState()
    }

    private val shouldShowWave: Boolean
        get() = volume > LiveSeat.VOLUME_WAVE_THRESHOLD && isVisible

    private fun applyWaveState() {
        val shouldShow = shouldShowWave && isAttachedToWindow
        if (shouldShow == isWaveActive) return
        isWaveActive = shouldShow
        if (shouldShow) {
            waveAnimator.start()
        } else {
            waveAnimator.cancel()
            firstWaveRing.reset()
            secondWaveRing.reset()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyWaveState()
    }

    override fun onDetachedFromWindow() {
        waveAnimator.cancel()
        isWaveActive = false
        firstWaveRing.reset()
        secondWaveRing.reset()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        applyWaveState()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val size = avatarSize(measuredWidth, measuredHeight)
        val exactSize = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        avatarView.measure(exactSize, exactSize)
        val ringSize = size + dp(WAVE_RING_OFFSET_DP * 2).toInt()
        val ringSpec = MeasureSpec.makeMeasureSpec(ringSize, MeasureSpec.EXACTLY)
        firstWaveRing.measure(ringSpec, ringSpec)
        secondWaveRing.measure(ringSpec, ringSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val size = avatarSize(width, height)
        val avatarLeft = (width - size) / 2
        val avatarTop = (height - size) / 2
        avatarView.layout(avatarLeft, avatarTop, avatarLeft + size, avatarTop + size)
        val ringInset = -dp(WAVE_RING_OFFSET_DP).toInt()
        listOf(firstWaveRing, secondWaveRing).forEach { ring ->
            ring.layout(
                avatarLeft + ringInset,
                avatarTop + ringInset,
                avatarLeft + size - ringInset,
                avatarTop + size - ringInset,
            )
        }
    }

    private fun avatarSize(containerWidth: Int, containerHeight: Int): Int {
        val density = resources.displayMetrics.density
        val width = containerWidth / density
        val height = containerHeight / density
        // Shared logical-unit policy: leave room for the full wave and bottom nickname.
        val targetDiameter = (min(width, height) * 0.44f).coerceIn(42f, 67f)
        val waveSpace = min(width - 16f, height - 32f).coerceAtLeast(0f)
        val fittingDiameter = (waveSpace / 1.38f - 6f).coerceAtLeast(0f)
        return dp(min(targetDiameter, fittingDiameter)).toInt()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        /**
         * 音浪：双层白色圆环以 1.4s 周期、半周期错峰向外扩散，对应设计 `co-mic-effects.css`
         * 的 `.comic-slot-portrait::before/::after` 与 `comic-speaking-wave`。
         */
        private const val WAVE_DURATION_MS = 1400L
        private const val WAVE_RING_OFFSET_DP = 2f
    }
}

/**
 * 音浪圆环：头像外圈 [WAVE_RING_OFFSET_DP] 处的单圈描边，按进度同时改变透明度与缩放。
 *
 * 描边画在边界内侧，圆环向外扩散时不会被自身边界裁切。
 */
private class LiveSeatWaveRingView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = WAVE_RING_COLOR
        strokeWidth = WAVE_RING_WIDTH_DP * resources.displayMetrics.density
    }
    private var availableSize = 0

    init {
        // Zero volume may never start the animator, so hide both rings from creation.
        reset()
    }

    fun applyWaveProgress(progress: Float) {
        alpha = opacityEvaluator.evaluate(progress, 0f, 1f)
        scaleX = 1f + (WAVE_MAX_SCALE - 1f) * progress
        scaleY = scaleX
    }

    fun reset() {
        alpha = 0f
        scaleX = 1f
        scaleY = 1f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        availableSize = min(measuredWidth, measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = availableSize
        if (size <= 0) return
        val radius = size / 2f - paint.strokeWidth / 2f
        if (radius <= 0) return
        canvas.drawCircle(size / 2f, size / 2f, radius, paint)
    }

    companion object {
        private const val WAVE_RING_COLOR = 0xE6FFFFFF.toInt()
        private const val WAVE_RING_WIDTH_DP = 2f
        private const val WAVE_MAX_SCALE = 1.38f

        private val opacityEvaluator = TypeEvaluator<Float> { fraction, _, _ ->
            when {
                fraction <= 0f -> 0f
                fraction < 0.14f -> 0.78f / 0.14f * fraction
                fraction < 0.7f -> 0.78f + (0.22f - 0.78f) / (0.7f - 0.14f) * (fraction - 0.14f)
                fraction < 1f -> 0.22f / (1f - 0.7f) * (1f - fraction)
                else -> 0f
            }
        }
    }
}

private class LiveSeatOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private val microphoneImage: AppCompatImageView
    private val nameView: AppCompatTextView

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_seat_overlay_view, this, true)
        microphoneImage = findViewById(R.id.livekit_seat_microphone_icon)
        nameView = findViewById(R.id.livekit_seat_name)
        isClickable = false
        isFocusable = false
    }

    fun update(name: String, microphoneOn: Boolean) {
        microphoneImage.setImageResource(
            if (microphoneOn) {
                R.drawable.livekit_coguest_seat_mic
            } else {
                R.drawable.livekit_coguest_seat_muted
            }
        )
        nameView.text = name
    }
}
