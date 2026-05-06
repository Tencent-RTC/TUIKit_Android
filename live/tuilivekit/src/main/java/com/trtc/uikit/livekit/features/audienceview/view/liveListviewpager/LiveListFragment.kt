package com.trtc.uikit.livekit.features.audienceview.view.liveListviewpager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.fragment.app.Fragment
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo

class LiveListFragment(
    private val liveInfo: LiveInfo,
    private val liveListViewAdapter: LiveListViewPagerAdapter?
) : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (liveListViewAdapter == null) {
            return RelativeLayout(context)
        }
        return liveListViewAdapter.onCreateView(liveInfo)
    }

    fun onFragmentWillSlideIn() {
        liveListViewAdapter?.onViewWillSlideIn(view)
    }

    fun onFragmentDidSlideIn() {
        liveListViewAdapter?.onViewDidSlideIn(view)
    }

    fun onFragmentSlideInCancelled() {
        liveListViewAdapter?.onViewSlideInCancelled(view)
    }

    fun onFragmentWillSlideOut() {
        liveListViewAdapter?.onViewWillSlideOut(view)
    }

    fun onFragmentDidSlideOut() {
        liveListViewAdapter?.onViewDidSlideOut(view)
    }

    fun onFragmentSlideOutCancelled() {
        liveListViewAdapter?.onViewSlideOutCancelled(view)
    }
}
