package io.trtc.tuikit.chat.uikit.components.messagelist.ui.auxiliary
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.components.messagelist.adapter.MaxWidthLinearLayout
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import kotlin.math.roundToInt

class AuxiliaryTextBubbleView(
    context: Context
) : MaxWidthLinearLayout(context) {

    private val density = context.resources.displayMetrics.density
    private val progressView = ProgressBar(context).apply {
        visibility = View.GONE
    }
    private val contentView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setLineSpacing(0f, 1.3f)
    }
    private val footerView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.DEFAULT
        visibility = View.GONE
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.START
        addView(progressView, LayoutParams(18.dp, 18.dp))
        addView(contentView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = 3.dp
        })
        addView(footerView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = 4.dp
        })
    }

    fun bind(
        isSelf: Boolean,
        isLoading: Boolean,
        contentText: String?,
        footerText: String?,
        colors: ColorTokens
    ) {
        setPadding(9.dp, 6.dp, 9.dp, 6.dp)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 9.dp.toFloat()
            setColor(if (isSelf) colors.bgColorBubbleOwn else colors.bgColorBubbleReciprocal)
        }
        progressView.visibility = if (isLoading) View.VISIBLE else View.GONE
        contentView.text = contentText.orEmpty()
        contentView.setTextColor(if (isSelf) colors.textColorAntiPrimary else colors.textColorPrimary)
        val footerVisible = !footerText.isNullOrBlank()
        footerView.visibility = if (footerVisible) View.VISIBLE else View.GONE
        footerView.text = footerText.orEmpty()
        footerView.setTextColor(
            if (isSelf) {
                colors.textColorAntiPrimary and 0x99FFFFFF.toInt()
            } else {
                colors.textColorSecondary
            }
        )
    }

    private val Int.dp: Int
        get() = (this * density).roundToInt()
}
