package com.trtc.uikit.livekit.pusher.living.endconfirm

import androidx.core.content.ContextCompat
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView

/** Bottom confirmation drawer shown before ending a host live stream. */
class PusherEndConfirmationView(context: Context) : BottomSheetView(context) {
    var onConfirmEnd: (() -> Unit)? = null

    private val audience: TextView
    private val question: TextView
    private val empty: TextView
    private val body: View
    private var hasAudience = false
    private var statusBarTop = 0

    override val sheetHeight: Int
        get() {
            val width = contentView.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
            body.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            return body.measuredHeight
                .coerceAtMost(context.resources.displayMetrics.heightPixels - statusBarTop - dp(24))
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_end_confirmation_view, contentView, true)
        audience = contentView.findViewById(R.id.livekit_pusher_end_audience)
        question = contentView.findViewById(R.id.livekit_pusher_end_question)
        empty = contentView.findViewById(R.id.livekit_pusher_end_empty)
        body = contentView.findViewById(R.id.livekit_pusher_end_body)
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val bottom = dp(24) + insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (body.paddingBottom != bottom) body.setPadding(body.paddingLeft, body.paddingTop, body.paddingRight, bottom)
            updateSheetHeight(animated = false)
            insets
        }
        contentView.findViewById<View>(R.id.livekit_pusher_end_continue).setOnClickListener { dismiss() }
        contentView.findViewById<View>(R.id.livekit_pusher_end_confirm_button).setOnClickListener {
            dismiss()
            onConfirmEnd?.invoke()
        }
    }

    fun show(viewerCount: Int) {
        hasAudience = viewerCount > 0
        audience.isVisible = hasAudience
        question.isVisible = hasAudience
        empty.isVisible = !hasAudience
        body.setPadding(body.paddingLeft, dp(if (hasAudience) 49 else 59), body.paddingRight, body.paddingBottom)
        val buttons = contentView.findViewById<View>(R.id.livekit_pusher_end_buttons)
        buttons.layoutParams = (buttons.layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = dp(if (hasAudience) 35 else 37)
        }
        if (hasAudience) audience.text = audienceText(viewerCount)
        super.show()
    }

    fun dismissIfShowing(): Boolean {
        if (!isShowing) return false
        dismiss()
        return true
    }

    private fun audienceText(count: Int): SpannableString {
        val countText = count.toString()
        val result = SpannableString(context.getString(R.string.livekit_pusher_end_audience_count, count))
        val start = result.indexOf(countText)
        if (start >= 0) {
            val end = start + countText.length
            result.setSpan(AbsoluteSizeSpan(23, true), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.livekit_pusher_end_count)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return result
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
