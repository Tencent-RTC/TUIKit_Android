package com.tencent.uikit.app.call.entry

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tencent.uikit.app.R
import com.tencent.uikit.app.call.common.dp

internal class CallAccessSegment @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    internal var onSelect: ((Int) -> Unit)? = null

    private val container: LinearLayout
    private val buttons = mutableListOf<TextView>()
    private var selectedIndex = 0

    init {
        setBackgroundResource(R.drawable.callaccess_bg_segment_track)
        container = LinearLayout(context)
        container.orientation = LinearLayout.HORIZONTAL
        val padding = 2.dp(context)
        container.setPadding(padding, padding, padding, padding)
        addView(container, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun configure(items: List<String>, fontSizeSp: Float, widthDp: Int) {
        container.removeAllViews()
        buttons.clear()
        items.forEachIndexed { index, title ->
            val item = TextView(context)
            item.text = title
            item.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            item.gravity = Gravity.CENTER
            item.setOnClickListener {
                select(index)
                onSelect?.invoke(index)
            }
            container.addView(item, LinearLayout.LayoutParams(0, 28.dp(context), 1f))
            buttons.add(item)
        }
        val params = layoutParams ?: LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        params.width = widthDp.dp(context)
        layoutParams = params
        select(selectedIndex.coerceAtMost((items.size - 1).coerceAtLeast(0)))
    }

    fun select(index: Int) {
        selectedIndex = index
        buttons.forEachIndexed { i, button ->
            val selected = i == index
            button.background = if (selected) ContextCompat.getDrawable(context, R.drawable.callaccess_bg_segment_item) else null
            button.elevation = if (selected) 1.dp(context).toFloat() else 0f
            button.setTextColor(
                ContextCompat.getColor(context, if (selected) R.color.callaccess_brand else R.color.callaccess_text2)
            )
            button.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }
}
