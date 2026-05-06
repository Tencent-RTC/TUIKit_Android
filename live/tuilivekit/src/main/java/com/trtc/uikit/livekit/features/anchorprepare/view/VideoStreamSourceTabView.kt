package com.trtc.uikit.livekit.features.anchorprepare.view

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.features.anchorprepare.VideoStreamSource
import io.trtc.tuikit.atomicx.common.util.ScreenUtil

class VideoStreamSourceTabView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onVideoStreamSourceChanged: ((VideoStreamSource) -> Unit)? = null

    private var currentSource: VideoStreamSource = VideoStreamSource.CAMERA
    private val tabItems = mutableListOf<TabItemView>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        initTabs()
    }

    private fun initTabs() {
        addTabItem(
            title = context.getString(R.string.common_preview_video_live),
            source = VideoStreamSource.CAMERA,
            isSelected = true
        )

        val spacer = android.view.View(context)
        val spacerParams = LayoutParams(ScreenUtil.dip2px(32.0f), ScreenUtil.dip2px(1.0f))
        addView(spacer, spacerParams)

        addTabItem(
            title = context.getString(R.string.common_game_live),
            source = VideoStreamSource.SCREEN_SHARE,
            isSelected = false
        )
    }

    private fun addTabItem(title: String, source: VideoStreamSource, isSelected: Boolean) {
        val tabItem = TabItemView(context, title, source, isSelected)
        tabItem.setOnClickListener {
            if (currentSource != source) {
                currentSource = source
                updateTabSelection()
                onVideoStreamSourceChanged?.invoke(source)
            }
        }
        tabItems.add(tabItem)
        val tabParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        addView(tabItem, tabParams)
    }

    private fun updateTabSelection() {
        tabItems.forEach { tab ->
            tab.setSelected(tab.source == currentSource)
        }
    }

    fun setCurrentSource(source: VideoStreamSource) {
        if (currentSource != source) {
            currentSource = source
            updateTabSelection()
        }
    }

    private class TabItemView(
        context: Context,
        title: String,
        val source: VideoStreamSource,
        isSelected: Boolean
    ) : LinearLayout(context) {

        private val titleView: TextView
        private val indicatorView: android.view.View

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL

            titleView = TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
            }
            val titleParams = LayoutParams(LayoutParams.WRAP_CONTENT, 0).apply {
                weight = 1f
            }
            addView(titleView, titleParams)

            indicatorView = android.view.View(context)
            val indicatorParams = LayoutParams(ScreenUtil.dip2px(24.0f), ScreenUtil.dip2px(2.0f))
            addView(indicatorView, indicatorParams)

            setSelected(isSelected)
        }

        override fun setSelected(selected: Boolean) {
            if (selected) {
                titleView.setTextColor(context.resources.getColor(R.color.common_design_standard_flowkit_white))
                titleView.typeface = Typeface.DEFAULT_BOLD
                indicatorView.setBackgroundColor(
                    context.resources.getColor(R.color.common_design_standard_flowkit_white)
                )
            } else {
                titleView.setTextColor(context.resources.getColor(R.color.common_design_standard_g6))
                titleView.typeface = Typeface.DEFAULT
                indicatorView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
    }
}
