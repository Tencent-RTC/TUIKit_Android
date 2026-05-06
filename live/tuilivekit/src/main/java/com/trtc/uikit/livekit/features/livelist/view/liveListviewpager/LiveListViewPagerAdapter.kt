package com.trtc.uikit.livekit.features.livelist.view.liveListviewpager

import android.text.TextUtils
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.livelist.store.LiveInfoListStore
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore

abstract class LiveListViewPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val liveInfoListStore: LiveInfoListStore,
) : FragmentStateAdapter(fragmentActivity) {

    companion object {
        private val LOGGER = LiveKitLogger.getCommonLogger("LiveListViewPagerAdapter")
    }

    private val fragmentManager: FragmentManager = fragmentActivity.supportFragmentManager
    private val liveInfoList = mutableListOf<LiveInfo>()
    private var isDataLoaded = false
    private var isLoading = false
    private var onDataChangedListener: OnDataChangedListener? = null

    init {
        val liveInfoList = liveInfoListStore.getLiveList()
        if (liveInfoList.isNotEmpty()) {
            addData(liveInfoList)
            isDataLoaded = true
        }
    }

    fun getFragment(position: Int): Fragment? {
        return fragmentManager.findFragmentByTag("f$position")
    }

    fun getDataList(): List<LiveInfo> = liveInfoList

    fun fetchData() {
        LOGGER.info("fetchLiveList enableSliding:")
        if (isLoading) {
            LOGGER.info("is start fetch data, waiting")
            return
        }
        if (isDataLoaded && TextUtils.isEmpty(liveInfoListStore.getLiveListDataCursor())) {
            LOGGER.info("there is no more data")
            return
        }
        isLoading = true
        liveInfoListStore.fetchLiveList(object : CompletionHandler {
            override fun onSuccess() {
                val startPosition = this@LiveListViewPagerAdapter.liveInfoList.size
                this@LiveListViewPagerAdapter.liveInfoList.clear()
                this@LiveListViewPagerAdapter.liveInfoList.addAll(LiveListStore.shared().liveState.liveList.value)
                notifyItemRangeInserted(startPosition, liveInfoList.size)
                isDataLoaded = true
                isLoading = false
            }

            override fun onFailure(code: Int, desc: String) {
                LOGGER.error("fetchLiveList failed,errorCode:$code,desc:$desc")
                isLoading = false
            }
        })
    }

    fun refreshData(callback: ActionCallback?) {
        LOGGER.info("refreshData enableSliding:")
        isLoading = true
        liveInfoListStore.refreshLiveList(object : CompletionHandler {
            override fun onSuccess() {
                this@LiveListViewPagerAdapter.liveInfoList.clear()
                this@LiveListViewPagerAdapter.liveInfoList.addAll(LiveListStore.shared().liveState.liveList.value)
                updateLiveList(liveInfoList)
                callback?.onComplete()
                isDataLoaded = true
                isLoading = false
            }

            override fun onFailure(code: Int, desc: String) {
                TODO("Not yet implemented")
            }
        })
    }

    override fun createFragment(position: Int): Fragment {
        return LiveListFragment(liveInfoList[position], this)
    }

    private fun updateLiveList(newLiveInfoList: List<LiveInfo>) {
        val oldSize = itemCount
        newLiveInfoList.forEachIndexed { index, liveInfo ->
            if (index < oldSize) {
                (getFragment(index) as? LiveListFragment)?.updateLiveInfo(liveInfo)
            }
        }
        val newSize = newLiveInfoList.size
        if (newSize > oldSize) {
            notifyItemRangeInserted(oldSize, newSize - oldSize)
        } else if (newSize < oldSize) {
            notifyItemRangeRemoved(newSize, oldSize - newSize)
        }
        onDataChangedListener?.onChanged()
    }

    override fun getItemCount(): Int = liveInfoList.size

    abstract fun createLiveInfoView(liveInfo: LiveInfo): View

    abstract fun updateLiveInfoView(view: View, liveInfo: LiveInfo)

    open fun onViewWillSlideIn(view: View) {}

    open fun onViewDidSlideIn(view: View) {}

    open fun onViewSlideInCancelled(view: View) {}

    open fun onViewWillSlideOut(view: View) {}

    open fun onViewDidSlideOut(view: View) {}

    open fun onViewSlideOutCancelled(view: View) {}

    fun setOnDataChangedListener(listener: OnDataChangedListener?) {
        onDataChangedListener = listener
    }

    private fun addData(liveInfoList: List<LiveInfo>) {
        val startPosition = this.liveInfoList.size
        this.liveInfoList.addAll(liveInfoList)
        notifyItemRangeInserted(startPosition, liveInfoList.size)
    }

    fun interface ActionCallback {
        fun onComplete()
    }

    fun interface OnDataChangedListener {
        fun onChanged()
    }
}