package com.tencent.uikit.app.home

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tencent.uikit.app.R
import com.tencent.uikit.app.main.MainActivity

class HomeTabBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val tabs: List<TextView>

    init {
        inflate(context, R.layout.app_home_tab_bar, this)
        tabs = listOf(
            R.id.home_meeting, R.id.home_live, R.id.home_call, R.id.home_messages, R.id.home_profile
        ).map { findViewById(it) }
    }

    fun bind(host: MainActivity) {
        tabs.forEachIndexed { index, tab -> tab.setOnClickListener { host.selectTab(index) } }
    }

    fun updateInteractionEnabled(enabled: Boolean) {
        tabs.forEach { it.isEnabled = enabled }
    }

    fun updateUnreadCount(count: Long) {
        findViewById<TextView>(R.id.home_unread).apply {
            visibility = if (count > 0) VISIBLE else GONE
            text = if (count > 99) "99+" else count.toString()
        }
    }

    fun updateSelection(selected: Int) {
        val dark = selected == 1
        setBackgroundResource(if (dark) R.color.home_dark else R.color.home_light)
        tabs.forEachIndexed { index, tab ->
            val active = selected == index
            tab.isSelected = active
            tab.textSize = if (active) 17f else 16f
            tab.setTypeface(null, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            tab.setTextColor(
                ContextCompat.getColor(
                    context, if (dark) {
                        if (active) R.color.home_white else R.color.home_inactive_dark
                    } else if (active) R.color.home_text else R.color.home_inactive_light
                )
            )
        }
    }
}
