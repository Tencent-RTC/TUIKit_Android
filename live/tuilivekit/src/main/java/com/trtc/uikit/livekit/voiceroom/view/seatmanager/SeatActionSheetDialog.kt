package com.trtc.uikit.livekit.voiceroom.view.seatmanager

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover

class SeatActionSheetDialog(private val context: Context) : AtomicPopover(context) {

    private val viewContainer: ViewGroup

    init {
        val rootView =
            View.inflate(context, R.layout.livekit_voiceroom_seat_action_sheet_panel, null)
        setContent(rootView)
        viewContainer = rootView.findViewById(R.id.view_container)
        rootView.findViewById<View>(R.id.text_cancel).setOnClickListener { dismiss() }
    }

    fun updateActionButton(menuInfoList: List<ListMenuInfo>) {
        viewContainer.removeAllViews()
        for (menuInfo in menuInfoList) {
            val itemView = LayoutInflater.from(context).inflate(
                R.layout.livekit_voiceroom_item_seat_action_sheet,
                viewContainer,
                false
            )
            val textAction = itemView.findViewById<TextView>(R.id.text_action)
            textAction.text = menuInfo.text
            textAction.setOnClickListener {
                menuInfo.listener?.onClick()
                dismiss()
            }
            viewContainer.addView(itemView)
        }
    }
}