package com.tencent.uikit.app.call.entry

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.tencent.uikit.app.R
import com.tencent.uikit.app.call.common.dp
import kotlin.math.max
import kotlin.math.min

internal class TokenFlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {

    private val spacing = 8.dp(context)
    private val chipHeight = 32.dp(context)

    fun setContacts(contacts: List<EntryContact>, onRemove: (String) -> Unit) {
        removeAllViews()
        contacts.forEach { contact ->
            val chip = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4.dp(context), 0, 6.dp(context), 0)
                setBackgroundResource(R.drawable.callaccess_bg_token)
            }
            val avatar = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(24.dp(context), 24.dp(context))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.callaccess_bg_avatar_circle)
                clipToOutline = true
            }
            Glide.with(this).load(contact.avatarURL).circleCrop()
                .placeholder(R.drawable.callaccess_default_avatar)
                .error(R.drawable.callaccess_default_avatar)
                .fallback(R.drawable.callaccess_default_avatar)
                .into(avatar)
            val name = TextView(context).apply {
                text = contact.displayName
                textSize = 13f
                maxWidth = 84.dp(context)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
                setTextColor(context.getColor(R.color.callaccess_text))
                layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    marginStart = 6.dp(context)
                }
            }
            val remove = TextView(context).apply {
                text = context.getString(R.string.callaccess_symbol_close)
                textSize = 14f
                gravity = Gravity.CENTER
                includeFontPadding = false
                contentDescription = context.getString(R.string.callaccess_entry_remove_format, contact.displayName)
                setTextColor(context.getColor(R.color.callaccess_text3))
                layoutParams = LinearLayout.LayoutParams(16.dp(context), 16.dp(context)).apply {
                    marginStart = 6.dp(context)
                }
                setOnClickListener { onRemove(contact.userID) }
            }
            chip.addView(avatar)
            chip.addView(name)
            chip.addView(remove)
            addView(chip)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val available = width - paddingLeft - paddingRight
        for (index in 0 until childCount) {
            getChildAt(index).measure(
                MeasureSpec.makeMeasureSpec(max(available, 0), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(chipHeight, MeasureSpec.EXACTLY),
            )
        }
        setMeasuredDimension(width, contentHeight(available) + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var x = paddingLeft
        var y = paddingTop
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (x > paddingLeft && x + child.measuredWidth > width - paddingRight) {
                x = paddingLeft
                y += chipHeight + spacing
            }
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
            x += child.measuredWidth + spacing
        }
    }

    private fun contentHeight(availableWidth: Int): Int {
        if (childCount == 0 || availableWidth <= 0) return 0
        var x = 0
        var rows = 1
        for (index in 0 until childCount) {
            val childWidth = getChildAt(index).measuredWidth
            if (x > 0 && x + childWidth > availableWidth) {
                rows += 1
                x = 0
            }
            x += childWidth + spacing
        }
        return rows * chipHeight + (rows - 1) * spacing
    }
}

internal class TokenScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ScrollView(context, attrs) {

    private val maxVisibleHeight = (5 * 32 + 4 * 8).dp(context)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val limit = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> maxVisibleHeight
            else -> min(MeasureSpec.getSize(heightMeasureSpec), maxVisibleHeight)
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST))
    }
}
