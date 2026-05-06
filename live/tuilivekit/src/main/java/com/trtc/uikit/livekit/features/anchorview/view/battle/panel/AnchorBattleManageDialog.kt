package com.trtc.uikit.livekit.features.anchorview.view.battle.panel

import android.content.Context
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.anchorview.store.AnchorBattleStore.Companion.BATTLE_DURATION
import com.trtc.uikit.livekit.features.anchorview.store.AnchorBattleStore.Companion.BATTLE_REQUEST_TIMEOUT
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.init
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.BattleConfig
import io.trtc.tuikit.atomicxcore.api.live.BattleInfo
import io.trtc.tuikit.atomicxcore.api.live.BattleRequestCallback
import io.trtc.tuikit.atomicxcore.api.live.BattleStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

class AnchorBattleManageDialog(
    private val context: Context,
    private val anchorStore: AnchorStore,
) {

    private val logger = LiveKitLogger.getFeaturesLogger("AnchorBattleManageDialog")
    private val anchorBattleStore = anchorStore.getAnchorBattleStore()
    private val anchorCoHostStore = anchorStore.getAnchorCoHostStore()
    private val battleState = anchorStore.getBattleState()
    private val liveID = anchorStore.getState().liveInfo.liveID

    private var anchorEndBattleDialog: AnchorEndBattleDialog? = null

    fun handleBattleClick() {
        if (battleState.isBattleRunning.value == true && anchorBattleStore.isSelfInBattle()) {
            showEndBattlePanel()
        } else {
            requestBattle()
        }
    }

    private fun showEndBattlePanel() {
        if (anchorEndBattleDialog == null) {
            anchorEndBattleDialog = AnchorEndBattleDialog(context)
            anchorEndBattleDialog?.setOnEndBattleListener(object : AnchorEndBattleDialog.OnEndBattleListener {
                override fun onEndBattle() {
                    showEndBattleConfirmDialog()
                }
            })
        }
        anchorEndBattleDialog?.show()
    }

    private fun requestBattle() {
        if (battleState.isOnDisplayResult.value == true || !anchorCoHostStore.isSelfInCoHost()) {
            logger.warn("can not requestBattle")
            return
        }

        val list = mutableListOf<String>()
        val selfId = LoginStore.shared.loginState.loginUserInfo.value?.userID
        val anchorState = anchorStore.getState()
        for (user in CoHostStore.create(anchorState.roomId).coHostState.connected.value) {
            if (user.userID != selfId) {
                list.add(user.userID)
            }
        }

        val battleConfig = BattleConfig().apply {
            duration = BATTLE_DURATION
            needResponse = true
            extensionInfo = ""
        }
        BattleStore.create(liveID).requestBattle(
            battleConfig, list, BATTLE_REQUEST_TIMEOUT, object : BattleRequestCallback {
                override fun onSuccess(
                    battleInfo: BattleInfo, resultMap: Map<String, Int>,
                ) {
                    anchorStore.getAnchorBattleStore().onRequestBattle(battleInfo.battleID, list)
                }

                override fun onError(code: Int, desc: String) {
                    logger.error("requestBattle failed:code:$code,desc:$desc")
                    ErrorLocalized.onError(code)
                }
            })
    }

    private fun showEndBattleConfirmDialog() {
        val dialog = AtomicAlertDialog(context).apply {
            init {
                init(
                    title = context.getString(R.string.common_battle_end_pk_tips),
                    content = null,
                    iconView = null,
                )

                cancelButton(context.getString(R.string.common_disconnect_cancel)) {
                    it.dismiss()
                }

                confirmButton(
                    text = context.getString(R.string.common_battle_end_pk),
                    type = AtomicAlertDialog.TextColorPreset.RED
                ) {
                    it.dismiss()
                    exitBattle()
                }
            }
        }
        dialog.show()
    }

    private fun exitBattle() {
        val battleId = battleState.battleId
        BattleStore.create(LiveListStore.shared().liveState.currentLive.value.liveID)
            .exitBattle(battleId, object : CompletionHandler {
                override fun onSuccess() {}

                override fun onFailure(code: Int, desc: String) {
                    logger.error("exitBattle failed:code:$code,desc:$desc")
                }
            })
    }

    fun dismissDialogs() {
        anchorEndBattleDialog?.dismiss()
        anchorEndBattleDialog = null
    }
}
