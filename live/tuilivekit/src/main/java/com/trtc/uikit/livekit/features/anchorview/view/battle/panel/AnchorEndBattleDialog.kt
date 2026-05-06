package com.trtc.uikit.livekit.features.anchorview.view.battle.panel

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover

class AnchorEndBattleDialog(
    context: Context,
) : AtomicPopover(context) {

    private var onEndBattleListener: OnEndBattleListener? = null

    init {
        val view = layoutInflater.inflate(R.layout.livekit_anchor_end_battle_panel, null)
        setContent(view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initCancelButton()
        initEndLiveButton()
    }

    fun setOnEndBattleListener(listener: OnEndBattleListener?) {
        onEndBattleListener = listener
    }

    private fun initEndLiveButton() {
        val textEndLive = findViewById<TextView>(R.id.tv_end_live)
        textEndLive?.text = context.getString(R.string.common_battle_end_pk)
        textEndLive?.setOnClickListener {
            dismiss()
            if (onEndBattleListener != null) {
                onEndBattleListener?.onEndBattle()
            }
        }
    }

    private fun initCancelButton() {
        val textCancel = findViewById<TextView>(R.id.tv_cancel)
        textCancel?.text = context.getString(R.string.common_cancel)
        textCancel?.setOnClickListener { dismiss() }
    }

    interface OnEndBattleListener {
        fun onEndBattle()
    }
}