package com.trtc.uikit.livekit.features.audienceview.view.liveListviewpager

import android.annotation.SuppressLint
import android.text.TextUtils
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.audienceview.AudienceViewDefine
import com.trtc.uikit.livekit.features.audienceview.store.AudienceConfig
import com.trtc.uikit.livekit.features.audienceview.store.LiveInfoListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo

abstract class LiveListViewPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val liveInfoListStore: LiveInfoListStore,
    liveInfo: LiveInfo?
) : FragmentStateAdapter(fragmentActivity) {

    companion object {
        private val LOGGER = LiveKitLogger.getCommonLogger("AudienceViewPagerAdapter")
    }

    private val liveInfoList: MutableList<LiveInfo> = ArrayList()
    private val fragmentManager: FragmentManager = fragmentActivity.supportFragmentManager

    private var isDataLoaded = false
    private var isLoading = false

    init {
        val liveInfoList = liveInfoListStore.getLiveList()
        if (liveInfoList.isNotEmpty()) {
            addData(liveInfoList)
            isDataLoaded = true
        } else {
            if (liveInfo != null) {
                liveInfoListStore.setFirstData(liveInfo)
                val firstLiveInfoList = ArrayList<LiveInfo>()
                firstLiveInfoList.add(liveInfo)
                addData(firstLiveInfoList)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun retainOnlyFirstElement() {
        if (liveInfoList.size > 1) {
            liveInfoList.subList(1, liveInfoList.size).clear()
            notifyDataSetChanged()
        }
    }

    fun getFragment(position: Int): Fragment? {
        return fragmentManager.findFragmentByTag("f$position")
    }

    val dataList: List<LiveInfo>
        get() = liveInfoList

    fun fetchData() {
        val disableRoomSliding = AudienceConfig.disableSliding.value == true
        LOGGER.info("fetchLiveList disableRoomSliding:$disableRoomSliding")
        if (disableRoomSliding) {
            return
        }
        if (isLoading) {
            LOGGER.info("is start fetch data, waiting")
            return
        }
        if (isDataLoaded && TextUtils.isEmpty(liveInfoListStore.getLiveListDataCursor())) {
            LOGGER.info("there is no more data")
            return
        }
        isLoading = true
        liveInfoListStore.fetchLiveList(object : AudienceViewDefine.LiveListCallback {
            override fun onSuccess(cursor: String, liveInfoList: List<LiveInfo>) {
                if (AudienceConfig.disableSliding.value == false) {
                    val startPosition = this@LiveListViewPagerAdapter.liveInfoList.size
                    this@LiveListViewPagerAdapter.liveInfoList.addAll(liveInfoList)
                    notifyItemRangeInserted(
                        startPosition,
                        this@LiveListViewPagerAdapter.liveInfoList.size
                    )
                }
                isDataLoaded = true
                isLoading = false
            }

            override fun onError(code: Int, message: String) {
                LOGGER.error("fetchLiveList failed,errorCode:$code,message:$message")
                isLoading = false
            }
        })
    }

    fun refreshData(callback: ActionCallback?) {
        val disableRoomSliding = AudienceConfig.disableSliding.value == true
        LOGGER.info("refreshData disableRoomSliding:$disableRoomSliding")
        if (disableRoomSliding) {
            callback?.onComplete()
            return
        }
        isLoading = true
        liveInfoListStore.refreshLiveList(object : AudienceViewDefine.LiveListCallback {
            @SuppressLint("NotifyDataSetChanged")
            override fun onSuccess(cursor: String, liveInfoList: List<LiveInfo>) {
                this@LiveListViewPagerAdapter.liveInfoList.clear()
                this@LiveListViewPagerAdapter.liveInfoList.addAll(liveInfoList)
                notifyDataSetChanged()
                callback?.onComplete()
                isDataLoaded = true
                isLoading = false
            }

            override fun onError(code: Int, message: String) {
                LOGGER.error("refreshData failed,errorCode:$code,message:$message")
                callback?.onComplete()
                isLoading = false
            }
        })
    }

    override fun createFragment(position: Int): Fragment {
        return LiveListFragment(liveInfoList[position], this)
    }

    override fun getItemCount(): Int {
        return liveInfoList.size
    }

    abstract fun onCreateView(liveInfo: LiveInfo): View

    open fun onViewWillSlideIn(view: View?) {}

    open fun onViewDidSlideIn(view: View?) {}

    open fun onViewSlideInCancelled(view: View?) {}

    open fun onViewWillSlideOut(view: View?) {}

    open fun onViewDidSlideOut(view: View?) {}

    open fun onViewSlideOutCancelled(view: View?) {}

    private fun addData(liveInfoList: List<LiveInfo>) {
        val startPosition = this.liveInfoList.size
        this.liveInfoList.addAll(liveInfoList)
        notifyItemRangeInserted(startPosition, this.liveInfoList.size)
    }

    interface ActionCallback {
        fun onComplete()
    }
}
