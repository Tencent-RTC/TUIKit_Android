package com.trtc.uikit.livekit.features.anchorview.store

import android.text.TextUtils
import com.google.gson.Gson
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine
import com.tencent.cloud.tuikit.engine.extension.TUILiveListManager
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.tencent.cloud.tuikit.engine.room.TUIRoomObserver
import com.tencent.trtc.TRTCCloudListener
import com.trtc.uikit.livekit.common.DEFAULT_BACKGROUND_URL
import com.trtc.uikit.livekit.common.DEFAULT_COVER_URL
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.component.pippanel.PIPPanelStore
import com.trtc.uikit.livekit.features.anchorview.AnchorState
import com.trtc.uikit.livekit.features.anchorview.AnchorViewListener
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveSummaryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

data class AnchorStoreState(
    var roomId: String,
    var liveInfo: LiveInfo,
    val lockAudioUserList: StateFlow<LinkedHashSet<String>>,
    val lockVideoUserList: StateFlow<LinkedHashSet<String>>,
    val screenStatus: SharedFlow<DeviceStatus>
)

class AnchorStore(liveInfo: LiveInfo) {

    private val _lockAudioUserList = MutableStateFlow<LinkedHashSet<String>>(LinkedHashSet())
    private val _lockVideoUserList = MutableStateFlow<LinkedHashSet<String>>(LinkedHashSet())
    private val _screenStatus =
        MutableSharedFlow<DeviceStatus>(replay = 0, extraBufferCapacity = 0, BufferOverflow.SUSPEND)
    private val state: AnchorStoreState = AnchorStoreState(
        roomId = liveInfo.liveID,
        liveInfo = liveInfo,
        lockAudioUserList = _lockAudioUserList,
        lockVideoUserList = _lockVideoUserList,
        screenStatus = _screenStatus
    )

    private var liveAudienceStore: LiveAudienceStore
    private var liveSummaryStore: LiveSummaryStore
    private val logger = LiveKitLogger.getFeaturesLogger("AnchorStore")
    private val userStore: UserStore = UserStore()
    private val mediaStore: MediaStore = MediaStore()
    private val anchorCoHostStore: AnchorCoHostStore = AnchorCoHostStore(liveInfo)
    private val anchorBattleStore: AnchorBattleStore = AnchorBattleStore(liveInfo)
    private val listenerList = CopyOnWriteArrayList<WeakReference<AnchorViewListener>>()
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())
    private val roomEngineObserver = object : TUIRoomObserver() {
        override fun onSeatListChanged(
            roomId: String?,
            seatList: List<TUIRoomDefine.SeatFullInfo>,
            newlySeatedUsers: List<TUIRoomDefine.UserInfo?>?,
            newlyLeftUsers: List<TUIRoomDefine.UserInfo?>?,
        ) {
            logger.info("${hashCode()} onSeatListChanged:roomId:$roomId,[seatList:${Gson().toJson(seatList)}]")
            if (state.roomId == roomId) {
                onSeatLockStateChanged(seatList)
            }
        }

        override fun onError(errorCode: TUICommonDefine.Error?, message: String?) {
            if (errorCode == TUICommonDefine.Error.START_SCREEN_SHARING_FAILED) {
                ErrorLocalized.onError(errorCode.value)
                mainScope.launch {
                    _screenStatus.emit(DeviceStatus.OFF)
                }
            }
        }
    }

    private val trtcCloudListener = object : TRTCCloudListener() {
        override fun onScreenCaptureStarted() {
            logger.info("${hashCode()} onScreenCaptureStarted")
            mainScope.launch {
                _screenStatus.emit(DeviceStatus.ON)
            }
        }

        override fun onScreenCaptureStopped(reason: Int) {
            logger.info("${hashCode()} onScreenCaptureStopped:reason:$reason")
            mainScope.launch {
                _screenStatus.emit(DeviceStatus.OFF)
            }
        }
    }

    private val externalState: AnchorState

    init {
        addObserver()
        setRoomId(liveInfo.liveID)
        liveAudienceStore = LiveAudienceStore.create(liveInfo.liveID)
        liveSummaryStore = LiveSummaryStore.create(liveInfo.liveID)
        mediaStore.setCustomVideoProcess()
        mediaStore.enableMultiPlaybackQuality(true)
        initCreateRoomState(liveInfo)

        externalState = AnchorState()
        initExternalState()
    }

    fun addObserver() {
        TUIRoomEngine.sharedInstance().addObserver(roomEngineObserver)
        TUIRoomEngine.sharedInstance().trtcCloud.addListener(trtcCloudListener)
        userStore.addObserver()
    }

    fun removeObserver() {
        TUIRoomEngine.sharedInstance().removeObserver(roomEngineObserver)
        TUIRoomEngine.sharedInstance().trtcCloud.removeListener(trtcCloudListener)
        userStore.removeObserver()
    }

    fun destroy() {
        removeObserver()
        mediaStore.destroy()
        anchorBattleStore.destroy()
        listenerList.clear()
    }

    fun getLiveAudienceStore(): LiveAudienceStore = liveAudienceStore
    fun getUserStore(): UserStore = userStore

    fun getMediaStore(): MediaStore = mediaStore

    fun getAnchorCoHostStore(): AnchorCoHostStore = anchorCoHostStore

    fun getAnchorBattleStore(): AnchorBattleStore = anchorBattleStore

    fun getState(): AnchorStoreState = state

    fun getCoHostState(): AnchorCoHostState = getAnchorCoHostStore().coHostState

    fun getBattleState(): AnchorBattleState = getAnchorBattleStore().battleState

    fun getUserState(): UserState = getUserStore().userState

    fun getMediaState(): MediaState = getMediaStore().mediaState

    fun setRoomId(roomId: String) {
        state.roomId = roomId
        logger.info("${hashCode()} setRoomId:[mRoomId=$roomId],mLiveObserver:${roomEngineObserver.hashCode()}]")
    }

    fun initCreateRoomState(liveInfo: LiveInfo) {
        logger.info("initCreateRoomState roomId [roomId: ${liveInfo.liveID}, roomName:${liveInfo.liveName}")
        state.roomId = liveInfo.liveID
        if (TextUtils.isEmpty(liveInfo.coverURL)) {
            liveInfo.coverURL = DEFAULT_COVER_URL
        }
        if (TextUtils.isEmpty(liveInfo.backgroundURL)) {
            liveInfo.backgroundURL = DEFAULT_BACKGROUND_URL
        }
    }

    fun updateRoomState(liveInfo: LiveInfo) {
        state.liveInfo = liveInfo
    }

    fun enablePipMode(enable: Boolean) {
        mediaStore.enablePipMode(enable)
    }

    fun setLiveStatisticsData(data: TUILiveListManager.LiveStatisticsData?) {
        if (data == null) {
            return
        }
        externalState.totalViewers = data.totalViewers.toLong()
        externalState.totalGiftUniqueSenders = data.totalUniqueGiftSenders.toLong()
        externalState.totalGiftCoins = data.totalGiftCoins.toLong()
        externalState.totalLikesReceived = data.totalLikesReceived.toLong()
        externalState.totalDuration=data.liveDuration.toLong()
        externalState.totalViewers=data.totalViewers.toLong()
        externalState.totalMessageSent=data.totalMessageCount.toLong()
    }

    private fun initExternalState() {
        externalState.totalDuration = 0
        externalState.totalViewers = 0
        externalState.totalMessageSent = 0
    }

    fun notifyPictureInPictureClick() {
        notifyAnchorViewListener { it.onClickFloatWindow() }
    }

    fun notifyLiveExit(reason: LiveEndedReason = LiveEndedReason.ENDED_BY_HOST) {
        if (reason == LiveEndedReason.ENDED_BY_SERVER) {
            val summary = liveSummaryStore.liveSummaryState.summaryData.value
            externalState.totalViewers = summary.totalViewers.toLong()
            externalState.totalDuration = summary.totalDuration
            externalState.totalMessageSent = summary.totalMessageSent.toLong()
            externalState.totalGiftCoins = summary.totalGiftCoins.toLong()
            externalState.totalGiftUniqueSenders = summary.totalGiftUniqueSenders.toLong()
            externalState.totalLikesReceived = summary.totalLikesReceived.toLong()
            externalState.liveEndedReason = reason
        }
        PIPPanelStore.sharedInstance().state.isAnchorStreaming = false
        notifyAnchorViewListener { it.onEndLiving(externalState) }
    }

    fun addAnchorViewListener(listener: AnchorViewListener) {
        listenerList.add(WeakReference(listener))
    }

    fun removeAnchorViewListener(listener: AnchorViewListener) {
        val toRemove = mutableListOf<WeakReference<AnchorViewListener>>()
        for (ref in listenerList) {
            if (ref.get() == listener) {
                toRemove.add(ref)
            }
        }
        listenerList.removeAll(toRemove)
    }

    private fun notifyAnchorViewListener(callback: (AnchorViewListener) -> Unit) {
        val toRemove = mutableListOf<WeakReference<AnchorViewListener>>()
        for (ref in listenerList) {
            val observer = ref.get()
            if (observer == null) {
                toRemove.add(ref)
            } else {
                callback(observer)
            }
        }
        listenerList.removeAll(toRemove)
    }

    private fun onSeatLockStateChanged(seatList: List<TUIRoomDefine.SeatFullInfo>) {
        val seatInfoMap = hashMapOf<String, TUIRoomDefine.SeatFullInfo>()
        for (seatInfo in seatList) {
            if (TextUtils.isEmpty(seatInfo.userId)) {
                continue
            }

            seatInfoMap[seatInfo.userId] = seatInfo
            val lockAudioUsers = state.lockAudioUserList.value
            if (seatInfo.userMicrophoneStatus == TUIRoomDefine.DeviceStatus.CLOSED_BY_ADMIN) {
                lockAudioUsers.add(seatInfo.userId)
            } else {
                lockAudioUsers.remove(seatInfo.userId)
            }
            _lockAudioUserList.update { lockAudioUsers }

            val lockVideoUsers = state.lockVideoUserList.value
            if (seatInfo.userCameraStatus == TUIRoomDefine.DeviceStatus.CLOSED_BY_ADMIN) {
                lockVideoUsers.add(seatInfo.userId)
            } else {
                lockVideoUsers.remove(seatInfo.userId)
            }
            _lockVideoUserList.update { lockVideoUsers }
        }
    }
}