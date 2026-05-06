package com.trtc.uikit.livekit.features.audienceview.view.coguest.panel

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover

class StopCoGuestDialog(
    context: Context,
    private val audienceStore: AudienceStore,
) : AtomicPopover(context) {

    init {
        initView()
    }

    @SuppressLint("InflateParams")
    private fun initView() {
        val view = LayoutInflater.from(context).inflate(R.layout.livekit_dialog_co_guest_stop, null)
        val textStopCoGuest = view.findViewById<TextView>(R.id.tv_stop_co_guest)
        val textDismiss = view.findViewById<TextView>(R.id.tv_dismiss)
        textStopCoGuest.setOnClickListener {
            audienceStore.getCoGuestStore().disconnect(null)
            audienceStore.getViewStore()
                .updateTakeSeatState(false)
            dismiss()
        }

        textDismiss.setOnClickListener {
            dismiss()
        }

        setContent(view)
    }
}
