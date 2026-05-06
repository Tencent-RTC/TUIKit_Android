package com.trtc.uikit.livekit.features.audienceview.store

import android.os.Handler
import android.os.Looper
import io.trtc.tuikit.atomicxcore.api.live.BattleEndedReason
import io.trtc.tuikit.atomicxcore.api.live.BattleInfo
import io.trtc.tuikit.atomicxcore.api.live.BattleListener
import io.trtc.tuikit.atomicxcore.api.live.BattleStore
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import kotlinx.coroutines.flow.MutableStateFlow

data class ViewState(
    val isApplyingToTakeSeat: MutableStateFlow<Boolean>,
    val isOnDisplayResult: MutableStateFlow<Boolean?>,
    val openCameraAfterTakeSeat: MutableStateFlow<Boolean>,
    val durationCountDown: MutableStateFlow<Int>,
    val isTakeSeatDialogShowing: MutableStateFlow<Boolean>
)

class ViewStore(liveID: String) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val coGuestState = CoGuestStore.create(liveID).coGuestState
    private val battleListener = BattleEventListener()
    private val liveBattleStore = BattleStore.create(liveID)
    private val _isApplyingToTakeSeat = MutableStateFlow<Boolean>(false)
    private val _openCameraAfterTakeSeat = MutableStateFlow<Boolean>(false)
    private val _isOnDisplayResult = MutableStateFlow<Boolean?>(null)
    private val _durationCountDown = MutableStateFlow(0)
    private val _isTakeSeatDialogShowing = MutableStateFlow(false)

    val viewState =
        ViewState(
            isApplyingToTakeSeat = _isApplyingToTakeSeat,
            isOnDisplayResult = _isOnDisplayResult,
            openCameraAfterTakeSeat = _openCameraAfterTakeSeat,
            durationCountDown = _durationCountDown,
            isTakeSeatDialogShowing = _isTakeSeatDialogShowing
        )

    init {
        liveBattleStore.addBattleListener(battleListener)
    }

    fun destroy() {
        liveBattleStore.removeBattleListener(battleListener)
        _isApplyingToTakeSeat.value = false
        _isOnDisplayResult.value = null
    }

    fun updateTakeSeatState(isApplying: Boolean) {
        _isApplyingToTakeSeat.value = isApplying
    }

    fun updateOpenCameraAfterTakeSeatState(isOpen: Boolean) {
        _openCameraAfterTakeSeat.value = isOpen
    }

    fun resetOnDisplayResult() {
        if (true == _isOnDisplayResult.value) {
            _isOnDisplayResult.value = false
        }
    }

    private inner class BattleEventListener : BattleListener() {
        override fun onBattleEnded(
            battleInfo: BattleInfo,
            reason: BattleEndedReason?
        ) {
            if (coGuestState.connected.value.isEmpty()) {
                _isOnDisplayResult.value = null
                return
            }
            _isOnDisplayResult.value = true
            mainHandler.postDelayed(
                {
                    _isOnDisplayResult.value = false
                },
                (BATTLE_END_INFO_DURATION * 1000).toLong()
            )
        }

    }

    companion object {
        const val BATTLE_END_INFO_DURATION = 5
    }
}