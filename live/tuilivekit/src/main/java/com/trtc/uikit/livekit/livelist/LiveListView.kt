package com.trtc.uikit.livekit.livelist

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.trtc.uikit.livekit.livelist.viewpager.PagerView
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo

@SuppressLint("ViewConstructor")
open class LiveListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    liveID: String = "",
) : FrameLayout(context, attrs, defStyleAttr) {

    private val pagerView: PagerView = PagerView(
        context = context,
        initialLiveID = liveID,
        onLoadMoreCallback = { onLoadMore() },
    ).also {
        addView(
            it,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun setData(liveList: List<LiveInfo>) = pagerView.setData(liveList)
    fun appendData(liveList: List<LiveInfo>) = pagerView.appendData(liveList)
    open fun onLoadMore() {}

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pagerView.onDetached()
    }
}