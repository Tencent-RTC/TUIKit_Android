package com.trtc.uikit.livekit.pusher.living.cohost

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.bumptech.glide.Glide
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveToast
import com.trtc.uikit.livekit.common.theme.imageloader.ImageLoader
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.random.Random

internal class PusherCoHostRandomConnectionView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val radar: CoHostRadarView
    private var enabledPresentation = false
    private var lifecycle: Lifecycle? = null
    private val lifecycleObserver = LifecycleEventObserver { _, _ -> refreshAnimation() }

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_cohost_random_view, this, true)
        radar = findViewById(R.id.livekit_cohost_radar)
    }

    fun bind(hostAvatarURL: String, candidates: List<PusherCoHostStore.User>) {
        radar.bind(hostAvatarURL, candidates.distinctBy { it.userID }.take(5).map { it.avatarURL })
    }

    fun setPresentationEnabled(enabled: Boolean) {
        enabledPresentation = enabled
        refreshAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lifecycle = findViewTreeLifecycleOwner()?.lifecycle
        lifecycle?.addObserver(lifecycleObserver)
        findViewById<View>(R.id.livekit_cohost_random_button).setOnClickListener {
            LiveToast.show(context, context.getString(R.string.livekit_cohost_random_developing))
        }
        refreshAnimation()
    }

    override fun onDetachedFromWindow() {
        lifecycle?.removeObserver(lifecycleObserver)
        lifecycle = null
        radar.setAnimating(false)
        radar.cancelImages()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) refreshAnimation()
    }

    private fun refreshAnimation() {
        val foreground = lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) ?: true
        radar.setAnimating(
            enabledPresentation && isAttachedToWindow && isShown && windowVisibility == VISIBLE && foreground
        )
    }
}

internal class CoHostRadarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    private val sweep = CoHostSweepView(context)
    private val host = AppCompatImageView(context)
    private val peers = List(5) { AppCompatImageView(context) }
    private val sizes = listOf(18, 16, 15, 16, 17)
    private val centers = listOf(51f to 17f, 129f to 45f, 56f to 72f, 104f to 12f, 105f to 74f)
    private var positions = centers
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.argb(150, 74, 158, 255)
    }
    private var scan: ValueAnimator? = null
    private var pulse: ValueAnimator? = null
    private var pulseTime = 0L
    private val peerColorIndexes = IntArray(5) { -1 }
    private val peerColors =
        listOf(.55f to .8f, 1f to 1.2f, 1f to 1.08f, .65f to .85f).map { (saturation, brightness) ->
            val matrix = android.graphics.ColorMatrix().apply { setSaturation(saturation) }
            matrix.postConcat(
                android.graphics.ColorMatrix(
                    floatArrayOf(
                        brightness, 0f, 0f, 0f, 0f, 0f, brightness, 0f, 0f, 0f, 0f, 0f, brightness, 0f, 0f, 0f, 0f, 0f,
                        1f, 0f
                    )
                )
            )
            android.graphics.ColorMatrixColorFilter(matrix)
        }
    private var urls: List<String> = emptyList()
    private var loadedURLs: List<String>? = null

    init {
        setWillNotDraw(false)
        clipChildren = false
        sweep.setImageResource(R.drawable.livekit_cohost_radar_sweep)
        addView(sweep, LayoutParams(dp(112f).toInt(), dp(112f).toInt()))
        peers.forEach { addView(it) }
        addView(host)
        peers.forEach { it.alpha = 0f }
    }

    fun bind(hostURL: String, peerURLs: List<String>) {
        val next = listOf(hostURL) + List(5) { peerURLs.getOrElse(it) { "" } }
        if (next == urls) return
        urls = next
        if (isAttachedToWindow) loadImages()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loadImages()
    }

    private fun loadImages() {
        if (loadedURLs == urls) return
        loadedURLs = urls
        (listOf(host) + peers).forEachIndexed { i, view ->
            ImageLoader.loadAvatar(view, urls.getOrElse(i) { "" }.trim(), R.drawable.livekit_livelist_default_avatar)
        }
    }

    fun cancelImages() {
        (listOf(host) + peers).forEach { Glide.with(context).clear(it) }
        loadedURLs = null
    }

    fun setAnimating(enabled: Boolean) {
        val reduced = Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()
        if (!enabled || reduced) {
            scan?.cancel()
            pulse?.cancel()
            scan = null
            pulse = null
            peers.forEach { it.alpha = if (enabled) .8f else 0f; it.clearColorFilter() }
            peerColorIndexes.fill(-1)
            pulseTime = 0L
            invalidate()
            return
        }
        if (scan != null) return
        loadImages()
        positions = randomPositions()
        requestLayout()
        scan = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4800
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                sweep.rotation = progress * 360
                peers.forEachIndexed { i, peer ->
                    val (x, y) = positions[i]
                    val startPhase = (((atan2(y - 42, x - 89) + PI / 2) / (2 * PI)).toFloat() + 1) % 1
                    val time = animation.currentPlayTime / 4800f - startPhase
                    val phase = ((time % 1) + 1) % 1
                    peer.alpha = if (time < 0) 0f else sample(phase, listOf(0f, 1f, .92f, .92f, 0f, 0f))
                    val scale = sample(phase, listOf(.72f, 1.08f, 1f, 1f, .96f, .96f))
                    peer.scaleX = scale
                    peer.scaleY = scale
                    val colorIndex = when {
                        phase < .04f -> 0; phase < .08f -> 1; phase < .42f -> 2; else -> 3
                    }
                    if (peerColorIndexes[i] != colorIndex) {
                        peerColorIndexes[i] = colorIndex; peer.colorFilter = peerColors[colorIndex]
                    }
                }
            }
            start()
        }
        pulse = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2800
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { pulseTime = it.currentPlayTime; invalidate() }
            start()
        }
    }

    private val discoveryCurve = android.view.animation.PathInterpolator(0f, 0f, .58f, 1f)
    private fun sample(phase: Float, values: List<Float>): Float {
        val times = listOf(0f, .04f, .08f, .22f, .42f, 1f)
        for (i in 0 until times.lastIndex) {
            if (phase <= times[i + 1]) {
                val t =
                    discoveryCurve.getInterpolation(((phase - times[i]) / (times[i + 1] - times[i])).coerceIn(0f, 1f))
                return values[i] + (values[i + 1] - values[i]) * t
            }
        }
        return values.last()
    }

    private fun randomPositions(): List<Pair<Float, Float>> {
        repeat(8) {
            val points = mutableListOf<Pair<Float, Float>>()
            for (index in sizes.indices) {
                val radius = sizes[index] * 1.08f / 2
                var chosen: Pair<Float, Float>? = null
                for (attempt in 0 until 160) {
                    val x = 33 + radius + 6 + Random.nextFloat() * (112 - 2 * radius - 10)
                    val y = radius + 3 + Random.nextFloat() * (84 - 2 * radius - 6)
                    val distance = kotlin.math.hypot(x - 89, y - 42)
                    if (distance < 17 + radius + 5 || distance > 54) continue
                    if (points.withIndex().any { (j, point) ->
                            kotlin.math.hypot(
                                x - point.first, y - point.second
                            ) < radius + sizes[j] * 1.08f / 2 + 6
                        }) continue
                    chosen = x to y
                    break
                }
                if (chosen == null) break
                points += chosen
            }
            if (points.size == sizes.size) return points
        }
        return centers
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        fun place(view: View, x: Float, y: Float, size: Float) {
            view.layout(dp(x).toInt(), dp(y).toInt(), dp(x + size).toInt(), dp(y + size).toInt())
        }
        place(sweep, 33f, -14f, 112f)
        peers.forEachIndexed { i, view ->
            val (x, y) = positions[i]
            val size = sizes[i].toFloat()
            place(view, x - size / 2, y - size / 2, size)
        }
        place(host, 72f, 25f, 34f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pulse == null) return
        for (i in 0..1) {
            val time = pulseTime / 2800f - i * .5f
            if (time < 0) continue
            val phase = time % 1
            val t = discoveryCurve.getInterpolation((phase / .76f).coerceIn(0f, 1f))
            paint.color = androidx.core.graphics.ColorUtils.blendARGB(
                Color.argb(209, 255, 255, 255), Color.argb(20, 126, 232, 255), t
            )
            paint.alpha = (Color.alpha(paint.color) * .68f * (1 - t)).toInt()
            canvas.drawCircle(dp(89f), dp(42f), dp(16.5f * (.95f + .95f * t)), paint)
        }
    }

    override fun onDetachedFromWindow() {
        setAnimating(false)
        cancelImages()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}

private class CoHostSweepView(context: Context) : AppCompatImageView(context) {
    private val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
    }

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val layer = canvas.saveLayer(-12 * density, -18 * density, 124 * density, 124 * density, null)
        drawable?.setBounds(
            (-12 * density).toInt(), (-18 * density).toInt(), (124 * density).toInt(), (124 * density).toInt()
        )
        drawable?.draw(canvas)
        canvas.drawCircle(56 * density, 56 * density, 20.5f * density, hole)
        canvas.restoreToCount(layer)
    }
}
