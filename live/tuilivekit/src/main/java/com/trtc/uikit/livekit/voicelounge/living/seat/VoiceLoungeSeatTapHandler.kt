package com.trtc.uikit.livekit.voicelounge.living.seat

import android.content.Context
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveToast
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeRole
import com.trtc.uikit.livekit.voicelounge.living.coguest.VoiceLoungeCoGuestStore
import com.trtc.uikit.livekit.voicelounge.vlString
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

internal class VoiceLoungeSeatTapHandler(
    private val context: Context,
    private val store: VoiceLoungeSeatStore,
) {
    var onSendGift: ((userID: String, name: String) -> Unit)? = null
    var onConfirm: ((title: String, message: String, confirmTitle: String, action: () -> Unit) -> Unit)? = null

    private val seatInvitePanel by lazy { VoiceLoungeSeatInvitePanel(context, store.liveID, store.coGuest) }
    private val seatUserPanel by lazy {
        VoiceLoungeSeatUserPanel(context, store).apply {
            onConfirm = { title, message, confirmTitle, action ->
                this@VoiceLoungeSeatTapHandler.onConfirm?.invoke(title, message, confirmTitle, action)
            }
        }
    }
    private val coGuest: VoiceLoungeCoGuestStore get() = store.coGuest

    private val selfUserID: String get() = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()

    fun handleTap(index: Int) {
        val seat = store.seats.firstOrNull { it.index == index } ?: return
        when (store.role) {
            VoiceLoungeRole.HOST -> handleHostTap(seat)
            VoiceLoungeRole.AUDIENCE -> handleAudienceTap(seat)
        }
    }

    fun dismissAll() {
        listOf(seatInvitePanel, seatUserPanel).forEach { if (it.isShowing) it.dismiss() }
    }

    private fun handleHostTap(seat: VoiceLoungeSeat) {
        if (seat.role == VoiceLoungeSeat.Role.HOST) return
        when (seat.state) {
            VoiceLoungeSeat.State.EMPTY, VoiceLoungeSeat.State.LOCKED -> seatInvitePanel.show(
                seat.index, isLocked = seat.state == VoiceLoungeSeat.State.LOCKED, skin = store.skin
            )

            VoiceLoungeSeat.State.FADED -> {
                if (seat.userID.isEmpty()) return
                seatUserPanel.show(seat)
            }

            VoiceLoungeSeat.State.OCCUPIED -> {
                if (seat.userID == selfUserID) return
                seatUserPanel.show(seat)
            }
        }
    }

    private fun handleAudienceTap(seat: VoiceLoungeSeat) {
        when (seat.state) {
            VoiceLoungeSeat.State.OCCUPIED -> {
                if (seat.userID == selfUserID && coGuest.guestStatus == VoiceLoungeCoGuestStore.GuestStatus.ON_SEAT) {
                    store.promptLeave()
                    return
                }
                if (seat.userID.isEmpty()) return
                onSendGift?.invoke(seat.userID, seat.name)
            }

            VoiceLoungeSeat.State.LOCKED -> toast(R.string.livekit_player_coguest_error_seat_locked)
            VoiceLoungeSeat.State.FADED -> {
                if (seat.userID != selfUserID) return
                if (coGuest.promptPendingInvitation()) return
                when (coGuest.guestStatus) {
                    VoiceLoungeCoGuestStore.GuestStatus.IDLE -> coGuest.apply(seat.index)
                    VoiceLoungeCoGuestStore.GuestStatus.APPLYING -> store.promptCancelApplication()
                    VoiceLoungeCoGuestStore.GuestStatus.ON_SEAT -> Unit
                }
            }

            VoiceLoungeSeat.State.EMPTY -> {
                if (seat.role == VoiceLoungeSeat.Role.HOST) return
                if (coGuest.promptPendingInvitation()) return
                when (coGuest.guestStatus) {
                    VoiceLoungeCoGuestStore.GuestStatus.IDLE -> {
                        store.moveAudienceFadedSeat(seat.index)
                        coGuest.apply(seat.index)
                    }

                    VoiceLoungeCoGuestStore.GuestStatus.APPLYING -> {
                        store.moveAudienceFadedSeat(seat.index)
                        coGuest.switchApplication(seat.index)
                    }

                    VoiceLoungeCoGuestStore.GuestStatus.ON_SEAT -> toast(
                        R.string.livekit_player_coguest_error_already_on_seat
                    )
                }
            }
        }
    }

    private fun toast(resId: Int) = LiveToast.show(context, vlString(resId))
}
