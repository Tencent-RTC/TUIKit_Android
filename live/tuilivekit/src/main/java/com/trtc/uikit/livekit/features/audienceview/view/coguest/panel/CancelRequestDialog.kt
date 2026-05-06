package com.trtc.uikit.livekit.features.audienceview.view.coguest.panel

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.completionHandler
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore

class CancelRequestDialog(
    context: Context,
    private val audienceStore: AudienceStore
) : AtomicPopover(context), AudienceStore.AudienceStoreListener {

    init {
        initView()
    }

    @SuppressLint("InflateParams")
    private fun initView() {
        val view =
            LayoutInflater.from(context).inflate(R.layout.livekit_dialog_co_guest_cancel, null)
        val textCancelCoGuest = view.findViewById<TextView>(R.id.tv_cancel_co_guest)
        val textDismiss = view.findViewById<TextView>(R.id.tv_dismiss)
        textCancelCoGuest.setOnClickListener { v ->
            if (!v.isEnabled) {
                return@setOnClickListener
            }
            v.isEnabled = false
            audienceStore.getCoGuestStore().cancelApplication(completionHandler {
                onSuccess {
                    audienceStore.getViewStore().updateTakeSeatState(false)
                }
                onError { code, _ ->
                    ErrorLocalized.onError(code)
                }
            })
            dismiss()
        }

        textDismiss.setOnClickListener {
            dismiss()
        }

        setContent(view)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        audienceStore.addAudienceViewListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        audienceStore.removeAudienceViewListener(this)
    }

    override fun onLiveEnded(roomId: String, ownerName: String, ownerAvatarUrl: String) {
        dismiss()
    }
}
