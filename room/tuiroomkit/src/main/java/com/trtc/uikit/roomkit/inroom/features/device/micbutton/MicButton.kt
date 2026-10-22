package com.trtc.uikit.roomkit.inroom.features.device.micbutton

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.FailureHandler
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.base.util.RoomDevice
import com.trtc.uikit.roomkit.base.util.RoomLocalMediaUtils
import com.trtc.uikit.roomkit.inroom.common.miclevel.RoomMicLevelView
import com.trtc.uikit.roomkit.inroom.common.toolbarbutton.RoomToolbarButton
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MicButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RoomToolbarButton(context, attrs, defStyleAttr) {

    private val deviceStore = DeviceStore.shared()
    private var roomID: String = ""
    private var subscribeJob: Job? = null
    private var allMicDisabled = false
    private var selfRole: ParticipantRole? = null

    init {
        icon = context.getDrawable(R.drawable.roomkit_icon_microphone_on)
        roomDisabledIcon = context.getDrawable(R.drawable.roomkit_icon_microphone_off)
        setOnClickListener { toggle() }
    }

    fun bind(roomID: String) {
        this.roomID = roomID
        if (isAttachedToWindow) subscribe()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (roomID.isNotBlank()) subscribe()
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) refreshFault()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) refreshFault()
    }

    private fun subscribe() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                deviceStore.deviceState.microphoneStatus.collect { status -> render(status) }
            }
            launch {
                RoomStore.shared().state.currentRoom.collect { room ->
                    allMicDisabled = room?.isAllMicrophoneDisabled == true
                    updateRoomDisabled()
                }
            }
            launch {
                RoomParticipantStore.create(roomID).state.localParticipant.collect { local ->
                    selfRole = local?.role
                    updateRoomDisabled()
                }
            }
            launch {
                RoomParticipantStore.create(roomID).state.speakingUsers
                    .sample(RoomMicLevelView.UPDATE_INTERVAL_MS.milliseconds)
                    .collect { speaking ->
                    micVolume = if (deviceStore.deviceState.microphoneStatus.value == DeviceStatus.ON) {
                        val localUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
                        speaking[localUserID] ?: 0
                    } else {
                        null
                    }
                }
            }
        }
        refreshFault()
    }

    private fun updateRoomDisabled() {
        val role = selfRole
        isRoomDisabled = allMicDisabled && role != null
            && role != ParticipantRole.OWNER && role != ParticipantRole.ADMIN
    }

    private fun render(status: DeviceStatus) {
        icon = context.getDrawable(
            if (status == DeviceStatus.ON) {
                R.drawable.roomkit_icon_microphone_on
            } else {
                R.drawable.roomkit_icon_microphone_off
            },
        )
    }

    private fun toggle() {
        if (isRoomDisabled) {
            RoomToast.show(context, context.getString(R.string.roomkit_device_all_mic_disabled))
            return
        }
        if (deviceStore.deviceState.microphoneStatus.value == DeviceStatus.ON) {
            deviceStore.closeLocalMicrophone()
        } else {
            hasFault = false
            RoomLocalMediaUtils.openMicrophone(context, object : CompletionHandler {
                override fun onSuccess() { unmuteOnServer() }
                override fun onFailure(code: Int, desc: String) {
                    ErrorLocalized.showError(context, code, desc)
                }
            })
        }
    }

    private fun unmuteOnServer() {
        if (roomID.isBlank()) return
        RoomParticipantStore.create(roomID).unmuteMicrophone(FailureHandler(context))
    }

    private fun refreshFault() {
        hasFault = RoomLocalMediaUtils.isPermissionDenied(context, RoomDevice.MICROPHONE)
    }
}
