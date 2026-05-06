package com.trtc.uikit.livekit.features.anchorview.view

import android.content.Context
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.addItem
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.init
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.BattleStore
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore

/**
 * Dialog shown when the anchor clicks "end live".
 * Displays different options based on the current live state
 * (in battle / in co-host / in co-guest / normal).
 */
class LiveStreamEndDialog(
    private val context: Context,
    private val anchorStore: AnchorStore,
    private val onEndLive: () -> Unit,
) {

    fun show() {
        val currentLiveId = LiveListStore.shared().liveState.currentLive.value.liveID

        val isInCoGuest = CoGuestStore.create(currentLiveId).coGuestState.connected
            .value.filterNot { it.liveID != currentLiveId }.size > 1
        val isInCoHost = CoHostStore.create(currentLiveId).coHostState.connected.value.isNotEmpty()
        val isInBattle = anchorStore.getBattleState().isBattleRunning.value == true

        AtomicAlertDialog(context).let { dialog ->
            when {
                isInBattle -> initBattleDialog(dialog, currentLiveId)
                isInCoHost -> initCoHostDialog(dialog, currentLiveId)
                isInCoGuest -> initCoGuestDialog(dialog)
                else -> initNormalDialog(dialog)
            }
            dialog.show()
        }
    }

    private fun initBattleDialog(dialog: AtomicAlertDialog, currentLiveId: String) {
        val title = context.getString(R.string.common_end_pk_tips)
        dialog.init {
            init(title)

            addItem(
                text = context.getString(R.string.common_end_pk),
                type = AtomicAlertDialog.TextColorPreset.RED,
            ) {
                exitBattle(currentLiveId)
                it.dismiss()
            }

            addItem(
                text = context.getString(R.string.common_end_live),
                type = AtomicAlertDialog.TextColorPreset.PRIMARY
            ) {
                onEndLive()
                it.dismiss()
            }

            addItem(
                text = context.getString(R.string.common_cancel),
                type = AtomicAlertDialog.TextColorPreset.PRIMARY
            ) {
                it.dismiss()
            }
        }
    }

    private fun initCoHostDialog(dialog: AtomicAlertDialog, currentLiveId: String) {
        val title = context.getString(R.string.common_end_connection_tips)
        dialog.init {
            init(title)

            addItem(
                text = context.getString(R.string.common_end_connection),
                type = AtomicAlertDialog.TextColorPreset.RED,
            ) {
                CoHostStore.create(currentLiveId).exitHostConnection(null)
                it.dismiss()
            }

            addItem(
                text = context.getString(R.string.common_end_live),
                type = AtomicAlertDialog.TextColorPreset.PRIMARY
            ) {
                onEndLive()
                it.dismiss()
            }

            addItem(
                text = context.getString(R.string.common_cancel),
                type = AtomicAlertDialog.TextColorPreset.PRIMARY
            ) {
                it.dismiss()
            }
        }
    }

    private fun initCoGuestDialog(dialog: AtomicAlertDialog) {
        val title = context.getString(R.string.common_anchor_end_link_tips)
        dialog.init {
            init(title)

            cancelButton(
                text = context.getString(R.string.common_cancel),
                type = AtomicAlertDialog.TextColorPreset.GREY
            ) {
                it.dismiss()
            }

            confirmButton(
                text = context.getString(R.string.common_end_live),
                type = AtomicAlertDialog.TextColorPreset.RED
            ) {
                onEndLive()
                it.dismiss()
            }
        }
    }

    private fun initNormalDialog(dialog: AtomicAlertDialog) {
        dialog.init {
            init(context.getString(R.string.live_end_live_tips))

            cancelButton(
                text = context.getString(R.string.common_cancel),
                type = AtomicAlertDialog.TextColorPreset.GREY
            ) {
                it.dismiss()
            }

            confirmButton(
                text = context.getString(R.string.common_end_live),
                type = AtomicAlertDialog.TextColorPreset.RED
            ) {
                onEndLive()
                it.dismiss()
            }
        }
    }

    private fun exitBattle(currentLiveId: String) {
        val battleId = anchorStore.getBattleState().battleId
        BattleStore.create(currentLiveId).exitBattle(battleId, object : CompletionHandler {
            override fun onSuccess() {
                anchorStore.getAnchorBattleStore().onExitBattle()
            }

            override fun onFailure(code: Int, desc: String) {}
        })
    }
}
