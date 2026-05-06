package com.trtc.uikit.livekit.voiceroom.store

import kotlinx.coroutines.flow.MutableStateFlow

data class ViewState(
    val isApplyingToTakeSeat: MutableStateFlow<Boolean>,
    val pendingBattleID: MutableStateFlow<String?>,
    val pendingBattleUserIDs: MutableStateFlow<List<String>>,
)

class ViewStore {
    private val _isApplyingToTakeSeat = MutableStateFlow<Boolean>(false)
    private val _pendingBattleID = MutableStateFlow<String?>(null)
    private val _pendingBattleUserIDs = MutableStateFlow<List<String>>(emptyList())
    val viewState = ViewState(_isApplyingToTakeSeat, _pendingBattleID, _pendingBattleUserIDs)

    fun updateTakeSeatState(isApplying: Boolean) {
        _isApplyingToTakeSeat.value = isApplying
    }

    fun onBattleRequestSent(battleID: String, userIDs: List<String>) {
        _pendingBattleID.value = battleID
        _pendingBattleUserIDs.value = userIDs
    }

    fun onBattleRequestCleared() {
        _pendingBattleID.value = null
        _pendingBattleUserIDs.value = emptyList()
    }

    fun destroy() {
        _isApplyingToTakeSeat.value = false
        _pendingBattleID.value = null
        _pendingBattleUserIDs.value = emptyList()
    }
}