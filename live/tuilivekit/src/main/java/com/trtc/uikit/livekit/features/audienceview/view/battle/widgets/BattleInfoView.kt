package com.trtc.uikit.livekit.features.audienceview.view.battle.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import com.tencent.rtmp.TXLiveBase
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.audienceview.view.BasicView
import io.trtc.tuikit.atomicxcore.api.live.BattleEndedReason
import io.trtc.tuikit.atomicxcore.api.live.BattleInfo
import io.trtc.tuikit.atomicxcore.api.live.BattleListener
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
class BattleInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : BasicView(context, attrs) {

    private enum class BattleResultType {
        DRAW, VICTORY, DEFEAT
    }

    private val logger = LiveKitLogger.getLiveStreamLogger("BattleInfoView")
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var battle1V1ScoreView: Battle1V1ScoreView
    private lateinit var battleTimeView: TextView
    private lateinit var battleStartView: ImageView
    private lateinit var battleResultView: ImageView

    private val battleListener = object : BattleListener() {
        override fun onBattleEnded(battleInfo: BattleInfo, reason: BattleEndedReason?) {
            onBattleEnd()
        }
    }

    private fun getCurrentTimestamp(): Long {
        val networkTimestamp = TXLiveBase.getNetworkTimestamp()
        val localTimestamp = System.currentTimeMillis()
        return if (networkTimestamp > 0) networkTimestamp else localTimestamp
    }

    override fun initView() {
        inflate(context, R.layout.livekit_audience_battle_info_view, this)
        battleTimeView = findViewById(R.id.tv_battle_time)
        battleStartView = findViewById(R.id.iv_battle_start)
        battleResultView = findViewById(R.id.iv_battle_result)
        battle1V1ScoreView = findViewById(R.id.single_battle_score_view)
        visibility = GONE
    }

    override fun addObserver() {
        logger.info("addObserver:" + hashCode())
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                audienceStore.getCoHostState().connected.collect {
                    onConnectedListChange()
                }
            }
            launch {
                audienceStore.getBattleState().battleUsers.collect {
                    onBattleScoreChanged()
                }
            }
            launch {
                audienceStore.getViewState().isOnDisplayResult.collect {
                    onResultDisplay(it)
                }
            }
            launch {
                mediaState.isPictureInPictureMode.collect {
                    onPictureInPictureObserver(it)
                }
            }
            launch {
                battleStore.battleState.currentBattleInfo.collect { battleInfo ->
                    if (battleInfo?.battleID?.isNotEmpty() == true) {
                        onBattleStart(battleInfo)
                    }
                }
            }
        }
        audienceStore.getBattleStore().addBattleListener(battleListener)
    }

    override fun removeObserver() {
        logger.info("removeObserver:" + hashCode())
        subscribeStateJob?.cancel()
        audienceStore.getBattleStore().removeBattleListener(battleListener)
    }

    private fun onBattleStart(battleInfo: BattleInfo) {
        visibility = if (mediaState.isPictureInPictureMode.value) GONE else VISIBLE
        if (coHostState.connected.value.size == 2) {
            battle1V1ScoreView.visibility = visibility
        } else {
            battle1V1ScoreView.visibility = GONE
        }
        battleStartView.visibility = VISIBLE
        postDelayed({ battleStartView.visibility = GONE }, 1000)
        var duration =
            (battleInfo.config.duration + battleInfo.startTime - getCurrentTimestamp() / 1000).toInt()
        duration = duration.coerceAtMost(battleInfo.config.duration)
        duration = duration.coerceAtLeast(0)
        viewState.durationCountDown.value = duration
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                val t = duration
                if (t > 0 && viewState.durationCountDown.value > 0) {
                    duration = t - 1
                    post { updateTime(duration.toLong()) }
                    mainHandler.postDelayed(this, 1000)
                }
            }
        }, 1000)
    }

    private fun onBattleEnd() {
        logger.info("onBattleEnd:" + hashCode())
        visibility = if (mediaState.isPictureInPictureMode.value) GONE else VISIBLE

        audienceStore.getViewState().isOnDisplayResult.value = true
        viewState.durationCountDown.value = 0
        updateTime(0L)
        mainHandler.postDelayed({
            post {
                audienceStore.getViewState().isOnDisplayResult.value = false
                mainHandler.postDelayed({
                    audienceStore.getViewState().isOnDisplayResult.value = null
                }, 200L)
            }
        }, (BATTLE_END_INFO_DURATION * 1000).toLong())
    }

    private fun onBattleScoreChanged() {
        val singleBattleUserMap = HashMap<String, SeatUserInfo>()
        if (audienceStore.getCoHostState().connected.value.size == 2) {
            for (connectionUser in audienceStore.getCoHostState().connected.value) {
                singleBattleUserMap[connectionUser.userID] = connectionUser
            }
        }
        val is1V1Battle = audienceStore.getCoHostState().connected.value.size == 2
        if (is1V1Battle) {
            val userList = ArrayList(singleBattleUserMap.values)
            updateData(userList[1], userList[0])
        } else {
            battle1V1ScoreView.visibility = GONE
        }
    }

    private fun updateData(inviter: SeatUserInfo, invitee: SeatUserInfo) {
        battle1V1ScoreView.visibility = VISIBLE
        battle1V1ScoreView.updateScores(
            audienceStore.getBattleState().battleScore.value[inviter.userID] ?: 0,
            audienceStore.getBattleState().battleScore.value[invitee.userID] ?: 0
        )
    }

    private fun showBattleResult() {
        val type = getBattleResult()
        battleResultView.visibility = VISIBLE
        val resId = when (type) {
            BattleResultType.VICTORY -> R.drawable.livekit_battle_result_victory
            BattleResultType.DEFEAT -> R.drawable.livekit_battle_result_defeat
            else -> R.drawable.livekit_battle_result_draw
        }
        battleResultView.setImageResource(resId)
    }

    @SuppressLint("DefaultLocale")
    private fun updateTime(time: Long) {
        battleTimeView.text = String.format("%d:%02d", time / 60, time % 60)
        if (time == 0L) {
            battleTimeView.text = context.getString(R.string.common_battle_pk_end)
        }
    }

    private fun onResultDisplay(display: Boolean?) {
        logger.info("onResultDisplay: $display： hashCode->${hashCode()}")
        if (display == true) {
            showBattleResult()
        } else if (false == display) {
            stopDisplayBattleResult()
        }
    }

    private fun getBattleResult(): BattleResultType {
        val list = audienceStore.getBattleState().battleUsers.value
        if (list.isEmpty()) {
            return BattleResultType.DRAW
        }
        val firstUser = list[0]
        val lastUser = list[list.size - 1]
        val firstScore = getRankingFromMap(firstUser.userID, audienceStore.getBattleState().battleScore.value)
        val secondScore = getRankingFromMap(lastUser.userID, audienceStore.getBattleState().battleScore.value)
        if (firstScore > secondScore) {
            return BattleResultType.VICTORY
        } else if (firstScore < secondScore) {
            return BattleResultType.DEFEAT
        }
        return BattleResultType.DRAW
    }

    private fun getRankingFromMap(userID: String, scoreMap: Map<String, Int>): Int {
        data class TmpUser(val userId: String, val score: Int)

        val list = scoreMap.map { TmpUser(it.key, it.value) }
            .sortedByDescending { it.score }

        val rankMap = mutableMapOf<String, Int>()
        for ((index, tmpUser) in list.withIndex()) {
            val rank = if (index > 0 && tmpUser.score == list[index - 1].score) {
                rankMap[list[index - 1].userId] ?: index
            } else {
                index + 1
            }
            rankMap[tmpUser.userId] = rank
        }

        return rankMap[userID] ?: 0
    }

    private fun stopDisplayBattleResult() {
        updateTime(0)
        visibility = GONE
        battleStartView.visibility = GONE
        battleResultView.visibility = GONE
    }

    private fun onConnectedListChange() {
        if (audienceStore.getCoHostState().connected.value.size <= 1) {
            audienceStore.getViewStore().resetOnDisplayResult()
            return
        }
        onBattleScoreChanged()
    }

    private fun onPictureInPictureObserver(isPipMode: Boolean?) {
        if (java.lang.Boolean.TRUE == isPipMode) {
            visibility = GONE
        } else {
            if (!audienceStore.getBattleStore().battleState.currentBattleInfo.value?.battleID.isNullOrBlank()) {
                visibility = VISIBLE
            }
        }
    }

    companion object {
        const val BATTLE_END_INFO_DURATION = 5
    }
}
