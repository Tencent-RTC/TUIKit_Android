package com.trtc.uikit.livekit.pusher.summary

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicxcore.api.live.LiveSummaryData
import io.trtc.tuikit.atomicxcore.api.live.PusherListener.PusherSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.RectF
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.trtc.uikit.livekit.common.theme.label.AtomicLabel
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import androidx.annotation.StringRes

internal data class PusherSummaryContext(
    val startTimeMillis: Long?,
    val endTimeMillis: Long,
    val pausedSeconds: Int,
    val interruptionMessage: String? = null,
)

class PusherSummaryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heroView: SummaryHeroCardView
    private val statCards: List<SummaryStatCardView>
    private var didRequestExit = false
    private var summaryContext: PusherSummaryContext? = null
    private var summary: PusherSummary? = null

    /** Raised when the host leaves the summary stage. */
    var onExit: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_summary_view, this, true)
        heroView = findViewById(R.id.livekit_pusher_summary_hero)
        statCards = listOf(
            findViewById(R.id.livekit_pusher_summary_gifts),
            findViewById(R.id.livekit_pusher_summary_gift_value),
            findViewById(R.id.livekit_pusher_summary_viewers),
            findViewById(R.id.livekit_pusher_summary_comments),
            findViewById(R.id.livekit_pusher_summary_gift_senders),
            findViewById(R.id.livekit_pusher_summary_likes),
        )
        configureCards()
        findViewById<FrameLayout>(R.id.livekit_pusher_summary_back).setOnClickListener {
            exitSummary()
        }
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(
                ContextCompat.getColor(context, R.color.livekit_pusher_summary_background_top),
                ContextCompat.getColor(context, R.color.livekit_pusher_summary_background_middle),
                ContextCompat.getColor(context, R.color.livekit_pusher_summary_background_bottom),
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            0f,
            ContextCompat.getColor(context, R.color.livekit_pusher_summary_glow_clear),
            ContextCompat.getColor(context, R.color.livekit_pusher_summary_glow_horizontal),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(
                ContextCompat.getColor(context, R.color.livekit_pusher_summary_glow_vertical),
                ContextCompat.getColor(context, R.color.livekit_pusher_summary_glow_clear),
                ContextCompat.getColor(context, R.color.livekit_pusher_summary_glow_clear),
            ),
            floatArrayOf(0f, 0.46f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        drawGlowDot(canvas, 54f, 142f, R.color.livekit_pusher_summary_dot_one)
        drawGlowDot(canvas, 327f, 310f, R.color.livekit_pusher_summary_dot_two)
        drawGlowDot(canvas, 281f, 678f, R.color.livekit_pusher_summary_dot_three)
        super.onDraw(canvas)
    }

    internal fun updateSummary(summary: PusherSummary?) {
        this.summary = summary
        renderSummary()
    }

    internal fun show(context: PusherSummaryContext) {
        summaryContext = context
        didRequestExit = false
        findViewById<AtomicLabel>(R.id.livekit_pusher_summary_reason).text = context.interruptionMessage
        renderSummary()
    }

    internal fun hide() {
        summaryContext = null
    }

    override fun onDetachedFromWindow() {
        hide()
        super.onDetachedFromWindow()
    }

    private fun exitSummary() {
        if (summaryContext == null || !isShown || didRequestExit) return
        didRequestExit = true
        onExit?.invoke()
    }

    private fun renderSummary() {
        val context = summaryContext ?: return
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val start = context.startTimeMillis?.let { formatter.format(Date(it)) } ?: "--:--"
        val end = formatter.format(Date(context.endTimeMillis))
        val summaryData = LiveSummaryData().apply {
            summary?.let {
                totalGiftsSent = it.totalGiftsSent.coerceAtLeast(0)
                totalGiftCoins = it.totalGiftCoins.coerceAtLeast(0)
                totalViewers = it.totalViewers.coerceAtLeast(0)
                totalMessageSent = it.totalMessageCount.coerceAtLeast(0)
                totalGiftUniqueSenders = it.totalUniqueGiftSenders.coerceAtLeast(0)
                totalLikesReceived = it.totalLikesReceived.coerceAtLeast(0)
            }
        }
        val displayData = summaryData
        heroView.update(
            this.context.getString(R.string.livekit_summaryview_time_range, start, end),
            summary?.let { formatDuration(it.liveDuration.coerceAtLeast(0)) } ?: "--",
            context.pausedSeconds.takeIf { it > 0 }?.let(::formatDuration),
        )
        val values = longArrayOf(
            displayData.totalGiftsSent.toLong().coerceAtLeast(0),
            displayData.totalGiftCoins.toLong().coerceAtLeast(0),
            displayData.totalViewers.toLong().coerceAtLeast(0),
            displayData.totalMessageSent.toLong().coerceAtLeast(0),
            displayData.totalGiftUniqueSenders.toLong().coerceAtLeast(0),
            displayData.totalLikesReceived.toLong().coerceAtLeast(0),
        )
        statCards.forEachIndexed { index, card -> card.update(values[index].takeIf { summary != null }) }
    }

    private fun configureCards() {
        val titles = intArrayOf(
            R.string.livekit_summaryview_stat_gift,
            R.string.livekit_summaryview_stat_gift_value,
            R.string.livekit_summaryview_stat_viewer,
            R.string.livekit_summaryview_stat_comments,
            R.string.livekit_summaryview_stat_gift_sender,
            R.string.livekit_summaryview_stat_like,
        )
        val units = intArrayOf(
            R.string.livekit_summaryview_unit_gift,
            R.string.livekit_summaryview_unit_coins,
            R.string.livekit_summaryview_unit_people,
            R.string.livekit_summaryview_unit_messages,
            R.string.livekit_summaryview_unit_people,
            R.string.livekit_summaryview_unit_times,
        )
        val accents = intArrayOf(
            R.color.livekit_pusher_summary_accent_yellow,
            R.color.livekit_pusher_summary_accent_pink,
            R.color.livekit_pusher_summary_accent_blue,
            R.color.livekit_pusher_summary_accent_purple,
            R.color.livekit_pusher_summary_accent_green,
            R.color.livekit_pusher_summary_accent_orange,
        )
        statCards.forEachIndexed { index, card ->
            card.configure(titles[index], units[index], ContextCompat.getColor(context, accents[index]))
        }
    }

    private fun drawGlowDot(canvas: Canvas, xDp: Float, yDp: Float, colorRes: Int) {
        val density = resources.displayMetrics.density
        backgroundPaint.shader = RadialGradient(
            xDp * density,
            yDp * density,
            density,
            ContextCompat.getColor(context, colorRes),
            0,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(xDp * density, yDp * density, density, backgroundPaint)
    }

    private fun formatDuration(seconds: Int): String = String.format(
        Locale.US,
        "%02d:%02d:%02d",
        seconds / 3600,
        seconds % 3600 / 60,
        seconds % 60,
    )

}
internal class SummaryCardPainter(
    private val radius: Float,
    private val borderColor: Int,
    borderWidth: Float,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
        color = borderColor
    }
    private val bounds = RectF()

    fun drawHero(canvas: Canvas, width: Int, height: Int, whiteTop: Int, whiteBottom: Int, blueTop: Int, blueMiddle: Int) {
        updateBounds(width, height)
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), whiteTop, whiteBottom, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(blueTop, blueMiddle, 0),
            floatArrayOf(0f, 0.38f, 0.75f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(bounds, radius, radius, paint)
        canvas.drawRoundRect(bounds, radius, radius, borderPaint)
    }

    fun drawStat(canvas: Canvas, width: Int, height: Int, whiteStart: Int, whiteEnd: Int, accentEnd: Int) {
        updateBounds(width, height)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, whiteStart, whiteEnd, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            0f,
            intArrayOf(0, 0, accentEnd),
            floatArrayOf(0f, 0.28f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(bounds, radius, radius, paint)
        canvas.drawRoundRect(bounds, radius, radius, borderPaint)
    }

    private fun updateBounds(width: Int, height: Int) {
        val inset = borderPaint.strokeWidth / 2f
        bounds.set(inset, inset, width - inset, height - inset)
    }
}

internal class SummaryHeroCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val painter = SummaryCardPainter(
        resources.displayMetrics.density * 22f,
        ContextCompat.getColor(context, R.color.livekit_pusher_summary_card_border),
        resources.displayMetrics.density,
    )
    private val timeRangeView: AtomicLabel
    private val durationView: AtomicLabel
    private val pauseView: View
    private val pauseDurationView: AtomicLabel

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_summary_hero, this, true)
        timeRangeView = findViewById(R.id.livekit_pusher_summary_time_range)
        durationView = findViewById(R.id.livekit_pusher_summary_duration)
        pauseView = findViewById(R.id.livekit_pusher_summary_pause)
        pauseDurationView = findViewById(R.id.livekit_pusher_summary_pause_duration)
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        painter.drawHero(
            canvas,
            width,
            height,
            ContextCompat.getColor(context, R.color.livekit_pusher_summary_hero_white_top),
            ContextCompat.getColor(context, R.color.livekit_pusher_summary_hero_white_bottom),
            ContextCompat.getColor(context, R.color.livekit_pusher_summary_hero_blue_top),
            ContextCompat.getColor(context, R.color.livekit_pusher_summary_hero_blue_middle),
        )
        super.onDraw(canvas)
    }

    fun update(timeRange: String, duration: String, pausedDuration: String?) {
        timeRangeView.text = timeRange
        durationView.text = duration
        pauseView.visibility = if (pausedDuration == null) GONE else VISIBLE
        pauseDurationView.text = pausedDuration.orEmpty()
    }
}

internal class SummaryStatCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val painter = SummaryCardPainter(
        resources.displayMetrics.density * 16f,
        ContextCompat.getColor(context, R.color.livekit_pusher_summary_stat_border),
        resources.displayMetrics.density,
    )
    private val accentView: View
    private val titleView: AtomicLabel
    private val valueView: AtomicLabel
    private val unitView: AtomicLabel
    private var accentColor = Color.TRANSPARENT

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_summary_stat_card, this, true)
        accentView = findViewById(R.id.livekit_pusher_summary_stat_accent)
        titleView = findViewById(R.id.livekit_pusher_summary_stat_title)
        valueView = findViewById(R.id.livekit_pusher_summary_stat_value)
        unitView = findViewById(R.id.livekit_pusher_summary_stat_unit)
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        painter.drawStat(
            canvas,
            width,
            height,
            ContextCompat.getColor(context, R.color.livekit_pusher_summary_stat_white_start),
            ContextCompat.getColor(context, R.color.livekit_pusher_summary_stat_white_end),
            Color.argb(22, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
        )
        super.onDraw(canvas)
    }

    fun configure(@StringRes title: Int, @StringRes unit: Int, @ColorInt accent: Int) {
        titleView.setText(title)
        unitView.setText(unit)
        accentColor = accent
        accentView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = resources.displayMetrics.density * 2f
            setColor(accent)
        }
        invalidate()
    }

    fun update(value: Long?) {
        valueView.text = value?.coerceAtLeast(0L)?.toString() ?: "--"
    }
}
