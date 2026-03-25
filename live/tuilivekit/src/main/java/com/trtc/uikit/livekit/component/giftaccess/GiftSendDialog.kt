package com.trtc.uikit.livekit.component.giftaccess

import android.content.Context
import android.os.Bundle
import android.view.View
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.component.gift.GiftListView
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover

class GiftSendDialog(
    context: Context,
    val roomId: String,
    val ownerId: String,
    val ownerName: String,
    val ownerAvatarUrl: String
) : AtomicPopover(context) {

    private fun init() {
        val mGiftListView = findViewById<GiftListView>(R.id.gift_list_view)
        mGiftListView?.let { giftListView ->
            giftListView.init(roomId)
        }
        setCanceledOnTouchOutside(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent(View.inflate(context, R.layout.gift_layout_send_dialog_panel, null))
        init()
    }
}