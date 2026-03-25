package com.trtc.uikit.livekit.features.audiencecontainer.store.observer

import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine
import com.tencent.cloud.tuikit.engine.room.TUIRoomObserver
import com.tencent.qcloud.tuicore.TUIConstants
import com.tencent.qcloud.tuicore.TUICore
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.EVENT_KEY_LIVE_KIT
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_DESTROY_LIVE_VIEW
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.audiencecontainer.store.AudienceStore
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import java.lang.ref.WeakReference

class RoomEngineObserver(store: AudienceStore) : TUIRoomObserver() {

    companion object {
        private val LOGGER = LiveKitLogger.getLiveStreamLogger("RoomEngineObserver")
    }

    private val liveStoreRef: WeakReference<AudienceStore> = WeakReference(store)

    override fun onRoomDismissed(roomId: String, reason: TUIRoomDefine.RoomDismissedReason) {
        LOGGER.info("${hashCode()} onRoomDismissed:[roomId$roomId]")
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
        val manager = liveStoreRef.get()
        manager?.notifyOnRoomDismissed(roomId)
    }

    override fun onKickedOffLine(message: String) {
        LOGGER.info("${hashCode()} onKickedOffLine:[message:$message]")
        ContextProvider.getApplicationContext()?.apply {
            AtomicToast.show(this, message, AtomicToast.Style.INFO)
        }
        val manager = liveStoreRef.get()
        TUICore.notifyEvent(
            TUIConstants.Privacy.EVENT_ROOM_STATE_CHANGED,
            TUIConstants.Privacy.EVENT_SUB_KEY_ROOM_STATE_STOP,
            null
        )
        if (manager != null) {
            TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_DESTROY_LIVE_VIEW, null)
        } else {
            LOGGER.error("${hashCode()} onKickedOffLine: AudienceStore is null")
        }
    }
}
