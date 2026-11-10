package com.trtc.uikit.livekit.pusher.summary

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.TextViewCompat
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicxcore.api.live.PusherListener.PusherSummary
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private val timeLabel: AppCompatTextView
    private val pauseLabel: AppCompatTextView
    private val interruptionLabel: AppCompatTextView
    private val metrics: List<SummaryMetricView>
    private var didRequestExit = false
    private var summaryContext: PusherSummaryContext? = null
    private var summary: PusherSummary? = null

    /** Raised when the host leaves the summary stage. */
    var onExit: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_summary_view, this, true)
        setBackgroundResource(R.color.livekit_pusher_summary_background)
        timeLabel = findViewById(R.id.livekit_pusher_summary_time_range)
        pauseLabel = findViewById(R.id.livekit_pusher_summary_pause_duration)
        interruptionLabel = findViewById(R.id.livekit_pusher_summary_reason)
        metrics = listOf(
            findViewById(R.id.livekit_pusher_summary_gift_value),
            findViewById(R.id.livekit_pusher_summary_gifts),
            findViewById(R.id.livekit_pusher_summary_gift_senders),
            findViewById(R.id.livekit_pusher_summary_viewers),
            findViewById(R.id.livekit_pusher_summary_comments),
            findViewById(R.id.livekit_pusher_summary_likes),
        )
        metrics[0].configure(
            R.string.livekit_summaryview_stat_gift_value, R.string.livekit_summaryview_unit_coins,
            SummaryMetricView.Style.INCOME
        )
        metrics[1].configure(
            R.string.livekit_summaryview_stat_gift, R.string.livekit_summaryview_unit_gift, SummaryMetricView.Style.GIFT
        )
        metrics[2].configure(
            R.string.livekit_summaryview_stat_gift_sender, R.string.livekit_summaryview_unit_people,
            SummaryMetricView.Style.GIFT
        )
        metrics[3].configure(R.string.livekit_summaryview_stat_viewer)
        metrics[4].configure(R.string.livekit_summaryview_stat_comments)
        metrics[5].configure(R.string.livekit_summaryview_stat_like)
        findViewById<TextView>(R.id.livekit_pusher_summary_title).applySummaryFont(24f, true)
        findViewById<TextView>(R.id.livekit_pusher_summary_section_title).applySummaryFont(15f, true)
        findViewById<View>(R.id.livekit_pusher_summary_back).setOnClickListener { exitSummary() }
        val body = findViewById<View>(R.id.livekit_pusher_summary_body)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val safeArea =
                insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            body.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
            insets
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    internal fun updateSummary(summary: PusherSummary?) {
        this.summary = summary
        renderSummary()
    }

    internal fun show(context: PusherSummaryContext) {
        summaryContext = context
        didRequestExit = false
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
        val presentation = summaryContext ?: return
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val start = presentation.startTimeMillis?.let { formatter.format(Date(it)) } ?: "--:--"
        val end = formatter.format(Date(presentation.endTimeMillis))
        val duration = summary?.let { durationText(it.liveDuration) } ?: "--"
        val total = context.getString(R.string.livekit_summaryview_total_duration, duration)
        timeLabel.text = context.getString(R.string.livekit_summaryview_time_range, start, end, total)
        pauseLabel.isVisible = presentation.pausedSeconds > 0
        pauseLabel.text = if (pauseLabel.isVisible) {
            context.getString(R.string.livekit_summaryview_pause_details, durationText(presentation.pausedSeconds))
        } else null
        interruptionLabel.text = presentation.interruptionMessage
        interruptionLabel.isVisible = !presentation.interruptionMessage.isNullOrEmpty()
        val values = summary?.let {
            longArrayOf(
                it.totalGiftCoins.toLong(), it.totalGiftsSent.toLong(), it.totalUniqueGiftSenders.toLong(),
                it.totalViewers.toLong(), it.totalMessageCount.toLong(), it.totalLikesReceived.toLong()
            )
        }
        metrics.forEachIndexed { index, metric -> metric.bind(values?.get(index)) }
    }

    private fun durationText(duration: Int): String {
        val seconds = duration.coerceAtLeast(0)
        val parts = listOf(
            seconds / 3600 to R.string.livekit_summaryview_duration_hours,
            seconds / 60 % 60 to R.string.livekit_summaryview_duration_minutes,
            seconds % 60 to R.string.livekit_summaryview_duration_seconds,
        )
        return parts.filter { it.first > 0 || (seconds == 0 && it.second == R.string.livekit_summaryview_duration_seconds) }
            .joinToString("") { context.getString(it.second, it.first) }
    }
}

// XML-instantiated counterpart of the template's private SummaryMetricView; no business state or SDK calls.
internal class SummaryMetricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    enum class Style(val valueSize: Float, val valueHeight: Int) {
        INCOME(48f, 62), GIFT(32f, 40), INTERACTION(26f, 38)
    }

    private val titleLabel: AppCompatTextView
    private val valueLabel: AppCompatTextView
    private var displayOptions = emptyList<Pair<String, Float>>()
    private var boundValue: Long? = null
    private var hasBoundValue = false
    private var valueSize = 26f
    private val numberFormatter = NumberFormat.getIntegerInstance()
    private val scientificFormatter = DecimalFormat("0.##E0", DecimalFormatSymbols.getInstance())

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_summary_stat_card, this, true)
        titleLabel = findViewById(R.id.livekit_pusher_summary_stat_title)
        valueLabel = findViewById(R.id.livekit_pusher_summary_stat_value)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        titleLabel.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        valueLabel.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun configure(title: Int, unit: Int? = null, style: Style = Style.INTERACTION) {
        val titleText = context.getString(title)
        titleLabel.text = unit?.let {
            context.getString(R.string.livekit_summaryview_metric_with_unit, titleText, context.getString(it))
        } ?: titleText
        val income = style == Style.INCOME
        titleLabel.applySummaryFont(if (income) 14f else 13f)
        valueSize = style.valueSize
        valueLabel.applySummaryFont(valueSize, true)
        valueLabel.fontFeatureSettings = "tnum"
        valueLabel.setTextColor(
            ContextCompat.getColor(
                context,
                if (income) R.color.livekit_pusher_summary_income else R.color.livekit_pusher_summary_text_primary
            )
        )
        removeAllViews()
        if (income) {
            addView(titleLabel)
            addView(valueLabel)
        } else {
            addView(valueLabel)
            addView(titleLabel)
        }
        titleLabel.updateLayoutParams<LayoutParams> {
            height = dp(if (income) 20 else 24)
            topMargin = if (income) 0 else dp(8)
        }
        valueLabel.updateLayoutParams<LayoutParams> {
            height = dp(style.valueHeight)
            topMargin = if (income) dp(12) else 0
        }
        titleLabel.setSummaryAutoSize(if (income) 14f else 13f, 0.75f)
        valueLabel.setSummaryAutoSize(valueSize, 0.5f)
        hasBoundValue = false
        bind(null)
    }

    fun bind(value: Long?) {
        val normalized = value?.coerceAtLeast(0)
        if (hasBoundValue && boundValue == normalized) return
        hasBoundValue = true
        boundValue = normalized
        val fullText = normalized?.let(numberFormatter::format) ?: "--"
        val texts = mutableListOf(fullText)
        if (normalized != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                texts += android.icu.text.CompactDecimalFormat.getInstance(
                    Locale.getDefault(), android.icu.text.CompactDecimalFormat.CompactStyle.SHORT
                ).apply {
                    maximumSignificantDigits = 3
                }.format(normalized)
            }
            texts += scientificFormatter.format(normalized)
        }
        val basePaint = android.text.TextPaint(valueLabel.paint).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, valueSize, resources.displayMetrics)
        }
        displayOptions = texts.distinct().map { it to basePaint.measureText(it) * 0.5f }
        contentDescription = "${titleLabel.text}, $fullText"
        updateValueText()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateValueText()
    }

    private fun updateValueText() {
        val available = width - paddingLeft - paddingRight
        val option =
            if (available > 0) displayOptions.firstOrNull { it.second <= available } ?: displayOptions.lastOrNull()
            else displayOptions.firstOrNull()
        if (valueLabel.text != option?.first) valueLabel.text = option?.first
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

private fun TextView.applySummaryFont(size: Float, semibold: Boolean = false) {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.DEFAULT, if (semibold) 600 else 400, false)
    } else Typeface.create(if (semibold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
}

private fun TextView.setSummaryAutoSize(size: Float, minimumScale: Float) {
    val sizes = mutableListOf(size * minimumScale)
    var nextSize = sizes.first() + 1f
    while (nextSize < size) {
        sizes += nextSize
        nextSize += 1f
    }
    sizes += size
    val pixels = sizes.map {
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, it, resources.displayMetrics).toInt().coerceAtLeast(1)
    }.distinct().toIntArray()
    TextViewCompat.setAutoSizeTextTypeUniformWithPresetSizes(this, pixels, TypedValue.COMPLEX_UNIT_PX)
}
