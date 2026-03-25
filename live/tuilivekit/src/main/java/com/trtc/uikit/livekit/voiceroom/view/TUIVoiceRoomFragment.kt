package com.trtc.uikit.livekit.voiceroom.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.interfaces.ITUINotification
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.DEFAULT_MAX_SEAT_COUNT
import com.trtc.uikit.livekit.common.EVENT_KEY_LIVE_KIT
import com.trtc.uikit.livekit.common.EVENT_PARAMS_IS_LINKING
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_CLOSE_VOICE_ROOM
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_FINISH_ACTIVITY
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_LINK_STATUS_CHANGE
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.voiceroom.manager.VoiceRoomManager
import com.trtc.uikit.livekit.voiceroom.store.LiveStatus
import io.trtc.tuikit.atomicx.common.foregroundservice.AudioForegroundService
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import io.trtc.tuikit.atomicxcore.api.device.AudioEffectStore
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.Serializable

class TUIVoiceRoomFragment(
    private val roomId: String,
    private val roomBehavior: RoomBehavior,
    private val roomParams: RoomParams
) : Fragment(), ITUINotification {

    private lateinit var voiceRoomRootView: VoiceRoomRootView
    private lateinit var voiceRoomManager: VoiceRoomManager
    private val liveSeatStore = LiveSeatStore.create(roomId)
    private var subscribeStateJob: Job? = null
    private var isLinking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceRoomManager = VoiceRoomManager()
        voiceRoomManager.prepareStore.updateLiveID(roomId)
        addObserver()
        TUICore.registerEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_FINISH_ACTIVITY, this)
        startForegroundService()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val contentView =
            inflater.inflate(R.layout.livekit_voiceroom_fragment_main, container, false)
        voiceRoomRootView = contentView.findViewById(R.id.root_view)
        voiceRoomRootView.init(voiceRoomManager, roomBehavior, roomParams)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback
        )
        return contentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        voiceRoomRootView.post {
            voiceRoomRootView.updateStatus(VoiceRoomRootView.VoiceRoomViewStatus.START_DISPLAY)
        }
    }

    override fun onResume() {
        super.onResume()
        voiceRoomRootView.post {
            voiceRoomRootView.updateStatus(VoiceRoomRootView.VoiceRoomViewStatus.DISPLAY_COMPLETE)
        }
    }

    override fun onPause() {
        super.onPause()
        voiceRoomRootView.post {
            voiceRoomRootView.updateStatus(VoiceRoomRootView.VoiceRoomViewStatus.END_DISPLAY)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        backPressedCallback.remove()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeObserver()
        TUICore.unRegisterEvent(this)
        voiceRoomManager.destroy()
        AudioEffectStore.shared().reset()
        DeviceStore.shared().reset()
        stopForegroundService()
    }

    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                liveSeatStore.liveSeatState.seatList.collect {
                    onLinkStatusChanged(!liveSeatStore.liveSeatState.seatList.value.none { it.userInfo.userID == TUIRoomEngine.getSelfInfo().userId })
                }
            }
        }
    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun onLinkStatusChanged(isLinking: Boolean) {
        if (this.isLinking != isLinking) {
            this.isLinking = isLinking
            val params = HashMap<String, Any>()
            params[EVENT_PARAMS_IS_LINKING] = isLinking
            TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_LINK_STATUS_CHANGE, params)
        }
    }

    override fun onNotifyEvent(key: String, subKey: String, param: Map<String, Any>?) {
        if (EVENT_SUB_KEY_FINISH_ACTIVITY == subKey) {
            if (param == null) {
                requireActivity().finish()
            } else {
                val roomId = param["roomId"] as? String
                if (roomId != null && roomId == this@TUIVoiceRoomFragment.roomId) {
                    requireActivity().finish()
                }
            }
        }
    }

    class RoomParams : Serializable {
        var roomName: String = ""
        var maxSeatCount: Int = DEFAULT_MAX_SEAT_COUNT
        var seatMode: TUIRoomDefine.SeatMode = TUIRoomDefine.SeatMode.FREE_TO_TAKE
    }

    enum class RoomBehavior {
        AUTO_CREATE,
        PREPARE_CREATE,
        JOIN
    }

    private fun startForegroundService() {
        LOGGER.info("startForegroundService")
        ContextProvider.getApplicationContext()?.apply {
            AudioForegroundService.start(
                this,
                this.getString(this.applicationInfo.labelRes),
                this.getString(R.string.common_app_running),
                0
            )
        }
    }

    private fun stopForegroundService() {
        LOGGER.info("stopForegroundService")
        ContextProvider.getApplicationContext()?.apply {
            AudioForegroundService.stop(this)
        }
    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val liveStatus = voiceRoomManager.prepareStore.prepareState.liveStatus.value
            if (LiveStatus.PUSHING == liveStatus || LiveStatus.PLAYING == liveStatus) {
                TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_CLOSE_VOICE_ROOM, null)
            } else {
                requireActivity().finish()
            }
        }
    }

    companion object {
        private val LOGGER = LiveKitLogger.getVoiceRoomLogger("TUIVoiceRoomFragment")
    }
}