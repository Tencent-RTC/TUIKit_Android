package com.trtc.uikit.roomkit.inroom.features.device.micbutton

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.AttributeSet
import android.view.LayoutInflater
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
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MicButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val deviceStore = DeviceStore.shared()
    private var roomID: String = ""
    private var subscribeJob: Job? = null
    private var allMicDisabled = false
    private var selfRole: ParticipantRole? = null

    private val iconView: AppCompatImageView
    private val micLevelView: RoomMicLevelView
    private val permissionBadge: AppCompatImageView

    private var micOn = true
        set(value) {
            if (field == value) return
            field = value
            applyIcon()
        }

    private var volume: Int? = null
        set(value) {
            if (field == value) return
            field = value
            if (value == null) {
                micLevelView.visibility = GONE
                micLevelView.reset()
            } else {
                micLevelView.visibility = VISIBLE
                micLevelView.setVolume(value)
            }
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
        LayoutInflater.from(context).inflate(R.layout.roomkit_mic_button, this, true)
        iconView = findViewById(R.id.icon)
        micLevelView = findViewById(R.id.mic_level)
        permissionBadge = findViewById(R.id.permission_badge)
        setOnClickListener { toggle() }
    }

    private fun applyIcon() {
        if (micOn) {
            iconView.setImageResource(R.drawable.roomkit_icon_microphone_on)
            alpha = 1f
            return
        }
        iconView.setImageResource(R.drawable.roomkit_icon_microphone_off)
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
        if (visibility == VISIBLE) refreshPermission()
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

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun subscribe() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                RoomParticipantStore.create(roomID).state.localParticipant.collect { local ->
                    micOn = local?.microphoneStatus == DeviceStatus.ON
                }
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
                        volume = if (micOn) speaking[LOCAL_VOLUME_KEY] ?: 0 else null
                    }
            }
        }
        refreshPermission()
    }

    private fun updateRoomDisabled() {
        val role = selfRole
        roomDisabled = allMicDisabled && role != null
                && role != ParticipantRole.OWNER && role != ParticipantRole.ADMIN
    }

    private fun refreshPermission() {
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun toggle() {
        CoroutineScope(Dispatchers.Main).launch {
            val deviceStore = DeviceStore.shared()
            if (micOn) {
                RoomParticipantStore.create(roomID).muteMicrophone()
                return@launch
            }
            if (roomDisabled) {
                RoomToast.show(context, context.getString(R.string.roomkit_device_all_mic_disabled))
                return@launch
            }
            val granted = RoomDevicePermission.requestMicrophonePermission(context as Activity)
            if (granted) {
                deviceStore.openLocalMicrophone(object : CompletionHandler {
                    override fun onSuccess() {
                        RoomForegroundService.start(context)
                        RoomParticipantStore.create(roomID).unmuteMicrophone(object : CompletionHandler {
                            override fun onSuccess() {}
                            override fun onFailure(code: Int, desc: String) {
                                ErrorLocalized.showError(context, code, desc)
                            }
                        })
                    }

                    override fun onFailure(code: Int, desc: String) {
                        ErrorLocalized.showError(context, code, desc)
                    }
                })
            } else {
                RoomToast.show(context, context.getString(R.string.roomkit_device_mic_no_permission))
            }
        }
    }

    private companion object {
        const val PRESSED_SCALE = 0.92f
        const val PRESSED_DURATION = 80
        const val ROOM_DISABLED_ALPHA = 0.45f
        const val LOCAL_VOLUME_KEY = ""
    }
}
