package com.trtc.uikit.livekit.features.anchorboardcast.store

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import com.tencent.cloud.tuikit.engine.extension.TUILiveBattleManager
import com.tencent.rtmp.TXLiveBase
import com.trtc.uikit.livekit.R
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.live.BattleConfig
import io.trtc.tuikit.atomicxcore.api.live.BattleInfo
import io.trtc.tuikit.atomicxcore.api.live.BattleStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class BattleUser(
    var roomId: String = "",
    var userId: String = "",
    var userName: String = "",
    var avatarUrl: String = "",
    var score: Int = 0,
    var ranking: Int = 0,
    var rect: Rect = Rect(),
)

fun convertToBattleUser(seatUserInfo: SeatUserInfo): BattleUser {
    return BattleUser(
        roomId = seatUserInfo.liveID,
        userId = seatUserInfo.userID,
        userName = seatUserInfo.userName,
        avatarUrl = seatUserInfo.avatarURL,
        score = 0,
        ranking = 0,
        rect = Rect()
    )
}


data class AnchorBattleState(
    val battledUsers: StateFlow<List<BattleUser>>,
    val sentBattleRequests: StateFlow<List<String>>,
    val receivedBattleRequest: StateFlow<BattleUser?>,
    val isInWaiting: StateFlow<Boolean?>,
    val isBattleRunning: StateFlow<Boolean?>,
    val isOnDisplayResult: StateFlow<Boolean?>,
    val durationCountDown: StateFlow<Int>,
    var battleConfig: BattleConfig,
    var battleId: String,
    var isShowingStartView: Boolean,
)


class AnchorBattleStore(val liveInfo: LiveInfo) {
    private val _battledUsers = MutableStateFlow<List<BattleUser>>(arrayListOf())
    private val _sentBattleRequests = MutableStateFlow<List<String>>(arrayListOf())
    private val _receivedBattleRequest = MutableStateFlow<BattleUser?>(null)
    private val _isInWaiting = MutableStateFlow<Boolean?>(null)
    private val _isBattleRunning = MutableStateFlow<Boolean?>(null)
    private val _isOnDisplayResult = MutableStateFlow<Boolean?>(null)
    private val _durationCountDown = MutableStateFlow(0)

    internal val battleState = AnchorBattleState(
        battledUsers = _battledUsers,
        sentBattleRequests = _sentBattleRequests,
        receivedBattleRequest = _receivedBattleRequest,
        isInWaiting = _isInWaiting,
        isBattleRunning = _isBattleRunning,
        isOnDisplayResult = _isOnDisplayResult,
        durationCountDown = _durationCountDown,
        battleConfig = BattleConfig(),
        battleId = "",
        isShowingStartView = false
    )
    private val mainHandler = Handler(Looper.getMainLooper())

    fun onRequestBattle(battleId: String, requestedUserIdList: List<String>) {
        battleState.battleId = battleId
        _isInWaiting.update { true }
        val currentRequests = _sentBattleRequests.value.toMutableList()
        currentRequests.addAll(requestedUserIdList)
        _sentBattleRequests.update { currentRequests }
    }

    fun onCanceledBattle() {
        _isInWaiting.update { false }
        _sentBattleRequests.update { arrayListOf() }
    }

    fun onResponseBattle() {
        removeBattleRequestReceived()
    }

    fun onExitBattle() {
        resetState()
    }

    fun resetOnDisplayResult() {
        mainHandler.removeCallbacksAndMessages(null)
        if (_isOnDisplayResult.value == true) {
            _isOnDisplayResult.update { false }
        }
    }

    fun isBattleDraw(): Boolean {
        val list = _battledUsers.value
        if (list.isEmpty()) {
            return false
        }
        val firstUser = list[0]
        val lastUser = list[list.size - 1]
        return firstUser.ranking == lastUser.ranking
    }

    fun isSelfInBattle(): Boolean {
        val userList = _battledUsers.value
        val selfUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID
        return userList.any { TextUtils.equals(selfUserId, it.userId) }
    }

    fun updateBattleUsers(users: List<BattleUser>) {
        _battledUsers.update { users }
    }

    fun onBattleStarted(battleInfo: BattleInfo?, inviter: SeatUserInfo, invitees: List<SeatUserInfo>) {
        if (battleInfo == null || _isBattleRunning.value == true) {
            return
        }
        battleState.battleId = battleInfo.battleID
        battleState.battleConfig = battleInfo.config
        var duration = (battleInfo.config.duration + battleInfo.startTime - getCurrentTimestamp() / 1000).toInt()
        duration = minOf(duration, battleInfo.config.duration)
        duration = maxOf(duration, 0)
        _durationCountDown.update { duration }

        val countdownRunnable = object : Runnable {
            override fun run() {
                val t = _durationCountDown.value
                if (t > 0) {
                    _durationCountDown.update { t - 1 }
                    mainHandler.postDelayed(this, 1000)
                }
            }
        }
        mainHandler.postDelayed(countdownRunnable, 1000)

        val users = arrayListOf<SeatUserInfo>().apply {
            addAll(invitees)
            add(inviter)
        }
        val list = _battledUsers.value.toMutableList()
        for (user in users) {
            val battleUser = convertToBattleUser(user).apply {
                score = BattleStore.create(liveInfo.liveID).battleState.battleScore.value[user.userID] ?: 0
            }
            list.add(battleUser)
        }
        sortBattleUsersByScore(list)
        _isInWaiting.update { false }
        _isBattleRunning.update { true }
        _battledUsers.update { list }
        battleState.isShowingStartView = true
    }

    fun onBattleEnded(battleInfo: BattleInfo?) {
        mainHandler.removeCallbacksAndMessages(null)
        battleState.isShowingStartView = false
        battleState.battleId = ""
        battleState.battleConfig = BattleConfig()
        _sentBattleRequests.update { arrayListOf() }

        battleInfo?.let {
            val list = _battledUsers.value.toMutableList()
            for (battleUser in list) {
                battleUser.score =
                    BattleStore.create(liveInfo.liveID).battleState.battleScore.value[battleUser.userId] ?: 0
            }
            sortBattleUsersByScore(list)
            _battledUsers.update { list }
        }
        _isBattleRunning.update { false }
        mainHandler.removeCallbacksAndMessages(null)

        val connectedList = CoHostStore.create(liveInfo.liveID).coHostState.connected.value
        if (connectedList.isEmpty()) {
            _isOnDisplayResult.update { false }
            resetState()
            return
        }
        _isOnDisplayResult.update { true }
        mainHandler.postDelayed({
            _isOnDisplayResult.update { false }
            mainHandler.postDelayed({ resetState() }, 100)
        }, BATTLE_END_INFO_DURATION * 1000L)
    }

    fun onBattleScoreChanged(users: List<TUILiveBattleManager.BattleUser>?) {
        if (users == null || users.isEmpty()) {
            return
        }
        val list = _battledUsers.value.toMutableList()
        for (user in users) {
            for (battleUser in list) {
                if (battleUser.userId == user.userId) {
                    battleUser.score = user.score
                    break
                }
            }
        }
        sortBattleUsersByScore(list)
        _battledUsers.update { list }
    }

    fun onUserExitBattle(user: SeatUserInfo?) {
        if (user == null) {
            return
        }
        val users = _battledUsers.value.toMutableList()
        var exitUser: BattleUser? = null
        for (battleUser in users) {
            if (battleUser.userId == user.userID) {
                exitUser = battleUser
                break
            }
        }
        if (users.size == 2) {
            return
        }
        exitUser?.let {
            users.remove(it)
            sortBattleUsersByScore(users)
            _battledUsers.update { users }
        }
    }

    fun onBattleRequestReceived(battleId: String?, inviter: SeatUserInfo?) {
        battleId?.let {
            battleState.battleId = it
        }
        inviter?.let { inviter ->
            _receivedBattleRequest.update { convertToBattleUser(inviter) }
        }
    }

    fun onBattleRequestCancelled(inviter: SeatUserInfo?) {
        removeBattleRequestReceived()
        ContextProvider.getApplicationContext()?.apply {
            val content = this.getString(
                R.string.common_battle_inviter_cancel,
                inviter?.userName
            )
            showToast(content)
        }
    }

    fun onBattleRequestAccept(invitee: SeatUserInfo?) {
        invitee?.let {
            removeSentBattleRequest(it.userID)
        }
    }

    fun onBattleRequestReject(invitee: SeatUserInfo?) {
        invitee?.let {
            removeSentBattleRequest(it.userID)
            ContextProvider.getApplicationContext()?.apply {
                val content = this.getString(
                    R.string.common_battle_invitee_reject,
                    it.userName
                )
                showToast(content)
            }
        }
    }

    fun onBattleRequestTimeout(inviter: SeatUserInfo?, invitee: SeatUserInfo?) {
        val selfUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID
        if (TextUtils.equals(inviter?.userID, selfUserId)) {
            _sentBattleRequests.update { arrayListOf() }
            _isInWaiting.update { false }
        } else {
            removeBattleRequestReceived()
            invitee?.let {
                removeSentBattleRequest(it.userID)
            }
        }
        ContextProvider.getApplicationContext()?.apply {
            showToast(this.getString(R.string.common_battle_invitation_timeout))
        }
    }

    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        resetState()
    }

    private fun getCurrentTimestamp(): Long {
        val networkTimestamp = TXLiveBase.getNetworkTimestamp()
        val localTimestamp = System.currentTimeMillis()
        return if (networkTimestamp > 0) networkTimestamp else localTimestamp
    }

    private fun sortBattleUsersByScore(users: MutableList<BattleUser>) {
        users.sortByDescending { it.score }
        for (i in users.indices) {
            val user = users[i]
            user.ranking = if (i == 0) {
                1
            } else {
                val preUser = users[i - 1]
                if (preUser.score == user.score) preUser.ranking else preUser.ranking + 1
            }
        }
    }

    private fun removeBattleRequestReceived() {
        _receivedBattleRequest.value = null
    }

    private fun removeSentBattleRequest(userId: String) {
        val sendRequests = _sentBattleRequests.value.toMutableList()
        val iterator = sendRequests.iterator()
        while (iterator.hasNext()) {
            val sendUserId = iterator.next()
            if (TextUtils.equals(sendUserId, userId)) {
                iterator.remove()
                break
            }
        }
        if (sendRequests.isEmpty()) {
            _isInWaiting.update { false }
        }
        _sentBattleRequests.update { sendRequests }
    }

    private fun resetState() {
        _battledUsers.value = arrayListOf()
        _sentBattleRequests.value = arrayListOf()
        _receivedBattleRequest.value = null
        _isInWaiting.value = null
        _isBattleRunning.value = null
        _isOnDisplayResult.value = null
        _durationCountDown.value = 0
        battleState.battleConfig = BattleConfig()
        battleState.battleId = ""
        battleState.isShowingStartView = false
    }

    companion object {
        const val BATTLE_REQUEST_TIMEOUT = 10
        const val BATTLE_DURATION = 30
        const val BATTLE_END_INFO_DURATION = 5

        private fun showToast(tips: String) {
            ContextProvider.getApplicationContext()?.apply {
                AtomicToast.show(
                    this,
                    tips,
                    style = AtomicToast.Style.INFO
                )
            }
        }
    }
}