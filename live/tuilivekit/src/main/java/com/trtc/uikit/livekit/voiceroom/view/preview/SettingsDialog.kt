package com.trtc.uikit.livekit.voiceroom.view.preview

import android.content.Context
import android.view.View
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.voiceroom.manager.VoiceRoomManager
import com.trtc.uikit.livekit.voiceroom.view.basic.Switch
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.live.TakeSeatMode

class SettingsDialog(
    context: Context,
    private val voiceRoomManager: VoiceRoomManager
) : AtomicPopover(context) {

    private var switch: Switch

    init {
        val rootView = View.inflate(context, R.layout.livekit_voiceroom_preview_settings, null)
        setContent(rootView)
        rootView.findViewById<View>(R.id.iv_back).setOnClickListener { dismiss() }
        switch = rootView.findViewById<Switch>(R.id.switch_need_request).apply {
            isChecked = voiceRoomManager.prepareStore.prepareState.liveInfo.value.seatMode == TakeSeatMode.APPLY
            onCheckedChangeListener = { enable -> onSeatModeClicked(enable) }
        }
    }

    private fun onSeatModeClicked(enable: Boolean) {
        val seatMode = if (enable) {
            TakeSeatMode.APPLY
        } else {
            TakeSeatMode.FREE
        }
        updateSeatMode(seatMode)
        voiceRoomManager.prepareStore.updateSeatMode(seatMode)
    }

    private fun updateSeatMode(seatMode: TakeSeatMode) {
        switch.isChecked = seatMode == TakeSeatMode.APPLY
    }
}