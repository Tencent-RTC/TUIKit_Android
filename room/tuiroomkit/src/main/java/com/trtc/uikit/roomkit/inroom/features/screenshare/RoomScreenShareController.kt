package com.trtc.uikit.roomkit.inroom.features.screenshare

import com.trtc.uikit.roomkit.base.util.getDisplayName
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object RoomScreenShareController {

    private val deviceStore = DeviceStore.shared()

    private val isSharingFlow = MutableStateFlow(false)
    val isSharing: StateFlow<Boolean> = isSharingFlow.asStateFlow()

    private val sharerNameFlow = MutableStateFlow<String?>(null)
    val sharerName: StateFlow<String?> = sharerNameFlow.asStateFlow()

    private var boundRoomID: String? = null
    private var bindJob: Job? = null

    fun bind(roomID: String) {
        if (boundRoomID == roomID) return
        bindJob?.cancel()
        boundRoomID = roomID
        val state = RoomParticipantStore.create(roomID).state
        bindJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                state.localParticipant.collect { local ->
                    isSharingFlow.value = local?.screenShareStatus == DeviceStatus.ON
                }
            }
            launch {
                state.participantWithScreen.collect { sharer ->
                    sharerNameFlow.value = sharer?.takeIf { it.userID.isNotBlank() }?.getDisplayName()
                }
            }
        }
    }

    fun unbind() {
        bindJob?.cancel()
        bindJob = null
        boundRoomID = null
        isSharingFlow.value = false
        sharerNameFlow.value = null
    }

    fun start() {
        deviceStore.startScreenShare()
    }

    fun stop() {
        deviceStore.stopScreenShare()
    }
}
