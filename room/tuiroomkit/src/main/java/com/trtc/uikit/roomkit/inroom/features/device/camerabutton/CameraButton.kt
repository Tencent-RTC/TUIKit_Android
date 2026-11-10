package com.trtc.uikit.roomkit.inroom.features.device.camerabutton

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.service.RoomForegroundService
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.base.util.RoomDevicePermission
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
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
) : FrameLayout(context, attrs, defStyleAttr) {

    private var roomID: String = ""
    private var subscribeJob: Job? = null
    private var allCamDisabled = false
    private var selfRole: ParticipantRole? = null

    private val iconView: AppCompatImageView
    private val permissionBadge: AppCompatImageView

    private var cameraOn = false
        set(value) {
            if (field == value) return
            field = value
            applyIcon()
        }

    private var roomDisabled = false
        set(value) {
            if (field == value) return
            field = value
            applyIcon()
            applyPermissionBadgeVisibility()
        }

    private var hasPermission: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            applyPermissionBadgeVisibility()
        }

    init {
        background = AppCompatResources.getDrawable(context, R.drawable.roomkit_bg_toolbar_button)
        LayoutInflater.from(context).inflate(R.layout.roomkit_camera_button, this, true)
        iconView = findViewById(R.id.icon)
        permissionBadge = findViewById(R.id.permission_badge)
        setOnClickListener { toggle() }
    }

    private fun applyIcon() {
        if (cameraOn) {
            iconView.setImageResource(R.drawable.roomkit_icon_camera_on)
            alpha = 1f
            return
        }
        iconView.setImageResource(R.drawable.roomkit_icon_camera_off)
        alpha = if (roomDisabled) ROOM_DISABLED_ALPHA else 1f
    }

    private fun applyPermissionBadgeVisibility() {
        permissionBadge.visibility = if (!hasPermission && !roomDisabled) VISIBLE else GONE
    }

    fun init(roomID: String) {
        this.roomID = roomID
        if (isAttachedToWindow) subscribe()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (roomID.isNotBlank()) subscribe()
        refreshPermission()
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) refreshPermission()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) refreshPermission()
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val scale = if (pressed) PRESSED_SCALE else 1f
        animate().setDuration(PRESSED_DURATION.toLong()).scaleX(scale).scaleY(scale)
    }

    private fun subscribe() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                RoomParticipantStore.create(roomID).state.localParticipant.collect { local ->
                    cameraOn = local?.cameraStatus == DeviceStatus.ON
                }
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
        refreshPermission()
    }

    private fun updateRoomDisabled() {
        val role = selfRole
        roomDisabled = allCamDisabled && role != null
                && role != ParticipantRole.OWNER && role != ParticipantRole.ADMIN
    }

    private fun refreshPermission() {
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun toggle() {
        CoroutineScope(Dispatchers.Main).launch {
            val deviceStore = DeviceStore.shared()
            if (cameraOn) {
                deviceStore.closeLocalCamera()
                return@launch
            }
            if (roomDisabled) {
                RoomToast.show(context, context.getString(R.string.roomkit_device_all_cam_disabled))
                return@launch
            }
            val granted = RoomDevicePermission.requestCameraPermission(context as Activity)
            if (granted) {
                val isFront = deviceStore.deviceState.isFrontCamera.value
                deviceStore.openLocalCamera(isFront, object : CompletionHandler {
                    override fun onSuccess() {
                        RoomForegroundService.start(context)
                    }

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.showError(context, code, desc)
                    }
                })
            } else {
                RoomToast.show(context, context.getString(R.string.roomkit_device_camera_no_permission))
            }
        }
    }

    private companion object {
        const val PRESSED_SCALE = 0.92f
        const val PRESSED_DURATION = 80
        const val ROOM_DISABLED_ALPHA = 0.45f
    }
}
