package com.trtc.uikit.livekit.features.livelist

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.tencent.qcloud.tuicore.TUICore
import com.trtc.uikit.livekit.features.livelist.view.access.DoubleColumnListViewAdapter
import com.trtc.uikit.livekit.features.livelist.view.access.SingleColumnListViewAdapter
import com.trtc.uikit.livekit.features.livelist.view.access.TUILiveListDataSource
import com.trtc.uikit.livekit.features.livelist.store.LiveInfoListStore
import com.trtc.uikit.livekit.features.livelist.view.doublecolumn.DoubleColumnListView
import com.trtc.uikit.livekit.features.livelist.view.singlecolumn.SingleColumnListView

class LiveListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var style: Style
    private var singleColumnListView: SingleColumnListView? = null
    private var doubleColumnListView: DoubleColumnListView? = null

    private lateinit var liveInfoListStore: LiveInfoListStore
    private lateinit var fragmentActivity: FragmentActivity
    private var liveListViewAdapter: LiveListViewAdapter? = null
    private var onItemClickListener: OnItemClickListener? = null
    private var isInit = false

    fun init(
        fragmentActivity: FragmentActivity,
        style: Style,
        adapter: LiveListViewAdapter? = null,
        dataSource: LiveListDataSource? = null
    ) {
        this.fragmentActivity = fragmentActivity
        this.style = style
        liveListViewAdapter = adapter
        val liveDataSource = dataSource ?: TUILiveListDataSource()
        liveInfoListStore = LiveInfoListStore(liveDataSource)
        getOrCreateColumnView(style)
        switchColumnVisibility(style)
        isInit = true
    }

    fun updateColumnStyle(style: Style) {
        if (isInit && style != this.style) {
            this.style = style
            getOrCreateColumnView(style)
            switchColumnVisibility(style)
        }
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        onItemClickListener = listener
        doubleColumnListView?.let { it.setOnItemClickListener(listener) }
        singleColumnListView?.let { it.setOnItemClickListener(listener) }
    }

    fun refreshData() {
        when (style) {
            Style.DOUBLE_COLUMN -> doubleColumnListView?.refreshData()
            else -> singleColumnListView?.refreshData()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        enableSwitchPlaybackQuality(true)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        enableSwitchPlaybackQuality(false)
    }

    private fun getOrCreateColumnView(style: Style) {
        when (style) {
            Style.DOUBLE_COLUMN -> {
                if (doubleColumnListView == null) {
                    val adapter = liveListViewAdapter ?: DoubleColumnListViewAdapter(fragmentActivity)
                    doubleColumnListView = DoubleColumnListView(fragmentActivity).apply {
                        init(fragmentActivity, adapter, liveInfoListStore)
                    }
                    addView(doubleColumnListView)
                    onItemClickListener?.let { doubleColumnListView!!.setOnItemClickListener(it) }
                }
            }
            else -> {
                if (singleColumnListView == null) {
                    val adapter = liveListViewAdapter ?: SingleColumnListViewAdapter(fragmentActivity)
                    singleColumnListView = SingleColumnListView(fragmentActivity).apply {
                        init(fragmentActivity, adapter, liveInfoListStore)
                    }
                    addView(singleColumnListView)
                    onItemClickListener?.let { singleColumnListView!!.setOnItemClickListener(it) }
                }
            }
        }
    }

    private fun switchColumnVisibility(style: Style) {
        if (style == Style.DOUBLE_COLUMN) {
            singleColumnListView?.let {
                it.visibility = GONE
                it.stopPreview()
            }
            doubleColumnListView?.let {
                it.visibility = VISIBLE
                it.resumePreview()
            }
        } else {
            doubleColumnListView?.let {
                it.visibility = GONE
                it.stopPreview()
            }
            singleColumnListView?.let {
                it.visibility = VISIBLE
                it.resumePreview()
            }
        }
    }

    private fun enableSwitchPlaybackQuality(enable: Boolean) {
        val params: MutableMap<String?, Any?> = HashMap<String?, Any?>()
        params.put("enable", enable)
        TUICore.callService("AdvanceSettingManager", "enableSwitchPlaybackQuality", params)
    }
}
