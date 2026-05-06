package com.trtc.uikit.livekit.features.anchorview.store

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.tencent.rtmp.TXLiveBase
import com.trtc.uikit.livekit.BuildConfig
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.live.BattleConfig
import io.trtc.tuikit.atomicxcore.api.live.BattleEndedReason
import io.trtc.tuikit.atomicxcore.api.live.BattleInfo
import io.trtc.tuikit.atomicxcore.api.live.BattleListener
import io.trtc.tuikit.atomicxcore.api.live.BattleStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val logger = LiveKitLogger.getFeaturesLogger("AnchorBattleStore")
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
    private var battleScoreJob: Job? = null

    private val battleListener = object : BattleListener() {
        override fun onBattleStarted(battleInfo: BattleInfo, inviter: SeatUserInfo, invitees: List<SeatUserInfo>) {
            logger.info("onBattleStarted:[battleInfo:$battleInfo]")
            this@AnchorBattleStore.onBattleStarted(battleInfo, inviter, invitees)
        }

        override fun onBattleEnded(battleInfo: BattleInfo, reason: BattleEndedReason?) {
            logger.info("onBattleEnded:[battleInfo:$battleInfo]")
            onBattleEnded(battleInfo)
        }

        override fun onUserJoinBattle(battleID: String, battleUser: SeatUserInfo) {
            logger.info("onUserJoinBattle:[battleID:$battleID,battleUser:$battleUser]")
        }

        override fun onUserExitBattle(battleID: String, battleUser: SeatUserInfo) {
            logger.info("onUserExitBattle:[battleID:$battleID,battleUser:$battleUser]")
            onUserExitBattle(battleUser)
        }

        override fun onBattleRequestReceived(battleID: String, inviter: SeatUserInfo, invitee: SeatUserInfo) {
            logger.info("onBattleRequestReceived:[battleID:$battleID,inviter:$inviter,invitee:$invitee]")
            onBattleRequestReceived(battleID, inviter)
        }

        override fun onBattleRequestCancelled(battleID: String, inviter: SeatUserInfo, invitee: SeatUserInfo) {
            logger.info("onBattleRequestCancelled:[battleID:$battleID,inviter:$inviter,invitee:$invitee]")
            onBattleRequestCancelled(inviter)
        }

        override fun onBattleRequestTimeout(battleID: String, inviter: SeatUserInfo, invitee: SeatUserInfo) {
            logger.info("onBattleRequestTimeout:[battleID:$battleID,inviter:$inviter,invitee:$invitee]")
            onBattleRequestTimeout(inviter, invitee)
        }

        override fun onBattleRequestAccept(battleID: String, inviter: SeatUserInfo, invitee: SeatUserInfo) {
            logger.info("onBattleRequestAccept:[battleID:$battleID,inviter:$inviter,invitee:$invitee]")
            onBattleRequestAccept(invitee)
        }

        override fun onBattleRequestReject(battleID: String, inviter: SeatUserInfo, invitee: SeatUserInfo) {
            logger.info("onBattleRequestReject:[battleID:$battleID,inviter:$inviter,invitee:$invitee]")
            onBattleRequestReject(invitee)
        }
    }

    init {
        BattleStore.create(liveInfo.liveID).addBattleListener(battleListener)
        battleScoreJob = CoroutineScope(Dispatchers.Main).launch {
            BattleStore.create(liveInfo.liveID).battleState.battleScore.collect { scoreMap ->
                logger.info("SDK battleScore changed in AnchorBattleStore, scoreMap=$scoreMap")
                onBattleScoreChanged(scoreMap)
            }
        }
    }

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

    private fun onBattleStarted(battleInfo: BattleInfo?, inviter: SeatUserInfo, invitees: List<SeatUserInfo>) {
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

    private fun onBattleEnded(battleInfo: BattleInfo?) {
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

    private fun onBattleScoreChanged(scoreMap: Map<String, Int>) {
        if (scoreMap.isEmpty() || _battledUsers.value.isEmpty()) {
            return
        }
        val list = _battledUsers.value.map { battleUser ->
            val newScore = scoreMap[battleUser.userId]
            if (newScore != null) battleUser.copy(score = newScore) else battleUser.copy()
        }.toMutableList()
        sortBattleUsersByScore(list)
        _battledUsers.update { list }
    }

    private fun onUserExitBattle(user: SeatUserInfo?) {
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

    private fun onBattleRequestReceived(battleId: String?, inviter: SeatUserInfo?) {
        battleId?.let {
            battleState.battleId = it
        }
        inviter?.let { inviter ->
            _receivedBattleRequest.update { convertToBattleUser(inviter) }
        }
    }

    private fun onBattleRequestCancelled(inviter: SeatUserInfo?) {
        removeBattleRequestReceived()
        ContextProvider.getApplicationContext()?.apply {
            val content = this.getString(
                R.string.common_battle_inviter_cancel,
                inviter?.userName
            )
            showToast(content)
        }
    }

    private fun onBattleRequestAccept(invitee: SeatUserInfo?) {
        invitee?.let {
            removeSentBattleRequest(it.userID)
        }
    }

    private fun onBattleRequestReject(invitee: SeatUserInfo?) {
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

    private fun onBattleRequestTimeout(inviter: SeatUserInfo?, invitee: SeatUserInfo?) {
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
        battleScoreJob?.cancel()
        BattleStore.create(liveInfo.liveID).removeBattleListener(battleListener)
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
        val isDebugMode = BuildConfig.LIVE_DEV_MODE
        val BATTLE_REQUEST_TIMEOUT = if (isDebugMode) 30 else 10
        val BATTLE_DURATION = if (isDebugMode) 60 else 30
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