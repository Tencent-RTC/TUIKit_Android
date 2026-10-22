package com.trtc.uikit.roomkit.inroom.features.device.speakerbutton

import android.content.Context
import android.util.AttributeSet
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.util.RoomAudioOutput
import com.trtc.uikit.roomkit.base.util.RoomLocalMediaUtils
import com.trtc.uikit.roomkit.inroom.common.toolbarbutton.RoomToolbarButton
import com.trtc.uikit.roomkit.inroom.features.device.audiodevicemenu.AudioRouteSheet
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SpeakerButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RoomToolbarButton(context, attrs, defStyleAttr) {

    private val deviceStore = DeviceStore.shared()
    private var subscribeJob: Job? = null
    private var routeSheet: AudioRouteSheet? = null

    init {
        updateIcon(RoomLocalMediaUtils.currentAudioOutput())
        setOnClickListener { showRouteSheet() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                deviceStore.deviceState.currentAudioRoute.collect {
                    updateIcon(RoomLocalMediaUtils.currentAudioOutput())
                }
            }
            launch {
                RoomLocalMediaUtils.isAudioOutputMuted.collect {
                    updateIcon(RoomLocalMediaUtils.currentAudioOutput())
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    private fun updateIcon(output: RoomAudioOutput) {
        icon = context.getDrawable(
            when (output) {
                RoomAudioOutput.SPEAKER -> R.drawable.roomkit_icon_speaker
                RoomAudioOutput.EARPIECE -> R.drawable.roomkit_icon_earpiece
            },
        )
    }

    private fun showRouteSheet() {
        if (routeSheet != null) return
        val sheet = AudioRouteSheet(context, RoomLocalMediaUtils.currentAudioOutput())
        sheet.onSelect = { output ->
            routeSheet = null
            RoomLocalMediaUtils.setAudioOutput(output)
        }
        sheet.onDismiss = { routeSheet = null }
        routeSheet = sheet
        sheet.show()
    }
}
