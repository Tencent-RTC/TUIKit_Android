package com.trtc.uikit.roomkit.inroom.features.device.camerabutton

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.base.util.RoomDevice
import com.trtc.uikit.roomkit.base.util.RoomLocalMediaUtils
import com.trtc.uikit.roomkit.inroom.common.toolbarbutton.RoomToolbarButton
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CameraButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RoomToolbarButton(context, attrs, defStyleAttr) {

    private val deviceStore = DeviceStore.shared()
    private var roomID: String = ""
    private var subscribeJob: Job? = null
    private var allCamDisabled = false
    private var selfRole: ParticipantRole? = null

    init {
        icon = context.getDrawable(R.drawable.roomkit_icon_camera_on)
        roomDisabledIcon = context.getDrawable(R.drawable.roomkit_icon_camera_off)
        setOnClickListener { toggle() }
    }

    fun bind(roomID: String) {
        this.roomID = roomID
        if (isAttachedToWindow) subscribe()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (roomID.isNotBlank()) subscribe()
        refreshFault()
    }

    private fun subscribe() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                deviceStore.deviceState.cameraStatus.collect { status -> render(status) }
            }
            launch {
                RoomStore.shared().state.currentRoom.collect { room ->
                    allCamDisabled = room?.isAllCameraDisabled == true
                    updateRoomDisabled()
                }
            }
            launch {
                RoomParticipantStore.create(roomID).state.localParticipant.collect { local ->
                    selfRole = local?.role
                    updateRoomDisabled()
                }
            }
        }
        refreshFault()
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) refreshFault()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) refreshFault()
    }

    private fun updateRoomDisabled() {
        val role = selfRole
        isRoomDisabled = allCamDisabled && role != null
            && role != ParticipantRole.OWNER && role != ParticipantRole.ADMIN
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun render(status: DeviceStatus) {
        icon = context.getDrawable(
            if (status == DeviceStatus.ON) {
                R.drawable.roomkit_icon_camera_on
            } else {
                R.drawable.roomkit_icon_camera_off
            },
        )
    }

    private fun toggle() {
        if (isRoomDisabled) {
            RoomToast.show(context, context.getString(R.string.roomkit_device_all_cam_disabled))
            return
        }
        if (deviceStore.deviceState.cameraStatus.value == DeviceStatus.ON) {
            deviceStore.closeLocalCamera()
        } else {
            hasFault = false
            RoomLocalMediaUtils.openCamera(context)
        }
    }

    private fun refreshFault() {
        hasFault = RoomLocalMediaUtils.isPermissionDenied(context, RoomDevice.CAMERA)
    }
}
