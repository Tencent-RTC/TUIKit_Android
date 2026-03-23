package com.example.atomicxcore.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.material.tabs.TabLayout

/**
 * Tab switching container component
 *
 * Used in SettingPanelController to display multiple setting panels as tabs:
 * - Device management (DeviceSettingView)
 * - Beauty settings (BeautySettingView)
 * - Audio effects (AudioEffectSettingView)
 *
 * Design: Uses TabLayout for top tab switching, FrameLayout at the bottom to display corresponding content views.
 * All tab contents are added to the container during initialization; visibility is toggled to show/hide them.
 */
class TabbedSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    data class Tab(val title: String, val view: View)

    private val tabLayout: TabLayout
    private val containerView: FrameLayout
    private val tabs = mutableListOf<Tab>()
    private var selectedTabIndex = 0

    init {
        orientation = VERTICAL

        val dp8 = (8 * resources.displayMetrics.density).toInt()
        val dp16 = (16 * resources.displayMetrics.density).toInt()

        // TabLayout
        tabLayout = TabLayout(context).apply {
            tabMode = TabLayout.MODE_FIXED
            tabGravity = TabLayout.GRAVITY_FILL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp8
                marginStart = dp16
                marginEnd = dp16
            }
        }
        addView(tabLayout)

        // Content container
        containerView = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        addView(containerView)

        // Listen for tab selection changes
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val index = tab?.position ?: return
                switchToTab(index)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    /**
     * Set the tab list and initialize views
     */
    fun setTabs(tabList: List<Tab>) {
        tabs.clear()
        tabs.addAll(tabList)
        tabLayout.removeAllTabs()
        containerView.removeAllViews()

        for ((index, tab) in tabs.withIndex()) {
            tabLayout.addTab(tabLayout.newTab().setText(tab.title))

            tab.view.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            tab.view.visibility = if (index == 0) View.VISIBLE else View.GONE
            containerView.addView(tab.view)
        }

        selectedTabIndex = 0
    }

    private fun switchToTab(index: Int) {
        if (index == selectedTabIndex || index >= tabs.size) return

        tabs[selectedTabIndex].view.visibility = View.GONE
        tabs[index].view.visibility = View.VISIBLE
        selectedTabIndex = index
    }
}
