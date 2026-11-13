package com.tencent.uikit.app.call.entry

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.tencent.uikit.app.R
import com.tencent.uikit.app.call.common.dp
import kotlin.math.max

internal class TokenFlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {

    private val spacing = 8.dp(context)

    fun setTokens(ids: List<String>, onRemove: (String) -> Unit) {
        removeAllViews()
        for (id in ids) {
            val capsule = LayoutInflater.from(context).inflate(R.layout.callaccess_token_capsule, this, false)
            capsule.findViewById<TextView>(R.id.token_label).text = id
            capsule.findViewById<View>(R.id.token_remove).setOnClickListener { onRemove(id) }
            addView(capsule)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        var maxChildHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            maxChildHeight = max(maxChildHeight, child.measuredHeight)
        }
        val contentHeight = contentHeight(width)
        val height = max(88.dp(context), contentHeight)
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var x = 0
        var y = 0
        var rowHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight
            if (x > 0 && x + childWidth > width) {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            child.layout(x, y, x + childWidth, y + childHeight)
            x += childWidth + spacing
            rowHeight = max(rowHeight, childHeight)
        }
    }

    private fun contentHeight(width: Int): Int {
        if (width <= 0 || childCount == 0) {
            return 88.dp(context)
        }
        var x = 0
        var rowHeight = 0
        var height = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (x > 0 && x + child.measuredWidth > width) {
                height += rowHeight + spacing
                x = 0
                rowHeight = 0
            }
            x += child.measuredWidth + spacing
            rowHeight = max(rowHeight, child.measuredHeight)
        }
        return max(88.dp(context), height + rowHeight)
    }
}
