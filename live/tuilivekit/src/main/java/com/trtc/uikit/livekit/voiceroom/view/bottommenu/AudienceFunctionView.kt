package com.trtc.uikit.livekit.voiceroom.view.bottommenu

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.RelativeLayout
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.trtc.uikit.livekit.component.karaoke.view.KaraokeControlView
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.completionHandler
import com.trtc.uikit.livekit.component.gift.LikeButton
import com.trtc.uikit.livekit.component.giftaccess.GiftButton
import com.trtc.uikit.livekit.voiceroom.manager.VoiceRoomManager
import com.trtc.uikit.livekit.voiceroom.view.basic.BasicView
import io.trtc.tuikit.atomicxcore.api.live.BattleStore
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostStatus
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.GuestListener
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.NoResponseReason
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.live.TakeSeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AudienceFunctionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BasicView(context, attrs, defStyleAttr) {

    private var takeSeatButton: ImageView
    private lateinit var imageKTV: ImageView
    private var mCoHostStore: CoHostStore? = null
    private var mBattleStore: BattleStore? = null
    private lateinit var liveListStore: LiveListStore
    private lateinit var coGuestStore: CoGuestStore
    private lateinit var liveSeatStore: LiveSeatStore

    companion object {
        private const val CONNECTION_MAX_SEAT_COUNT = 6
    }

    private val guestListener = object : GuestListener() {
        override fun onGuestApplicationResponded(isAccept: Boolean, hostUser: LiveUserInfo) {
            if (isAccept) return
            voiceRoomManager?.viewStore?.updateTakeSeatState(false)
            AtomicToast.show(context, context.getString(R.string.common_voiceroom_take_seat_rejected), AtomicToast.Style.INFO)
        }

        override fun onGuestApplicationNoResponse(reason: NoResponseReason) {
            voiceRoomManager?.viewStore?.updateTakeSeatState(false)
            if (reason != NoResponseReason.TIMEOUT) return
            AtomicToast.show(context, context.getString(R.string.common_voiceroom_take_seat_timeout), AtomicToast.Style.INFO)
        }
    }

    init {
        inflate(context, R.layout.livekit_voiceroom_audience_function, this)
        takeSeatButton = findViewById(R.id.iv_take_seat)
    }

    override fun init(liveID: String, voiceRoomManager: VoiceRoomManager) {
        super.init(liveID, voiceRoomManager)

        initTakeButton()
        initGiftButton()
        initLikeButton()
        initKTVView()
    }

    override fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                voiceRoomManager?.viewStore?.viewState?.isApplyingToTakeSeat?.collect {
                    onLinkStateChanged()
                }
            }
            launch {
                liveSeatStore.liveSeatState.seatList.collect {
                    onLinkStateChanged()
                }
            }
            launch {
                mCoHostStore?.coHostState?.connected?.collect {
                    onConnectedListChanged(it)
                }
            }
        }
        coGuestStore.addGuestListener(guestListener)
    }

    fun onConnectedListChanged(connectedRoomList: List<SeatUserInfo>) {
        val currentLiveId = liveID
        if (currentLiveId.isEmpty()) return
        val isConnected = connectedRoomList.any { it.liveID == currentLiveId }
        if (isConnected) {
            imageKTV.visibility = GONE
        } else {
            imageKTV.visibility = VISIBLE
        }
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
        coGuestStore.removeGuestListener(guestListener)
    }

    override fun initStore() {
        liveListStore = LiveListStore.shared()
        coGuestStore = CoGuestStore.create(liveID)
        liveSeatStore = LiveSeatStore.create(liveID)
        mCoHostStore = CoHostStore.create(liveID)
        mBattleStore = BattleStore.create(liveID)
    }

    private fun initGiftButton() {
        val ownerInfo = liveListStore.liveState.currentLive.value.liveOwner
        findViewById<RelativeLayout>(R.id.rl_gift).addView(
            GiftButton(context).apply {
                init(
                    liveID, ownerInfo.userID,
                    ownerInfo.userName, ownerInfo.avatarURL
                )
                layoutParams = RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }
        )
    }

    private fun initLikeButton() {
        findViewById<RelativeLayout>(R.id.rl_like).addView(
            LikeButton(context).apply {
                init(liveID)
                layoutParams = RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }
        )
    }

    private fun initKTVView() {
        imageKTV = findViewById(R.id.iv_ktv)
        imageKTV.setOnClickListener {
            KaraokeControlView(context).apply {
                init(
                    liveID,
                    liveListStore.liveState.currentLive.value.liveOwner.userID == TUIRoomEngine.getSelfInfo().userId
                )
                showSongRequestPanel()
            }
        }
    }

    private fun initTakeButton() {
        takeSeatButton.setOnClickListener { view ->
            if (!liveSeatStore.liveSeatState.seatList.value.none { it.userInfo.userID == TUIRoomEngine.getSelfInfo().userId }) {
                leaveSeat()
            } else if (voiceRoomManager?.viewStore?.viewState?.isApplyingToTakeSeat?.value == true) {
                cancelSeatApplication(view)
            } else {
                takeSeat()
            }
        }
    }

    private fun hasAvailableSeat(): Boolean {
        val allSeats = liveSeatStore?.liveSeatState?.seatList?.value
        if (allSeats.isNullOrEmpty()) {
            return false
        }
        val currentLiveId = liveListStore.liveState.currentLive.value.liveID
        if (currentLiveId.isEmpty()) {
            return false
        }

        val currentRoomSeats = allSeats.filter { seat ->
            seat.userInfo.liveID == currentLiveId
        }

        return currentRoomSeats.take(CONNECTION_MAX_SEAT_COUNT).any { seat ->
            val userId = seat.userInfo.userID
            val isLocked = seat.isLocked
            userId.isEmpty() && !isLocked
        }
    }

    private fun isBackSeatsOccupied(): Boolean {
        val seatList = liveSeatStore.liveSeatState.seatList.value
        return seatList.any { it.index >= CONNECTION_MAX_SEAT_COUNT && it.userInfo.userID.isNotEmpty() }
    }

    private fun takeSeat() {
        if (voiceRoomManager?.viewStore?.viewState?.isApplyingToTakeSeat?.value == true) return
        val currentLiveId = liveListStore.liveState.currentLive.value.liveID
        if (currentLiveId.isEmpty()) return

        val isInConnection = mCoHostStore?.coHostState?.coHostStatus?.value == CoHostStatus.CONNECTED
        val isInBattle = mBattleStore?.battleState?.battleUsers?.value?.isEmpty() == false

        if (isInConnection || isInBattle) {
            if (isBackSeatsOccupied()) {
                AtomicToast.show(context, context.getString(R.string.common_back_seats_occupied), AtomicToast.Style.ERROR)
                return
            }
            if (!hasAvailableSeat()) {
                AtomicToast.show(context, context.getString(R.string.common_server_error_the_seats_are_all_taken), AtomicToast.Style.ERROR)
                return
            }
        }

        val liveInfo = liveListStore.liveState.currentLive.value
        val isOwner = TUIRoomEngine.getSelfInfo().userId == liveInfo.liveOwner.userID
        if (liveInfo.seatMode == TakeSeatMode.FREE || isOwner) {
            liveSeatStore.takeSeat(-1, completionHandler {
                onError { code, _ ->
                    ErrorLocalized.onError(code)
                }
            })
            return
        }

        voiceRoomManager?.viewStore?.updateTakeSeatState(true)
        coGuestStore.applyForSeat(-1, 60, null, completionHandler {
            onError { code, _ ->
                voiceRoomManager?.viewStore?.updateTakeSeatState(false)
                ErrorLocalized.onError(code)
            }
        })
    }

    private fun leaveSeat() {
        coGuestStore.disconnect(completionHandler {
            onError { code, _ ->
                ErrorLocalized.onError(code)
            }
        })
    }

    private fun cancelSeatApplication(view: View) {
        voiceRoomManager?.viewStore?.updateTakeSeatState(false)
        view.isEnabled = false
        coGuestStore.cancelApplication(
            completionHandler {
                onSuccess { view.isEnabled = true }
                onError { code, _ ->
                    ErrorLocalized.onError(code)
                    view.isEnabled = true
                }
            })
    }

    private fun onLinkStateChanged() {
        takeSeatButton.clearAnimation()
        if (!liveSeatStore.liveSeatState.seatList.value.none { it.userInfo.userID == TUIRoomEngine.getSelfInfo().userId }) {
            voiceRoomManager?.viewStore?.updateTakeSeatState(false)
            takeSeatButton.setImageResource(R.drawable.livekit_audience_linking_mic)
        } else if (voiceRoomManager?.viewStore?.viewState?.isApplyingToTakeSeat?.value == true) {
            takeSeatButton.setImageResource(R.drawable.livekit_audience_applying_link_mic)
            AnimationUtils.loadAnimation(context, R.anim.rotate_animation).apply {
                interpolator = LinearInterpolator()
                takeSeatButton.startAnimation(this)
            }
        } else {
            takeSeatButton.setImageResource(R.drawable.livekit_ic_hand_up)
        }
    }
}
