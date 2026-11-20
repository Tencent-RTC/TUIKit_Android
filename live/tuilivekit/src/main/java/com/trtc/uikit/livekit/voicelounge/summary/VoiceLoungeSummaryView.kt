package com.trtc.uikit.livekit.voicelounge.summary

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.rgb
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.setPressAlpha
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha
import com.trtc.uikit.livekit.voicelounge.withAlpha
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class VoiceLoungeSummaryData(
    val startTimeMillis: Long? = null,
    val endTimeMillis: Long = System.currentTimeMillis(),
    val giftCount: Int = 0,
    val newFollowers: Int = 0,
    val viewers: Int = 0,
    val fanViewers: Int = 0,
    val gifters: Int = 0,
    val likes: Int = 0,
)

internal class VoiceLoungeSummaryView(context: Context) : FrameLayout(context) {
    var onExit: (() -> Unit)? = null

    private val scrollView = ScrollView(context)
    private val contentColumn = LinearLayout(context)
    private val header = FrameLayout(context)
    private val timeRangeLabel =
        voiceLoungeText(context, LiveTheme.TextRole.CONTROL, LiveTheme.TextTone.TERTIARY, Gravity.CENTER)
    private val durationLabel = TextView(context)
    private val statCards = listOf(
        StatCard(
            context, R.string.livekit_voiceroom_summary_gift, R.string.livekit_voiceroom_summary_unit_items, GIFT_ACCENT
        ),
        StatCard(
            context, R.string.livekit_voiceroom_summary_followers, R.string.livekit_voiceroom_summary_unit_people,
            FOLLOWERS_ACCENT,
        ),
        StatCard(
            context, R.string.livekit_voiceroom_summary_viewers, R.string.livekit_voiceroom_summary_unit_people,
            VIEWERS_ACCENT,
        ),
        StatCard(
            context, R.string.livekit_voiceroom_summary_fan_viewers, R.string.livekit_voiceroom_summary_unit_people,
            FAN_ACCENT,
        ),
        StatCard(
            context, R.string.livekit_voiceroom_summary_gifters, R.string.livekit_voiceroom_summary_unit_people,
            GIFTERS_ACCENT,
        ),
        StatCard(
            context, R.string.livekit_voiceroom_summary_likes, R.string.livekit_voiceroom_summary_unit_times,
            LIKES_ACCENT
        ),
    )

    init {
        setBackgroundColor(rgb(0.06f, 0.07f, 0.10f))
        isClickable = true
        val backgroundView = ImageView(context).apply {
            setImageResource(R.drawable.livekit_summary_bg)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        addView(backgroundView, frameParams(MATCH, MATCH))

        scrollView.isVerticalScrollBarEnabled = false
        scrollView.overScrollMode = OVER_SCROLL_NEVER
        addView(scrollView, frameParams(MATCH, MATCH))
        contentColumn.orientation = LinearLayout.VERTICAL
        scrollView.addView(contentColumn, FrameLayout.LayoutParams(MATCH, WRAP))

        setupHeader()
        contentColumn.addView(header, linearParams(MATCH, dp(44), top = dp(8)))
        contentColumn.addView(makeHeroCard(), linearParams(MATCH, dp(210), start = dp(16), top = dp(10), end = dp(16)))
        val sectionTitle = voiceLoungeText(
            context, LiveTheme.TextRole.PROMINENT, LiveTheme.TextTone.PRIMARY,
            text = context.getString(R.string.livekit_voiceroom_summary_section),
        )
        contentColumn.addView(sectionTitle, linearParams(WRAP, WRAP, start = dp(19), top = dp(20)))
        contentColumn.addView(makeStatGrid(), linearParams(MATCH, dp(252), start = dp(16), top = dp(10), end = dp(16)))
        val note = voiceLoungeText(
            context, LiveTheme.TextRole.METADATA, LiveTheme.TextTone.TERTIARY, Gravity.CENTER,
            text = context.getString(R.string.livekit_voiceroom_summary_note),
        )
        contentColumn.addView(
            note, linearParams(MATCH, WRAP, start = dp(16), top = dp(16), end = dp(16), bottom = dp(24))
        )

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val top =
                insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            contentColumn.setPadding(0, top, 0, bottom)
            insets
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    fun bind(input: VoiceLoungeSummaryData) {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val start = input.startTimeMillis?.let { formatter.format(Date(it)) } ?: "--:--"
        val end = formatter.format(Date(input.endTimeMillis))
        timeRangeLabel.text = "$start ~ $end"
        val duration = input.startTimeMillis?.let { maxOf(0L, (input.endTimeMillis - it) / 1000).toInt() } ?: 0
        durationLabel.text = formatDuration(duration)
        scrollView.scrollTo(0, 0)

        val values =
            listOf(input.giftCount, input.newFollowers, input.viewers, input.fanViewers, input.gifters, input.likes)
        statCards.zip(values).forEach { (card, value) -> card.bind(value) }
    }

    private fun setupHeader() {
        val back = ImageView(context).apply {
            setImageResource(R.drawable.livekit_summary_back)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPressAlpha()
            setOnClickListener { onExit?.invoke() }
        }
        header.addView(back, frameParams(dp(34), dp(34), Gravity.CENTER_VERTICAL or Gravity.START, start = dp(12)))
        val title = voiceLoungeText(
            context, LiveTheme.TextRole.SHEET_TITLE, LiveTheme.TextTone.PRIMARY, Gravity.CENTER,
            text = context.getString(R.string.livekit_voiceroom_summary_title),
        )
        header.addView(title, frameParams(WRAP, WRAP, Gravity.CENTER, start = dp(46), end = dp(46)))
    }

    private fun makeHeroCard(): View {
        val card = FrameLayout(context)
        card.background = cardBackground(
            radius = dp(22).toFloat(),
            base = intArrayOf(whiteAlpha(0.065f), whiteAlpha(0.025f)),
            accent = intArrayOf(VoiceLoungeColor.brandBlue.withAlpha(0.13f), VoiceLoungeColor.brandBlue.withAlpha(0f)),
            orientation = GradientDrawable.Orientation.TOP_BOTTOM,
            border = whiteAlpha(0.085f),
        )

        val ring = FrameLayout(context).apply {
            background = roundedDrawable(VoiceLoungeColor.brandBlue.withAlpha(0.1f), dp(26).toFloat())
        }
        val mark = FrameLayout(context).apply {
            background =
                roundedDrawable(rgb(0.098f, 0.243f, 0.42f), dp(21).toFloat(), dp(1), VoiceLoungeColor.brandBlue)
        }
        val check = ImageView(context).apply {
            setImageResource(R.drawable.livekit_summary_done)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        mark.addView(check, frameParams(dp(20), dp(16), Gravity.CENTER))
        ring.addView(mark, frameParams(dp(42), dp(42), Gravity.CENTER))

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        column.addView(ring, linearParams(dp(52), dp(52), top = dp(16)))
        val ended = TextView(context).apply {
            text = context.getString(R.string.livekit_voiceroom_summary_ended)
            setTextColor(whiteAlpha(0.95f))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        column.addView(ended, linearParams(MATCH, WRAP, start = dp(30), top = dp(12), end = dp(30)))
        column.addView(timeRangeLabel, linearParams(MATCH, WRAP, start = dp(30), top = dp(6), end = dp(30)))
        val durationTitle = TextView(context).apply {
            text = context.getString(R.string.livekit_voiceroom_summary_duration)
            setTextColor(whiteAlpha(0.48f))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        column.addView(durationTitle, linearParams(MATCH, WRAP, start = dp(30), top = dp(14), end = dp(30)))
        durationLabel.setTextColor(VoiceLoungeColor.statHighlight)
        durationLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        durationLabel.typeface = Typeface.DEFAULT_BOLD
        durationLabel.gravity = Gravity.CENTER
        durationLabel.includeFontPadding = false
        column.addView(durationLabel, linearParams(MATCH, WRAP, start = dp(30), top = dp(4), end = dp(30)))
        card.addView(column, frameParams(MATCH, WRAP))
        return card
    }

    private fun makeStatGrid(): View {
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        statCards.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { index, card ->
                row.addView(card, linearParams(0, MATCH, weight = 1f, start = if (index == 0) 0 else dp(10)))
            }
            grid.addView(row, linearParams(MATCH, 0, weight = 1f, top = if (rowIndex == 0) 0 else dp(10)))
        }
        return grid
    }

    private class StatCard(
        context: Context,
        @StringRes titleRes: Int,
        @StringRes unitRes: Int,
        @ColorInt accent: Int,
    ) : FrameLayout(context) {
        private val valueLabel = TextView(context)

        init {
            background = cardBackground(
                radius = dp(16).toFloat(),
                base = intArrayOf(whiteAlpha(0.055f), whiteAlpha(0.022f)),
                accent = intArrayOf(accent.withAlpha(0f), accent.withAlpha(0.085f)),
                orientation = GradientDrawable.Orientation.LEFT_RIGHT,
                border = whiteAlpha(0.065f),
            )
            val accentBar = View(context).apply { background = roundedDrawable(accent, dp(2).toFloat()) }
            addView(accentBar, frameParams(dp(16), dp(4), Gravity.TOP or Gravity.START, start = dp(13), top = dp(14)))

            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val titleLabel = voiceLoungeText(
                context, LiveTheme.TextRole.METADATA, LiveTheme.TextTone.TERTIARY, emphasized = true,
                text = context.getString(titleRes),
            )
            titleLabel.maxLines = 1
            column.addView(titleLabel, linearParams(MATCH, WRAP))

            valueLabel.setTextColor(whiteAlpha(0.94f))
            valueLabel.typeface = Typeface.DEFAULT_BOLD
            valueLabel.includeFontPadding = false
            valueLabel.maxLines = 1
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                valueLabel, 12, 24, 1, TypedValue.COMPLEX_UNIT_SP
            )
            val unitLabel = voiceLoungeText(
                context, LiveTheme.TextRole.METADATA, LiveTheme.TextTone.DISABLED, text = context.getString(unitRes),
            )
            unitLabel.maxLines = 1
            val valueRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = true
                gravity = Gravity.BOTTOM
            }
            valueRow.addView(valueLabel, linearParams(0, dp(30), weight = 1f))
            valueRow.addView(unitLabel, linearParams(WRAP, WRAP, start = dp(5)))
            column.addView(valueRow, linearParams(MATCH, WRAP, top = dp(8)))
            addView(column, frameParams(MATCH, WRAP, Gravity.TOP, start = dp(13), top = dp(27), end = dp(13)))
        }

        fun bind(value: Int) {
            valueLabel.text = maxOf(0, value).toString()
        }
    }

    private companion object {
        val GIFT_ACCENT = rgb(0.941f, 0.737f, 0.373f)
        val FOLLOWERS_ACCENT = rgb(1f, 0.431f, 0.616f)
        val VIEWERS_ACCENT = rgb(0.412f, 0.659f, 1f)
        val FAN_ACCENT = rgb(0.745f, 0.514f, 1f)
        val GIFTERS_ACCENT = rgb(0.341f, 0.835f, 0.749f)
        val LIKES_ACCENT = rgb(1f, 0.482f, 0.447f)

        fun formatDuration(seconds: Int): String =
            String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)

        fun cardBackground(
            radius: Float,
            base: IntArray,
            accent: IntArray,
            orientation: GradientDrawable.Orientation,
            @ColorInt border: Int,
        ): LayerDrawable {
            val baseLayer = GradientDrawable(orientation, base).apply { cornerRadius = radius }
            val accentLayer = GradientDrawable(orientation, accent).apply { cornerRadius = radius }
            val borderLayer = GradientDrawable().apply {
                cornerRadius = radius
                setColor(android.graphics.Color.TRANSPARENT)
                setStroke(1, border)
            }
            return LayerDrawable(arrayOf(baseLayer, accentLayer, borderLayer))
        }
    }
}
