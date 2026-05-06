package com.trtc.uikit.livekit.features.audienceview.view.liveListviewpager

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING
import androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE
import com.trtc.uikit.livekit.features.audienceview.store.AudienceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LiveListViewPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val viewPager: ViewPager2
    private var liveListViewAdapter: LiveListViewPagerAdapter? = null
    private var subscribeStateJob: Job? = null

    private var currentPosition = -1
    private var positionOffset = -1f
    private var willSlideInPosition = -1
    private var willSlideOutPosition = -1

    init {
        viewPager = ViewPager2(context)
        val layoutParams = RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        addView(viewPager, layoutParams)
        viewPager.offscreenPageLimit = 1
        viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    SCROLL_STATE_DRAGGING -> {
                        willSlideInPosition = -1
                        willSlideOutPosition = -1
                        positionOffset = -1f
                    }

                    SCROLL_STATE_IDLE -> {
                        onScrollEnd()
                    }

                    else -> {}
                }
            }

            override fun onPageSelected(position: Int) {
                if (currentPosition == -1) {
                    onFragmentDidSlideIn(position)
                    currentPosition = position
                    return
                }
                if (isSliding()) {
                    onFragmentDidSlideOut(currentPosition)
                    onFragmentDidSlideIn(position)
                    currentPosition = position
                }
            }

            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
                if (positionOffset <= 0) {
                    return
                }
                if (isSliding()) {
                    return
                }
                if (isSlideToPrevious(position, positionOffset)) {
                    onSlideToPrevious()
                } else {
                    onSlideToNext()
                }
                this@LiveListViewPager.positionOffset = positionOffset
            }
        })
    }

    fun setAdapter(adapter: LiveListViewPagerAdapter) {
        liveListViewAdapter = adapter
        viewPager.adapter = adapter
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            AudienceConfig.disableSliding.collect {
                onRoomSlidingDisable(it)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        subscribeStateJob?.cancel()
    }

    private fun getCurrentItem(): Int {
        return viewPager.currentItem
    }

    fun enableSliding(enabled: Boolean) {
        if (AudienceConfig.disableSliding.value == true) {
            return
        }
        viewPager.isUserInputEnabled = enabled
    }

    private fun isSlideToPrevious(position: Int, positionOffset: Float): Boolean {
        if (position < currentPosition) {
            if (this.positionOffset == -1f) {
                return true
            }
            return positionOffset < this.positionOffset
        }
        return false
    }

    private fun isSliding(): Boolean {
        return willSlideInPosition != -1 || willSlideOutPosition != -1
    }

    private fun onSlideToNext() {
        liveListViewAdapter?.let { adapter ->
            if (currentPosition >= adapter.dataList.size - 1) {
                return
            }
            willSlideOutPosition = currentPosition
            willSlideInPosition = currentPosition + 1
            onFragmentWillSlideOut(willSlideOutPosition)
            onFragmentWillSlideIn(willSlideInPosition)
        }
    }

    private fun onSlideToPrevious() {
        if (currentPosition <= 0) {
            return
        }
        willSlideOutPosition = currentPosition
        willSlideInPosition = currentPosition - 1
        onFragmentWillSlideOut(willSlideOutPosition)
        onFragmentWillSlideIn(willSlideInPosition)
    }

    private fun onScrollEnd() {
        if (!isSliding()) {
            return
        }
        liveListViewAdapter?.let { adapter ->
            if (willSlideInPosition < 0 || willSlideInPosition >= adapter.dataList.size) {
                return
            }
            if (willSlideOutPosition < 0 || willSlideOutPosition >= adapter.dataList.size) {
                return
            }
            if (willSlideInPosition != getCurrentItem()) {
                onFragmentSlideInCancelled(willSlideInPosition)
                onFragmentSlideOutCancelled(willSlideOutPosition)
            }
        }
        willSlideInPosition = -1
        willSlideOutPosition = -1
    }

    private fun onFragmentWillSlideIn(position: Int) {
        val fragment = liveListViewAdapter?.getFragment(position) as? LiveListFragment
        fragment?.onFragmentWillSlideIn()
    }

    private fun onFragmentDidSlideIn(position: Int) {
        val fragment = liveListViewAdapter?.getFragment(position) as? LiveListFragment
        fragment?.onFragmentDidSlideIn()
        liveListViewAdapter?.let { adapter ->
            if (position >= adapter.dataList.size - 2) {
                adapter.fetchData()
            }
        }
    }

    private fun onFragmentSlideInCancelled(position: Int) {
        val fragment = liveListViewAdapter?.getFragment(position) as? LiveListFragment
        fragment?.onFragmentSlideInCancelled()
    }

    private fun onFragmentWillSlideOut(position: Int) {
        val fragment = liveListViewAdapter?.getFragment(position) as? LiveListFragment
        fragment?.onFragmentWillSlideOut()
    }

    private fun onFragmentDidSlideOut(position: Int) {
        val fragment = liveListViewAdapter?.getFragment(position) as? LiveListFragment
        fragment?.onFragmentDidSlideOut()
    }

    private fun onFragmentSlideOutCancelled(position: Int) {
        val fragment = liveListViewAdapter?.getFragment(position) as? LiveListFragment
        fragment?.onFragmentSlideOutCancelled()
    }

    fun findViewByPosition(position: Int): View? {
        val fragment = liveListViewAdapter?.getFragment(position) as? LiveListFragment
        return fragment?.view
    }

    private fun onRoomSlidingDisable(disable: Boolean?) {
        viewPager.isUserInputEnabled = disable != true
    }
}
