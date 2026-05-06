package com.trtc.uikit.livekit.features.audienceview.store

import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.tencent.cloud.tuikit.engine.room.TUIRoomObserver
import com.tencent.qcloud.tuicore.TUIConstants
import com.tencent.qcloud.tuicore.TUICore
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.EVENT_KEY_LIVE_KIT
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_DESTROY_LIVE_VIEW
import com.trtc.uikit.livekit.common.LiveKitLogger
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.live.BattleState
import io.trtc.tuikit.atomicxcore.api.live.BattleStore
import io.trtc.tuikit.atomicxcore.api.live.CoGuestState
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostState
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListListener
import io.trtc.tuikit.atomicxcore.api.live.LiveListState
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatState
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

data class AudienceState(
    var liveInfo: MutableStateFlow<LiveInfo>
)

class AudienceStore(liveID: String) {

    companion object {
        private val LOGGER = LiveKitLogger.getLiveStreamLogger("AudienceStore")
    }

    private val liveListStore: LiveListStore = LiveListStore.Companion.shared()
    private val deviceStore: DeviceStore = DeviceStore.Companion.shared()
    private val roomEngine: TUIRoomEngine = TUIRoomEngine.sharedInstance()
    private val liveSeatStore: LiveSeatStore
    private val audienceStore: LiveAudienceStore
    private val coGuestStore: CoGuestStore
    private val coHostStore: CoHostStore
    private val battleStore: BattleStore
    private val imStore: IMStore
    private val mediaStore: MediaStore
    private val viewStore: ViewStore
    private val roomEngineObserver: TUIRoomObserver
    private val liveListListener: LiveListListener
    private val audienceViewListeners: MutableList<WeakReference<AudienceStoreListener>> = CopyOnWriteArrayList()
    private val _liveInfo = MutableStateFlow(LiveInfo(seatTemplate = SeatLayoutTemplate.VideoDynamicGrid9Seats))

    val audienceState = AudienceState(_liveInfo)

    init {
        liveSeatStore = LiveSeatStore.Companion.create(liveID)
        coGuestStore = CoGuestStore.Companion.create(liveID)
        coHostStore = CoHostStore.Companion.create(liveID)
        battleStore = BattleStore.Companion.create(liveID)
        this@AudienceStore.audienceStore = LiveAudienceStore.Companion.create(liveID)
        viewStore = ViewStore(liveID)
        imStore = IMStore()
        mediaStore = MediaStore(liveID)
        roomEngineObserver = createRoomEngineObserver()
        liveListListener = createLiveListListener()
    }

    fun addObserver() {
        roomEngine.addObserver(roomEngineObserver)
        liveListStore.addLiveListListener(liveListListener)
    }

    fun removeObserver() {
        roomEngine.removeObserver(roomEngineObserver)
        liveListStore.removeLiveListListener(liveListListener)
    }

    fun addAudienceViewListener(listener: AudienceStoreListener) {
        audienceViewListeners.add(WeakReference(listener))
    }

    fun removeAudienceViewListener(listener: AudienceStoreListener) {
        audienceViewListeners.removeAll { it.get() == listener || it.get() == null }
    }

    fun destroy() {
        removeObserver()
        audienceViewListeners.clear()
        mediaStore.destroy()
    }


    fun getLiveListStore(): LiveListStore {
        return liveListStore
    }

    fun getDeviceStore(): DeviceStore {
        return deviceStore
    }

    fun getLiveSeatStore(): LiveSeatStore {
        return liveSeatStore
    }

    fun getCoGuestStore(): CoGuestStore {
        return coGuestStore
    }

    fun getCoHostStore(): CoHostStore {
        return coHostStore
    }

    fun getBattleStore(): BattleStore {
        return battleStore
    }

    fun getLiveAudienceStore(): LiveAudienceStore {
        return this@AudienceStore.audienceStore
    }

    fun getIMStore(): IMStore {
        return imStore
    }

    fun getViewStore(): ViewStore {
        return viewStore
    }

    fun getMediaStore(): MediaStore {
        return mediaStore
    }

    fun getLiveListState(): LiveListState {
        return liveListStore.liveState
    }

    fun getLiveSeatState(): LiveSeatState {
        return liveSeatStore.liveSeatState
    }

    fun getCoGuestState(): CoGuestState {
        return coGuestStore.coGuestState
    }

    fun getCoHostState(): CoHostState {
        return coHostStore.coHostState
    }

    fun getBattleState(): BattleState {
        return battleStore.battleState
    }

    fun getIMState(): IMState {
        return imStore.imState
    }

    fun getViewState(): ViewState {
        return viewStore.viewState
    }

    fun getMediaState(): MediaState {
        return mediaStore.mediaState
    }

    fun updateLiveInfo(liveInfo: LiveInfo) {
        _liveInfo.update { liveInfo }
    }

    fun notifyOnRoomDismissed(roomId: String) {
        val ownerInfo = audienceState.liveInfo.value.liveOwner
        notifyAudienceViewListeners { listener ->
            listener.onLiveEnded(roomId, ownerInfo.userName, ownerInfo.avatarURL)
        }
        updateLiveInfo(LiveInfo(seatTemplate = SeatLayoutTemplate.VideoDynamicGrid9Seats))
    }

    fun notifyPictureInPictureClick() {
        notifyAudienceViewListeners { listener -> listener.onPictureInPictureClick() }
    }

    private fun notifyAudienceViewListeners(action: (AudienceStoreListener) -> Unit) {
        val listenersToRemove = ArrayList<WeakReference<AudienceStoreListener>>()
        for (ref in audienceViewListeners) {
            val listener = ref.get()
            if (listener == null) {
                listenersToRemove.add(ref)
            } else {
                action(listener)
            }
        }
        audienceViewListeners.removeAll(listenersToRemove)
    }

    private fun createRoomEngineObserver(): TUIRoomObserver {
        return object : TUIRoomObserver() {
            override fun onKickedOffLine(message: String) {
                LOGGER.info("onKickedOffLine:[message:$message]")
                ContextProvider.getApplicationContext()?.apply {
                    AtomicToast.show(this, message, AtomicToast.Style.INFO)
                }
                TUICore.notifyEvent(
                    TUIConstants.Privacy.EVENT_ROOM_STATE_CHANGED,
                    TUIConstants.Privacy.EVENT_SUB_KEY_ROOM_STATE_STOP,
                    null
                )
                TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_DESTROY_LIVE_VIEW, null)
            }
        }
    }

    private fun createLiveListListener(): LiveListListener {
        return object : LiveListListener(){
            override fun onLiveEnded(liveID: String, reason: LiveEndedReason, message: String) {
                LOGGER.info("onLiveEnded:[liveID:$liveID]")
                ContextProvider.getApplicationContext()?.apply {
                    AtomicToast.show(
                        this,
                        this.resources.getString(R.string.common_room_destroy),
                        AtomicToast.Style.INFO
                    )
                }
                TUICore.notifyEvent(
                    TUIConstants.Privacy.EVENT_ROOM_STATE_CHANGED,
                    TUIConstants.Privacy.EVENT_SUB_KEY_ROOM_STATE_STOP,
                    null
                )
                notifyOnRoomDismissed(liveID)
            }
        }
    }

    interface AudienceStoreListener {
        fun onLiveEnded(roomId: String, ownerName: String, ownerAvatarUrl: String)
        fun onPictureInPictureClick() {}
    }
}