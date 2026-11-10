package com.trtc.uikit.livekit.common.theme

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LineHeightSpan
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import com.trtc.uikit.livekit.R
import kotlin.math.ceil

internal class LiveText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatTextView(context, attrs, defStyleAttr) {
    var role: LiveTheme.TextRole = LiveTheme.TextRole.BODY
        set(value) {
            field = value
            applyFont()
        }

    var tone: LiveTheme.TextTone = LiveTheme.TextTone.PRIMARY
        set(value) {
            field = value
            setTextColor(LiveTheme.Color.text(value))
        }

    var isEmphasized: Boolean = false
        set(value) {
            field = value
            applyFont()
        }

    init {
        val values = context.obtainStyledAttributes(attrs, R.styleable.LiveText)
        try {
            role = LiveTheme.TextRole.values()[
                values.getInt(R.styleable.LiveText_liveTextRole, LiveTheme.TextRole.BODY.ordinal),
            ]
            tone = LiveTheme.TextTone.values()[
                values.getInt(R.styleable.LiveText_liveTextTone, LiveTheme.TextTone.PRIMARY.ordinal),
            ]
            isEmphasized = values.getBoolean(R.styleable.LiveText_liveTextEmphasized, false)
        } finally {
            values.recycle()
        }
    }

    fun setText(text: String, lineHeightSp: Float) {
        val lineHeightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            lineHeightSp,
            resources.displayMetrics,
        ).toInt()
        this.text = SpannableString(text).apply {
            setSpan(
                FixedLineHeightSpan(lineHeightPx),
                0,
                length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE,
            )
        }
    }

    private fun applyFont() {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, role.sizeSp)
        typeface = Typeface.create(
            if (role.semibold || isEmphasized) "sans-serif-medium" else "sans-serif",
            Typeface.NORMAL,
        )
    }

    private class FixedLineHeightSpan(private val lineHeight: Int) : LineHeightSpan {
        override fun chooseHeight(
            text: CharSequence?,
            start: Int,
            end: Int,
            spanStartVertical: Int,
            lineHeightVertical: Int,
            fontMetrics: Paint.FontMetricsInt?,
        ) {
            val metrics = fontMetrics ?: return
            val currentHeight = metrics.descent - metrics.ascent
            if (currentHeight <= 0) return
            metrics.descent = ceil(metrics.descent * lineHeight.toFloat() / currentHeight).toInt()
            metrics.ascent = metrics.descent - lineHeight
        }
    }
}
