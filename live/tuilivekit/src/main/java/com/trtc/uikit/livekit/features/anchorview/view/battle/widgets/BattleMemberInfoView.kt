package com.trtc.uikit.livekit.features.anchorview.view.battle.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore
import com.trtc.uikit.livekit.features.anchorview.store.BattleUser
import com.trtc.uikit.livekit.features.anchorview.view.BasicView
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
class BattleMemberInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BasicView(context, attrs) {
    private val rankingImage = intArrayOf(
        R.drawable.livekit_battle_ranking_1,
        R.drawable.livekit_battle_ranking_2,
        R.drawable.livekit_battle_ranking_3,
        R.drawable.livekit_battle_ranking_4,
        R.drawable.livekit_battle_ranking_5,
        R.drawable.livekit_battle_ranking_6,
        R.drawable.livekit_battle_ranking_7,
        R.drawable.livekit_battle_ranking_8,
        R.drawable.livekit_battle_ranking_9,
    )
    private val logger = LiveKitLogger.getFeaturesLogger("BattleMemberInfoView")
    private lateinit var rankingView: ImageView
    private lateinit var scoreView: TextView
    private lateinit var connectionStatusView: TextView
    private var subscribeStateJob: Job? = null
    private var userId: String? = null

    fun init(liveStreamManager: AnchorStore, userId: String) {
        this@BattleMemberInfoView.userId = userId
        init(liveStreamManager)
    }

    override fun initView() {
        inflate(context, R.layout.livekit_battle_member_info_view, this)
        rankingView = findViewById(R.id.iv_ranking)
        scoreView = findViewById(R.id.tv_score)
        connectionStatusView = findViewById(R.id.tv_connection_status)
        reset()
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
                onBattleResultDisplay()
            }
        }
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun setData(user: BattleUser?) {
        if (mediaState?.isPipModeEnabled?.value != true) {
            visibility = VISIBLE
        }

        if (user == null) {
            showBattleView(false)
        } else {
            showBattleView(true)
            scoreView.text = user.score.toString()
            if (user.ranking > 0 && user.ranking <= rankingImage.size) {
                rankingView.setImageResource(rankingImage[user.ranking - 1])
            }
        }
    }

    private fun reset() {
        visibility = GONE
        rankingView.visibility = GONE
        scoreView.visibility = GONE
        connectionStatusView.visibility = GONE
    }

    private fun showBattleView(show: Boolean) {
        visibility = VISIBLE
        rankingView.visibility = if (show) VISIBLE else GONE
        scoreView.visibility = if (show) VISIBLE else GONE
        connectionStatusView.visibility = if (show) GONE else VISIBLE
    }

    private suspend fun handleBattleUserChange(battleUsers: List<BattleUser>) {
        val connectedUsers =
            CoHostStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).coHostState.connected.value
        if (battleUsers.isEmpty() || connectedUsers.isEmpty()) {
            anchorBattleStore?.resetOnDisplayResult()
        } else {
            val battleUserMap = battleState?.battledUsers?.value?.associateBy { it.userId } ?: emptyMap()

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
            logger.info("onBattleChanged isSingleBattle: $isSingleBattle")

            if (isSingleBattle) {
                reset()
            } else {
                setData(battleUserMap[userId])
            }
        }
    }

    private suspend fun onConnectedListChange() {
        CoHostStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).coHostState.connected.collect {
            handleBattleUserChange(battleState?.battledUsers?.value ?: emptyList())
        }
    }

    private suspend fun onBattleScoreChanged() {
        battleState?.battledUsers?.collect { battleUsers ->
            handleBattleUserChange(battleUsers)
        }
    }

    private suspend fun onBattleStartChange() {
        battleState?.isBattleRunning?.collect { start ->
            start?.let {
                when (start) {
                    true -> onBattleStart()
                    false -> onBattleEnd()
                }
            }
        }
    }

    private fun onBattleStart() {
        reset()
    }

    private fun onBattleEnd() {
        val connectedUsers =
            CoHostStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).coHostState.connected.value
        if (connectedUsers.isEmpty()) {
            return
        }

        val battleUserMap = battleState?.battledUsers?.value?.associateBy { it.userId } ?: emptyMap()

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
        logger.info("onBattleEnd isSingleBattle: $isSingleBattle")

        if (!isSingleBattle) {
            setData(battleUserMap[userId])
        }
    }

    private suspend fun onBattleResultDisplay() {
        battleState?.isOnDisplayResult?.collect { display ->
            if (display == false) {
                reset()
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