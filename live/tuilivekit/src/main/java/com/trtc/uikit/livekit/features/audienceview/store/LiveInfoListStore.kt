package com.trtc.uikit.livekit.features.audienceview.store

import android.text.TextUtils
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.audienceview.AudienceViewDefine
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo

class LiveInfoListStore(private val liveListDataSource: AudienceViewDefine.LiveListDataSource) {

    companion object {
        private val LOGGER = LiveKitLogger.getComponentLogger("LiveInfoListStore")
    }

    private var cursor: String = ""
    private var firstLiveInfo: LiveInfo? = null
    private val liveInfoList: MutableList<LiveInfo> = ArrayList()

    fun setFirstData(liveInfo: LiveInfo) {
        firstLiveInfo = liveInfo
    }

    fun refreshLiveList(callback: AudienceViewDefine.LiveListCallback) {
        fetchLiveList(true, callback)
    }

    fun fetchLiveList(callback: AudienceViewDefine.LiveListCallback) {
        fetchLiveList(false, callback)
    }

    private fun fetchLiveList(
        isRefresh: Boolean,
        callback: AudienceViewDefine.LiveListCallback
    ) {
        LOGGER.info("fetchLiveList start,isRefresh:$isRefresh")
        val currentCursor = if (isRefresh) "" else cursor
        cursor = currentCursor
        liveListDataSource.fetchLiveList(
            currentCursor,
            object : AudienceViewDefine.LiveListCallback {
                override fun onSuccess(cursor: String, liveInfoList: List<LiveInfo>) {
                    LOGGER.info("fetchLiveList onSuccess. result.liveInfoList.size:${liveInfoList.size}")
                    val list = ArrayList<LiveInfo>()
                    for (liveInfo in liveInfoList) {
                        if (firstLiveInfo != null) {
                            if (TextUtils.equals(liveInfo.liveID, firstLiveInfo!!.liveID)) {
                                continue
                            }
                        }
                        list.add(liveInfo)
                    }

                    if (isRefresh) {
                        this@LiveInfoListStore.liveInfoList.clear()
                    }
                    this@LiveInfoListStore.liveInfoList.addAll(list)
                    this@LiveInfoListStore.cursor = cursor
                    callback.onSuccess(cursor, list)
                }

                override fun onError(code: Int, message: String) {
                    LOGGER.error("fetchLiveList onError. code:$code,message:$message")
                    callback.onError(code, message)
                }
            })
    }

    fun getLiveList(): List<LiveInfo> {
        return liveInfoList
    }

    fun getLiveListDataCursor(): String {
        return cursor
    }
}
