package com.trtc.uikit.livekit.features.anchorview.view.battle.panel

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.anchorview.store.AnchorBattleStore.Companion.BATTLE_REQUEST_TIMEOUT
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.BattleStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import java.util.Locale

class BattleCountdownDialog(
    context: Context,
    private val anchorManager: AnchorStore,
) : Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {

    private val logger = LiveKitLogger.getFeaturesLogger("BattleCountdownDialog")
    private lateinit var mCountdownView: TextView
    private lateinit var mTipView: TextView
    private var mCountdownValue = BATTLE_REQUEST_TIMEOUT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.livekit_battle_count_down_view)
        setCancelable(false)
        setCanceledOnTouchOutside(false)

        mCountdownView = findViewById(R.id.tv_countdown)
        mTipView = findViewById(R.id.tv_tip)
        findViewById<TextView>(R.id.tv_cancel).setOnClickListener { cancelBattle() }
        startCountdown()
    }

    private fun startCountdown() {
        val countdownRunnable = object : Runnable {
            override fun run() {
                mTipView.text = formatTip()
                mCountdownView.text = formatTime(mCountdownValue)
                mCountdownValue--
                if (mCountdownValue < 0) {
                    dismiss()
                    return
                }
                mCountdownView.postDelayed(this, 1000)
            }
        }
        mCountdownView.post(countdownRunnable)
    }

    private fun formatTip(): String {
        val tip = context.getString(R.string.common_battle_wait_start)
        val tipBuilder = StringBuilder(tip)
        for (i in 0..2 - mCountdownValue % 3) {
            tipBuilder.append(".")
        }
        return tipBuilder.toString()
    }

    private fun formatTime(second: Int): String {
        return when {
            second <= 0 -> "00:00"
            second < 60 -> String.format(Locale.getDefault(), "00:%02d", second % 60)
            else -> String.format(Locale.getDefault(), "%02d:%02d", second / 60, second % 60)
        }
    }

    private fun cancelBattle() {
        val list = mutableListOf<String>()
        val selfId = LoginStore.shared.loginState.loginUserInfo.value?.userID
        val connectedUsers =
            CoHostStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).coHostState.connected.value

        for (user in connectedUsers) {
            if (user.userID != selfId) {
                list.add(user.userID)
            }
        }

        val battleId = anchorManager.getBattleState().battleId
        anchorManager.getAnchorBattleStore().onCanceledBattle()
        BattleStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).cancelBattleRequest(
            battleId,
            list, object : CompletionHandler {
                override fun onSuccess() {

                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("BattleCountdownDialog cancelBattle failed:code:$code,desc:$desc")
                    ErrorLocalized.onError(code)
                }

            })
    }
}