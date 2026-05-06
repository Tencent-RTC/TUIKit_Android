package com.trtc.uikit.livekit.component.beauty.tebeauty

import android.content.Context
import android.os.Bundle
import android.view.View
import com.trtc.uikit.livekit.component.beauty.basicbeauty.BeautyListPanel
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover

class BeautyPanelDialog(
    private val context: Context
) : AtomicPopover(context) {

    init {
        setShowMask(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val beautyView: View = if (TEBeautyManager.isSupportTEBeauty()) {
            TEBeautyView(context)
        } else {
            BeautyListPanel(context)
        }
        setContent(beautyView)
    }
}