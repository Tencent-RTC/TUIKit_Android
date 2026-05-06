package com.trtc.uikit.livekit.voiceroom.view.seatmanager

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.completionHandler
import com.trtc.uikit.livekit.voiceroom.view.seatmanager.SeatInvitationAdapter.OnInviteButtonClickListener
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.HostListener
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.NoResponseReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SeatInvitationDialog(
    private val context: Context,
) : AtomicPopover(context) {
    private val TAKE_SEAT_TIMEOUT: Int = 10
    private lateinit var imageBack: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var seatInvitationListView: RecyclerView
    private lateinit var seatInvitationAdapter: SeatInvitationAdapter

    private val liveListStore = LiveListStore.shared()
    private val liveSeatStore =
        LiveSeatStore.create(liveListStore.liveState.currentLive.value.liveID)
    private val liveAudienceStore =
        LiveAudienceStore.create(liveListStore.liveState.currentLive.value.liveID)
    private val coGuestStore = CoGuestStore.create(liveListStore.liveState.currentLive.value.liveID)
    private val coHostStore = CoHostStore.create(liveListStore.liveState.currentLive.value.liveID)
    private var invitationIndex = -1
    private var subscribeStateJob: Job? = null

    init {
        initView()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeObserver()
    }

    private fun initView() {
        val rootView = View.inflate(context, R.layout.livekit_voiceroom_seat_invite_panel, null)
        setContent(rootView)
        bindViewId(rootView)
        tvTitle.setText(R.string.common_voiceroom_invite)
        setTitle(context.getString(R.string.common_voiceroom_invite))
        showBackButton()
        initSeatListView()
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                liveAudienceStore.liveAudienceState.audienceList.collect { audienceList ->
                    onAudienceListChanged()
                }
            }
            launch {
                coGuestStore.coGuestState.invitees.collect { coGuestList ->
                    seatInvitationAdapter.notifyDataSetChanged()
                }
            }
            launch {
                liveSeatStore.liveSeatState.seatList.collect {
                    updateSeatListView()
                }
            }
        }
        coGuestStore.addHostListener(hostListener)
    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
        coGuestStore.removeHostListener(hostListener)
    }

    private fun bindViewId(rootView: View) {
        imageBack = rootView.findViewById(R.id.iv_back)
        tvTitle = rootView.findViewById(R.id.tv_title)
        seatInvitationListView = rootView.findViewById(R.id.rv_seat_invitation)
    }

    private fun showBackButton() {
        imageBack.setOnClickListener { dismiss() }
        imageBack.visibility = VISIBLE
    }

    fun setInviteSeatIndex(seatIndex: Int) {
        invitationIndex = seatIndex
    }

    private fun initSeatListView() {
        seatInvitationListView.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        seatInvitationAdapter = SeatInvitationAdapter(context)
        seatInvitationAdapter.setOnInviteButtonClickListener(object : OnInviteButtonClickListener {
            override fun onItemClick(
                inviteButton: View,
                userInfo: LiveUserInfo,
            ) {
                onInviteButtonClicked(inviteButton, userInfo)
            }
        })
        seatInvitationListView.adapter = seatInvitationAdapter
    }

    private fun onAudienceListChanged() {
        seatInvitationAdapter.updateData()
    }

    private fun updateSeatListView() {
        seatInvitationAdapter.updateData()
    }

    fun setTargetIndex(index: Int?) {
        if (index != null)
            invitationIndex = index
    }

    private fun onInviteButtonClicked(inviteButton: View, userInfo: LiveUserInfo) {
        val userId = userInfo.userID
        if (inviteButton.isSelected) {
            coGuestStore.cancelInvitation(userId, completionHandler {
                onError { code, desc ->
                    LOGGER.error("cancelInvitation failed,error:$code,message:$desc")
                    ErrorLocalized.onError(code)
                }
            })
            return
        }
        val isConnected =
            coHostStore.coHostState.connected.value.any { it.liveID == LiveListStore.shared().liveState.currentLive.value.liveID }
        if (isConnected && !hasAvailableSeat()) {
            AtomicToast.show(
                context,
                context.getString(R.string.common_server_error_the_seats_are_all_taken),
                AtomicToast.Style.ERROR
            )
            return
        }
        coGuestStore.inviteToSeat(
            userId,
            invitationIndex,
            TAKE_SEAT_TIMEOUT,
            null,
            completionHandler {
                onError { code, desc ->
                    LOGGER.error("takeUserOnSeatByAdmin failed,error:$code,message:$desc")
                    ErrorLocalized.onError(code)
                }
            })
        if (invitationIndex != -1) {
            dismiss()
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

        return currentRoomSeats.take(6).any { seat ->
            val userId = seat.userInfo.userID
            val isLocked = seat.isLocked
            userId.isEmpty() && !isLocked
        }
    }

    private val hostListener = object : HostListener() {
        override fun onHostInvitationResponded(isAccept: Boolean, guestUser: LiveUserInfo) {
            if (isAccept) return
            AtomicToast.show(
                context,
                context.getString(R.string.common_voiceroom_invite_seat_rejected),
                AtomicToast.Style.INFO
            )
        }

        override fun onHostInvitationNoResponse(guestUser: LiveUserInfo, reason: NoResponseReason) {
            if (reason != NoResponseReason.TIMEOUT) return
            AtomicToast.show(
                context,
                context.getString(R.string.common_connect_invitation_timeout),
                AtomicToast.Style.INFO
            )
        }
    }

    companion object {
        private val LOGGER = LiveKitLogger.getVoiceRoomLogger("SeatInvitationDialog")
    }
}