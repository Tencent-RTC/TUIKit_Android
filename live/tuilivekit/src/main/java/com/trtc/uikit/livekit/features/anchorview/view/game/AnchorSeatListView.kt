package com.trtc.uikit.livekit.features.anchorview.view.game

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.anchorview.view.game.AnchorSeatItemView
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 主播端横向麦位列表视图 - 用于屏幕分享（手游直播）模式
 * 独立实现，不依赖 anchor/audience BasicView，直接使用 LiveSeatStore / CoGuestStore
 */
class AnchorSeatListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        private val LOGGER = LiveKitLogger.Companion.getFeaturesLogger("AnchorSeatListView")
        private const val SEAT_COUNT = 4
    }

    private lateinit var llSeatList: LinearLayout
    private val seatItemViews: MutableList<AnchorSeatItemView> = mutableListOf()

    private var liveID: String = ""
    private var subscribeJob: Job? = null

    var onSeatClick: ((SeatInfo) -> Unit)? = null

    init {
        initView()
    }

    private fun initView() {
        LayoutInflater.from(context).inflate(R.layout.livekit_co_guest_seat_list_view, this, true)
        llSeatList = findViewById(R.id.ll_seat_list)

        for (i in 0 until SEAT_COUNT) {
            val seatItemView = AnchorSeatItemView(context)
            seatItemView.layoutParams = LayoutParams(
                0,
                LayoutParams.WRAP_CONTENT,
                1f
            )
            seatItemView.onSeatClick = { seatInfo ->
                onSeatClick?.invoke(seatInfo)
            }
            llSeatList.addView(seatItemView)
            seatItemViews.add(seatItemView)
        }
    }

    fun init(liveID: String) {
        this.liveID = liveID
        LOGGER.info("AnchorSeatListView init, liveID=$liveID")
        updateSeatList()
        addObserver()
    }

    fun destroy() {
        subscribeJob?.cancel()
        subscribeJob = null
    }

    private fun addObserver() {
        subscribeJob?.cancel()
        if (liveID.isEmpty()) return

        val liveSeatStore = LiveSeatStore.Companion.create(liveID)
        val coGuestStore = CoGuestStore.Companion.create(liveID)

        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                liveSeatStore.liveSeatState.seatList.collect {
                    post { updateSeatList() }
                }
            }
            launch {
                coGuestStore.coGuestState.connected.collect {
                    post { updateSeatList() }
                }
            }
        }
    }

    private fun updateSeatList() {
        if (liveID.isEmpty()) return

        val liveSeatStore = LiveSeatStore.Companion.create(liveID)
        val coGuestStore = CoGuestStore.Companion.create(liveID)

        val seatList = liveSeatStore.liveSeatState.seatList.value
        val currentLive = LiveListStore.Companion.shared().liveState.currentLive.value
        val loginUserId = LoginStore.Companion.shared.loginState.loginUserInfo.value?.userID ?: ""
        val isOwner = currentLive.liveOwner.userID == loginUserId
        val connectedCount = coGuestStore.coGuestState.connected.value.size

        LOGGER.info("updateSeatList: seatList.size=${seatList.size}, isOwner=$isOwner, connectedCount=$connectedCount")

        for (i in 0 until SEAT_COUNT) {
            val seatInfo = seatList.find { it.index == i } ?: SeatInfo(
                index = i,
                userInfo = SeatUserInfo()
            )
            seatItemViews.getOrNull(i)?.setSeatInfo(seatInfo, isOwner, connectedCount)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        destroy()
    }
}