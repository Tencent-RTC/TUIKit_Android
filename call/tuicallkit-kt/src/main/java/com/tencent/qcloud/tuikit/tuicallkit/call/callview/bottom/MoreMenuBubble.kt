package com.tencent.qcloud.tuikit.tuicallkit.call.callview.bottom

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.call.CallKitConfig
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitBubble
import com.tencent.qcloud.tuikit.tuicallkit.common.dp

internal class MoreMenuBubble @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : CallKitBubble(context, attrs, defStyleAttr) {

    enum class Item {
        BlurBackground,
        Beauty
    }

    var onSelect: ((Item) -> Unit)? = null

    init {
        if (CallKitConfig.shared.enableVirtualBackground) {
            stackView.addView(makeRow(R.drawable.tuicallkit_icon_blur, context.getString(R.string.tuicallkit_more_blur_background), Item.BlurBackground))
            val separator = android.view.View(context).apply {
                setBackgroundColor(ContextCompat.getColor(context, R.color.tuicallkit_hairline_on_dark))
            }
            val separatorParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1.dp(context))
            separatorParams.marginStart = 18.dp(context)
            separatorParams.marginEnd = 18.dp(context)
            stackView.addView(separator, separatorParams)
        }
        stackView.addView(makeRow(R.drawable.tuicallkit_icon_beauty, context.getString(R.string.tuicallkit_more_beauty), Item.Beauty))
    }

    private fun makeRow(iconRes: Int, text: String, item: Item): android.view.View {
        val context = context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val paddingV = 12.dp(context)
            val paddingH = 18.dp(context)
            setPadding(paddingH, paddingV, paddingH, paddingV)
            setBackgroundResource(R.drawable.tuicallkit_bg_row_ripple)
            isClickable = true
            setOnClickListener { onSelect?.invoke(item) }
        }
        val icon = ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(ContextCompat.getColor(context, android.R.color.white))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        row.addView(icon, LinearLayout.LayoutParams(22.dp(context), 22.dp(context)))
        val label = TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }
        val labelParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        labelParams.marginStart = 12.dp(context)
        row.addView(label, labelParams)
        return row
    }
}
