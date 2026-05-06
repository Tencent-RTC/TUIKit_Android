package com.trtc.uikit.livekit.features.anchorview.view.battle.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.anchorview.store.BattleUser
import com.trtc.uikit.livekit.features.anchorview.view.BasicView
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
class BattleInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : BasicView(context, attrs) {

    private enum class BattleResultType {
        DRAW, VICTORY, DEFEAT
    }

    private val logger = LiveKitLogger.getFeaturesLogger("BattleInfoView")
    private val isRtl: Boolean = context.resources.configuration.layoutDirection == LAYOUT_DIRECTION_RTL
    private lateinit var singleBattleScoreView: SingleBattleScoreView
    private lateinit var battleTimeView: TextView
    private lateinit var battleStartView: ImageView
    private lateinit var battleResultView: ImageView
    private var subscribeStateJob: Job? = null

    override fun initView() {
        inflate(context, R.layout.livekit_battle_info_view, this)
        battleTimeView = findViewById(R.id.tv_battle_time)
        battleStartView = findViewById(R.id.iv_battle_start)
        battleResultView = findViewById(R.id.iv_battle_result)
        singleBattleScoreView = findViewById(R.id.single_battle_score_view)
        visibility = GONE
    }

    override fun refreshView() {
    }

    override fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                onConnectedListChange()
            }
            launch {
                onPipModeObserver()
            }
            launch {
                onBattleStartChange()
            }
            launch {
                onBattleScoreChanged()
            }
            launch {
                onDurationCountDown()
            }
            launch {
                onResultDisplay()
            }
        }
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun onBattleStart() {
        singleBattleScoreView.visibility = GONE
        visibility = VISIBLE
        battleStartView.visibility = VISIBLE
        postDelayed({ battleStartView.visibility = GONE }, 1000)
    }

    private fun onBattleEnd() {
        if (mediaState?.isPipModeEnabled?.value != true && battleState?.isBattleRunning?.value == true) {
            visibility = VISIBLE
        }
        battleTimeView.text = baseContext.getString(R.string.common_battle_pk_end)
    }

    private suspend fun onBattleScoreChanged() {
        battleState?.battledUsers?.collect {
            battleState?.battledUsers?.value?.let { users ->
                if (users.isNotEmpty()) {
                    val battleUserMap = users.associateBy { it.userId }
                    val connectedUsers =
                        CoHostStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).coHostState.connected.value

                    // single battle: only 2 users in connecting and battling (1v1 battle)
                    val singleBattleUserMap = mutableMapOf<String, BattleUser>()
                    if (connectedUsers.size == 2) {
                        for (connectionUser in connectedUsers) {
                            battleUserMap[connectionUser.userID]?.let { battleUser ->
                                singleBattleUserMap[battleUser.userId] = battleUser
                            }
                        }
                    }

                    val isSingleBattle = singleBattleUserMap.size == 2
                    if (isSingleBattle) {
                        val userList = singleBattleUserMap.values.toList()
                        if (userList[0].rect.left < userList[1].rect.left) {
                            updateData(userList[0], userList[1])
                        } else {
                            updateData(userList[1], userList[0])
                        }
                    }
                }
            }
        }
    }

    private fun updateData(inviter: BattleUser, invitee: BattleUser) {
        singleBattleScoreView.visibility = VISIBLE
        singleBattleScoreView.updateScores(inviter.score, invitee.score)
    }

    private fun showBattleResult(type: BattleResultType) {
        battleResultView.visibility = VISIBLE
        val resId = when (type) {
            BattleResultType.VICTORY -> R.drawable.livekit_battle_result_victory
            BattleResultType.DEFEAT -> R.drawable.livekit_battle_result_defeat
            BattleResultType.DRAW -> R.drawable.livekit_battle_result_draw
        }
        battleResultView.setImageResource(resId)
    }

    private suspend fun onDurationCountDown() {
        battleState?.durationCountDown?.collect { duration ->
            post { updateTime(duration.toLong()) }
        }
    }

    private fun updateTime(time: Long) {
        val minutes = time / 60
        val seconds = time % 60
        battleTimeView.text = if (isRtl) {
            String.format("%02d:%02d", seconds, minutes)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private suspend fun onResultDisplay() {
        battleState?.isOnDisplayResult?.collect { display ->
            logger.info("onResultDisplay: $display")
            display?.let {
                when (display) {
                    true -> {
                        val ownerUserId = LiveListStore.shared().liveState.currentLive.value.liveOwner.userID
                        battleState?.battledUsers?.value?.let { battledUsers ->
                            for (battleUser in battledUsers) {
                                if (battleUser.userId == ownerUserId) {
                                    val type = when {
                                        anchorBattleStore?.isBattleDraw() == true -> BattleResultType.DRAW
                                        battleUser.ranking == 1 -> BattleResultType.VICTORY
                                        else -> BattleResultType.DEFEAT
                                    }
                                    showBattleResult(type)
                                    break
                                }
                            }
                        }
                    }

                    false -> stopDisplayBattleResult()
                }
            }
        }
    }

    private fun stopDisplayBattleResult() {
        updateTime(0)
        visibility = GONE
        battleStartView.visibility = GONE
        battleResultView.visibility = GONE
    }

    private suspend fun onConnectedListChange() {
        CoHostStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).coHostState.connected.collect {
            onBattleScoreChanged(battleState?.battledUsers?.value ?: emptyList())
            if (it.size <= 1) {
                stopDisplayBattleResult()
            } else if (it.size == 2 && battleState?.isBattleRunning?.value == true) {
                singleBattleScoreView.visibility = VISIBLE
            } else {
                singleBattleScoreView.visibility = GONE
            }
        }
    }

    private fun onBattleScoreChanged(battleUsers: List<BattleUser>) {
        val connectedUsers =
            CoHostStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).coHostState.connected.value
        if (battleUsers.isEmpty() || connectedUsers.isEmpty()) {
            anchorBattleStore?.resetOnDisplayResult()
            return
        }
        anchorBattleStore?.updateBattleUsers(battleState?.battledUsers?.value ?: arrayListOf())
    }

    private suspend fun onBattleStartChange() {
        battleState?.isBattleRunning?.collect { start ->
            logger.info("onBattleStartChange: $start")
            start?.let {
                when (start) {
                    true -> onBattleStart()
                    false -> onBattleEnd()
                }
            }
        }

    }

    private suspend fun onPipModeObserver() {
        mediaState?.isPipModeEnabled?.collect {
            if (it) {
                visibility = GONE
            } else {
                if (battleState?.isBattleRunning?.value == true) {
                    visibility = VISIBLE
                }
            }
        }
    }
}