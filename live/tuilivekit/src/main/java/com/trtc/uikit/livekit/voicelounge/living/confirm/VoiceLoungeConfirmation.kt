package com.trtc.uikit.livekit.voicelounge.living.confirm

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
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
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeSkin
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha

internal class VoiceLoungeEndPanel(context: Context) : BottomSheetView(context) {
    var onConfirmEnd: (() -> Unit)? = null

    private val audienceStatusLabel = TextView(context)
    private val confirmationLabel = voiceLoungeText(context, LiveTheme.TextRole.SHEET_TITLE, gravity = Gravity.CENTER)
    private val emptyLabel =
        voiceLoungeText(context, LiveTheme.TextRole.BODY, LiveTheme.TextTone.SECONDARY, Gravity.CENTER)
    private val buttonRow = LinearLayout(context)
    private val continueButton =
        makeButton(context.getString(R.string.livekit_voiceroom_end_continue), VoiceLoungeColor.overlayBase)
    private val endButton = makeButton(context.getString(R.string.livekit_voiceroom_end_end), END_BACKGROUND)
    private var hasAudience = false

    override val sheetHeight: Int get() = context.dp(if (hasAudience) 244 else 208)

    init {
        contentView.addView(
            audienceStatusLabel,
            frameParams(
                MATCH, context.dp(31), Gravity.TOP, start = context.dp(24), end = context.dp(24), top = context.dp(48)
            ),
        )
        contentView.addView(
            confirmationLabel,
            frameParams(
                MATCH, context.dp(24), Gravity.TOP, start = context.dp(24), end = context.dp(24), top = context.dp(92)
            ),
        )
        contentView.addView(
            emptyLabel,
            frameParams(MATCH, WRAP, Gravity.TOP, start = context.dp(28), end = context.dp(28), top = context.dp(54)),
        )
        buttonRow.orientation = LinearLayout.HORIZONTAL
        buttonRow.addView(continueButton, linearParams(0, MATCH, 1f))
        buttonRow.addView(endButton, linearParams(0, MATCH, 1f, start = context.dp(16)))
        contentView.addView(
            buttonRow,
            frameParams(
                MATCH, context.dp(46), Gravity.TOP, start = context.dp(20), end = context.dp(20), top = context.dp(116)
            ),
        )
        audienceStatusLabel.gravity = Gravity.CENTER
        audienceStatusLabel.includeFontPadding = false
        audienceStatusLabel.maxLines = 1
        confirmationLabel.text = context.getString(R.string.livekit_voiceroom_end_confirm)
        emptyLabel.text = context.getString(R.string.livekit_voiceroom_end_empty)
        continueButton.setOnClickListener { dismiss() }
        endButton.setOnClickListener { confirmEnd() }
    }

    fun show(viewerCount: Int) {
        hasAudience = viewerCount > 0
        audienceStatusLabel.visibility = if (hasAudience) View.VISIBLE else View.GONE
        confirmationLabel.visibility = if (hasAudience) View.VISIBLE else View.GONE
        emptyLabel.visibility = if (hasAudience) View.GONE else View.VISIBLE
        (buttonRow.layoutParams as FrameLayout.LayoutParams).topMargin = context.dp(if (hasAudience) 150 else 116)
        buttonRow.requestLayout()
        if (hasAudience) audienceStatusLabel.text = makeAudienceStatus(viewerCount)
        updateSheetHeight(animated = false)
        show()
    }

    private fun confirmEnd() {
        dismiss()
        onConfirmEnd?.invoke()
    }

    private fun makeButton(title: String, background: Int): TextView = TextView(context).apply {
        text = title
        setTextColor(whiteAlpha(0.94f))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        includeFontPadding = false
        this.background = roundedDrawable(background, context.dp(12).toFloat())
        setPressAlpha()
    }

    private fun makeAudienceStatus(count: Int): CharSequence {
        val countString = count.toString()
        val text = context.getString(R.string.livekit_voiceroom_end_audience_count, countString)
        val result = SpannableString(text)
        val all = 0 to text.length
        result.setSpan(AbsoluteSizeSpan(15, true), all.first, all.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        result.setSpan(ForegroundColorSpan(whiteAlpha(0.76f)), all.first, all.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val start = text.indexOf(countString)
        if (start >= 0) {
            val end = start + countString.length
            result.setSpan(AbsoluteSizeSpan(24, true), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.setSpan(
                ForegroundColorSpan(VoiceLoungeColor.statHighlight), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return result
    }

    private companion object {
        val END_BACKGROUND = rgb(0.769f, 0.267f, 0.314f)
    }
}

internal class VoiceLoungeAlertView(context: Context) : FrameLayout(context) {
    private val cardView = LinearLayout(context)
    private val titleLabel = voiceLoungeText(context, LiveTheme.TextRole.SHEET_TITLE, gravity = Gravity.CENTER)
    private val messageLabel =
        voiceLoungeText(context, LiveTheme.TextRole.BODY, LiveTheme.TextTone.SECONDARY, Gravity.CENTER)
    private val buttonRow = LinearLayout(context)
    private val cancelButton = makeButton(VoiceLoungeColor.overlayBase)
    private val confirmButton = makeButton(VoiceLoungeColor.rose)
    private var onConfirm: (() -> Unit)? = null

    init {
        visibility = View.GONE
        setBackgroundColor(LiveTheme.Color.sheetScrim)
        isClickable = true
        cardView.orientation = LinearLayout.VERTICAL
        cardView.background = roundedDrawable(
            VoiceLoungeColor.dialogBackground, dp(20).toFloat(), dp(1), VoiceLoungeColor.overlayHair,
        )
        cardView.setPadding(dp(20), dp(24), dp(20), dp(20))
        cardView.isClickable = true
        addView(cardView, frameParams(dp(CARD_WIDTH), WRAP, Gravity.CENTER))
        cardView.addView(titleLabel, linearParams(MATCH, WRAP))
        cardView.addView(messageLabel, linearParams(MATCH, WRAP, top = dp(10)))
        buttonRow.orientation = LinearLayout.HORIZONTAL
        buttonRow.addView(cancelButton, linearParams(0, MATCH, 1f))
        buttonRow.addView(confirmButton, linearParams(0, MATCH, 1f, start = dp(12)))
        cardView.addView(buttonRow, linearParams(MATCH, dp(44), top = dp(22)))
        cancelButton.text = context.getString(R.string.common_cancel)
        cancelButton.setOnClickListener { dismiss() }
        confirmButton.setOnClickListener {
            val action = onConfirm
            dismiss()
            action?.invoke()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val width = minOf(dp(CARD_WIDTH), w - dp(80))
        val params = cardView.layoutParams
        if (params.width != width && width > 0) {
            params.width = width
            post { cardView.layoutParams = params }
        }
    }

    fun show(title: String, message: String, confirmTitle: String, skin: VoiceLoungeSkin, onConfirm: () -> Unit) {
        titleLabel.text = title
        messageLabel.text = message
        confirmButton.text = confirmTitle
        confirmButton.background = roundedDrawable(skin.mid, dp(12).toFloat())
        this.onConfirm = onConfirm
        bringToFront()
        visibility = View.VISIBLE
        alpha = 0f
        cardView.scaleX = 0.92f
        cardView.scaleY = 0.92f
        animate().cancel()
        animate().alpha(1f).setDuration(200).start()
        cardView.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    fun dismiss() {
        if (visibility != View.VISIBLE) return
        onConfirm = null
        animate().cancel()
        animate().alpha(0f).setDuration(150).withEndAction { visibility = View.GONE }.start()
    }

    private fun makeButton(background: Int): TextView = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        includeFontPadding = false
        this.background = roundedDrawable(background, context.dp(12).toFloat())
        setPressAlpha()
    }

    private companion object {
        const val CARD_WIDTH = 300
    }
}
