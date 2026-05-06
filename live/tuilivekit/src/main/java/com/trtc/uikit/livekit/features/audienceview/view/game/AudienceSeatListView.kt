package com.trtc.uikit.livekit.features.audienceview.view.game

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore
import com.trtc.uikit.livekit.features.audienceview.view.BasicView
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 横向麦位列表视图 - 用于 VideoLandscape4Seats 模板
 * 对应 Flutter 的 CoGuestSeatListWidget
 */
class AudienceSeatListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BasicView(context, attrs, defStyleAttr) {

    companion object {
        private val LOGGER = LiveKitLogger.getFeaturesLogger("coGuest-SeatListView")
        private const val SEAT_COUNT = 4
    }

    private lateinit var llSeatList: LinearLayout
    private lateinit var seatItemViews: MutableList<AudienceSeatItemView>

    var onSeatClick: ((SeatInfo) -> Unit)? = null

    override fun initView() {
        seatItemViews = mutableListOf()
        LayoutInflater.from(context).inflate(R.layout.livekit_co_guest_seat_list_view, this, true)
        llSeatList = findViewById(R.id.ll_seat_list)
        
        // 创建4个麦位视图
        for (i in 0 until SEAT_COUNT) {
            val seatItemView = AudienceSeatItemView(context)
            seatItemView.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            seatItemView.onSeatClick = { seatInfo ->
                onSeatClick?.invoke(seatInfo)
            }
            llSeatList.addView(seatItemView)
            seatItemViews.add(seatItemView)
        }
    }

    override fun init(manager: AudienceStore) {
        super.init(manager)
        LOGGER.info("CoGuestSeatListView init")
        removeObserver()
        addObserver()
    }

    override fun refreshView() {
        updateSeatList()
    }

    override fun addObserver() {
        LOGGER.info("CoGuestSeatListView addObserver:${LiveListStore.shared().liveState.currentLive.value.liveID}")
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                liveSeatState.seatList.collect {
                    post { updateSeatList() }
                }
            }
        }
    }

    override fun removeObserver() {
        LOGGER.info("CoGuestSeatListView removeObserver:${LiveListStore.shared().liveState.currentLive.value.liveID}")
        subscribeStateJob?.cancel()
    }

    private fun updateSeatList() {
        if (!isStoreInitialized()) return@updateSeatList

        val seatList = liveSeatState.seatList.value
        val currentLive = liveListState.currentLive.value
        val loginUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
        val isOwner = currentLive.liveOwner.userID == loginUserId
        val connectedCount = coGuestState.connected.value.size

        LOGGER.info("updateSeatList: seatList=${seatList}, isOwner=$isOwner, connectedCount=$connectedCount")

        for (i in 0 until SEAT_COUNT) {
            val seatInfo = seatList.find { it.index == i } ?: SeatInfo(
                index = i,
                userInfo = SeatUserInfo()
            )
            seatItemViews.getOrNull(i)?.setSeatInfo(seatInfo, isOwner, connectedCount)
        }
    }
}
