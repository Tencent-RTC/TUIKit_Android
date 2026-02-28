package com.trtc.uikit.livekit.features.audiencecontainer.store

import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.trtc.uikit.livekit.features.audiencecontainer.store.observer.AudienceContainerViewListenerList
import com.trtc.uikit.livekit.features.audiencecontainer.store.observer.AudienceViewListenerList
import com.trtc.uikit.livekit.features.audiencecontainer.store.observer.RoomEngineObserver
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.live.BattleState
import io.trtc.tuikit.atomicxcore.api.live.BattleStore
import io.trtc.tuikit.atomicxcore.api.live.CoGuestState
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostState
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListState
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatState
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class AudienceState(
    var liveInfo: MutableStateFlow<LiveInfo>
)

class AudienceStore(liveID: String) {
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
    private val roomEngineObserver: RoomEngineObserver
    private val audienceViewListenerList: AudienceViewListenerList
    private var audienceContainerViewListenerList: AudienceContainerViewListenerList? = null
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
        roomEngineObserver = RoomEngineObserver(this)
        audienceViewListenerList = AudienceViewListenerList()
    }

    fun setAudienceContainerViewListenerList(viewListenerList: AudienceContainerViewListenerList) {
        audienceContainerViewListenerList = viewListenerList
    }

    fun addObserver() {
        roomEngine.addObserver(roomEngineObserver)
    }

    fun removeObserver() {
        roomEngine.removeObserver(roomEngineObserver)
        audienceViewListenerList.clearListeners()
    }

    fun addAudienceViewListener(listener: AudienceViewListener) {
        audienceViewListenerList.addListener(listener)
    }

    fun removeAudienceViewListener(listener: AudienceViewListener) {
        audienceViewListenerList.removeListener(listener)
    }

    fun destroy() {
        removeObserver()
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
        audienceViewListenerList.notifyListeners { listener -> listener.onRoomDismissed(roomId) }
        audienceContainerViewListenerList?.let { listenerList ->
            val ownerInfo = audienceState.liveInfo.value.liveOwner
            listenerList.notifyListeners { listener ->
                listener.onLiveEnded(
                    roomId,
                    ownerInfo.userName,
                    ownerInfo.avatarURL
                )
            }
            updateLiveInfo(LiveInfo(seatTemplate = SeatLayoutTemplate.VideoDynamicGrid9Seats))
        }
    }

    fun notifyPictureInPictureClick() {
        audienceContainerViewListenerList?.notifyListeners { listener ->
            listener.onPictureInPictureClick()
        }
    }

    interface AudienceViewListener {
        fun onRoomDismissed(roomId: String)
    }
}