package com.trtc.uikit.livekit.voicelounge.living.coguest

import android.os.SystemClock
import androidx.annotation.StringRes
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.log.LiveKitLog
import com.trtc.uikit.livekit.common.theme.LiveDevicePermission
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeEvents
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeMain
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeSignal
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeRole
import com.trtc.uikit.livekit.voicelounge.voiceLoungeAppContext
import com.trtc.uikit.livekit.voicelounge.vlString
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.barrage.Barrage
import io.trtc.tuikit.atomicxcore.api.barrage.BarrageStore
import io.trtc.tuikit.atomicxcore.api.barrage.CustomMessageEvent
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.GuestListener
import io.trtc.tuikit.atomicxcore.api.live.HostListener
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.NoResponseReason
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONObject

internal class VoiceLoungeCoGuestStore {
    enum class GuestStatus {
        IDLE, APPLYING, ON_SEAT,
    }

    sealed class InvitationEvent {
        data class Received(val user: LiveUserInfo) : InvitationEvent()
        object Cancelled : InvitationEvent()
    }

    var onToast: ((String) -> Unit)? = null
    var onMicOpenRequested: (() -> Unit)? = null
    var onMicrophoneDenied: (() -> Unit)? = null

    val invitationEvents = VoiceLoungeEvents<InvitationEvent>()
    val changes = VoiceLoungeSignal()

    val fadedSeatReleases = VoiceLoungeSignal()

    var guestStatus = GuestStatus.IDLE
        private set(value) {
            if (field == value) return
            field = value
            changes.send()
        }
    var applicants: List<LiveUserInfo> = emptyList()
        private set
    var pendingUserIDs: Set<String> = emptySet()
        private set
    var invitees: List<LiveUserInfo> = emptyList()
        private set
    var inviteeIDs: Set<String> = emptySet()
        private set

    var applicantSeatIndex: Map<String, Int> = emptyMap()
        private set

    var inviteeSeatIndex: Map<String, Int> = emptyMap()
        private set
    var connectedUsers: List<SeatUserInfo> = emptyList()
        private set

    private var store: CoGuestStore? = null
    private var barrageStore: BarrageStore? = null
    private var scope: CoroutineScope? = null
    private var liveID = ""
    private var role = VoiceLoungeRole.AUDIENCE
    private var pendingInviterID: String? = null
    private var pendingInviter: LiveUserInfo? = null
    private var pendingInviteDeadline: Long = 0L
    private var pendingApplySeatIndex: Int = -1
    private val logger = LiveKitLog.getVoiceRoomLogger("VoiceLoungeCoGuestStore")

    private val selfUserID: String get() = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()

    val hasPendingInvitation: Boolean
        get() = role == VoiceLoungeRole.AUDIENCE && guestStatus != GuestStatus.ON_SEAT && pendingInviterID != null && SystemClock.elapsedRealtime() < pendingInviteDeadline

    fun promptPendingInvitation(): Boolean {
        if (!hasPendingInvitation) return false
        val inviter = pendingInviter ?: return false
        invitationEvents.send(InvitationEvent.Received(inviter))
        return true
    }

    private val guestListener = object : GuestListener() {
        override fun onGuestApplicationResponded(isAccept: Boolean, hostUser: LiveUserInfo) {
            VoiceLoungeMain.post {
                if (isAccept) {
                    guestStatus = GuestStatus.ON_SEAT
                    takeSeatAfterAccept()
                    openMicrophone()
                } else {
                    guestStatus = GuestStatus.IDLE
                    toast(R.string.livekit_player_coguest_error_rejected)
                    fadedSeatReleases.send()
                }
            }
        }

        override fun onGuestApplicationNoResponse(reason: NoResponseReason) {
            VoiceLoungeMain.post {
                toast(
                    if (reason == NoResponseReason.ALREADY_SEATED) {
                        R.string.livekit_player_coguest_error_already_seated
                    } else {
                        R.string.livekit_player_coguest_error_timeout
                    },
                )
                if (guestStatus == GuestStatus.APPLYING) guestStatus = GuestStatus.IDLE
            }
        }

        override fun onKickedOffSeat(seatIndex: Int, hostUser: LiveUserInfo) {
            VoiceLoungeMain.post {
                DeviceStore.shared().closeLocalMicrophone()
                guestStatus = GuestStatus.IDLE
            }
        }

        override fun onHostInvitationReceived(hostUser: LiveUserInfo) {
            VoiceLoungeMain.post {
                if (guestStatus == GuestStatus.ON_SEAT) return@post
                pendingInviterID = hostUser.userID
                pendingInviter = hostUser
                pendingInviteDeadline = SystemClock.elapsedRealtime() + INVITE_TIMEOUT_SECONDS * 1_000L
                invitationEvents.send(InvitationEvent.Received(hostUser))
            }
        }

        override fun onHostInvitationCancelled(hostUser: LiveUserInfo) {
            VoiceLoungeMain.post {
                clearPendingInvitation()
                invitationEvents.send(InvitationEvent.Cancelled)
            }
        }
    }

    private val hostListener = object : HostListener() {
        override fun onHostInvitationResponded(isAccept: Boolean, guestUser: LiveUserInfo) {
            VoiceLoungeMain.post {
                if (!isAccept) {
                    onToast?.invoke(vlString(R.string.livekit_voicelounge_comic_invite_rejected, displayName(guestUser)))
                }
            }
        }

        override fun onHostInvitationNoResponse(guestUser: LiveUserInfo, reason: NoResponseReason) {
            VoiceLoungeMain.post {
                onToast?.invoke(vlString(R.string.livekit_voicelounge_comic_invite_timeout, displayName(guestUser)))
            }
        }
    }

    fun activate(liveID: String, role: VoiceLoungeRole) {
        if (liveID.isEmpty()) return
        if (this.liveID == liveID && store != null) return
        deactivate()
        this.liveID = liveID
        this.role = role
        val store = CoGuestStore.create(liveID)
        this.store = store
        val barrageStore = BarrageStore.create(liveID)
        this.barrageStore = barrageStore
        val activeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = activeScope
        val state = store.coGuestState
        activeScope.launch {
            combine(state.connected, state.invitees, state.applicants) { connected, invitees, applicants ->
                Triple(connected, invitees, applicants)
            }.collect { (connected, invitees, applicants) -> apply(connected, invitees, applicants) }
        }
        when (role) {
            VoiceLoungeRole.AUDIENCE -> store.addGuestListener(guestListener)
            VoiceLoungeRole.HOST -> store.addHostListener(hostListener)
        }
        activeScope.launch {
            barrageStore.customMessageEvent.collect { event ->
                if (event is CustomMessageEvent.CustomMessageReceived) handleCustomMessage(event.barrage)
            }
        }
    }

    fun deactivate() {
        val store = store
        if (role == VoiceLoungeRole.AUDIENCE && store != null) {
            when (guestStatus) {
                GuestStatus.APPLYING -> store.cancelApplication(null)
                GuestStatus.ON_SEAT -> store.disconnect(null)
                GuestStatus.IDLE -> Unit
            }
            pendingInviterID?.let { store.rejectInvitation(it, null) }
        }
        if (role == VoiceLoungeRole.AUDIENCE && guestStatus == GuestStatus.ON_SEAT) {
            DeviceStore.shared().closeLocalMicrophone()
        }
        store?.removeGuestListener(guestListener)
        store?.removeHostListener(hostListener)
        scope?.cancel()
        scope = null
        this.store = null
        barrageStore = null
        liveID = ""
        applicants = emptyList()
        pendingUserIDs = emptySet()
        invitees = emptyList()
        inviteeIDs = emptySet()
        applicantSeatIndex = emptyMap()
        inviteeSeatIndex = emptyMap()
        connectedUsers = emptyList()
        clearPendingInvitation()
        pendingApplySeatIndex = -1
        guestStatus = GuestStatus.IDLE
        changes.send()
    }

    fun apply(seatIndex: Int) {
        val store = store ?: return
        if (role != VoiceLoungeRole.AUDIENCE || guestStatus != GuestStatus.IDLE) return
        if (promptPendingInvitation()) return
        guestStatus = GuestStatus.APPLYING
        pendingApplySeatIndex = seatIndex
        store.applyForSeat(seatIndex, APPLY_TIMEOUT_SECONDS, "{\"mediaType\":\"audio\"}", object : CompletionHandler {
            override fun onSuccess() {
                VoiceLoungeMain.post { announceSeatIntent(seatIndex) }
            }

            override fun onFailure(code: Int, desc: String) {
                VoiceLoungeMain.post {
                    logger.error("applyForSeat failed: code=$code, message=$desc")
                    onToast?.invoke(VoiceLoungeErrorText.coGuestApply(code))
                    if (guestStatus == GuestStatus.APPLYING) guestStatus = GuestStatus.IDLE
                }
            }
        })
    }

    fun switchApplication(seatIndex: Int) {
        val store = store ?: return
        if (role != VoiceLoungeRole.AUDIENCE || guestStatus != GuestStatus.APPLYING) return
        val restart = {
            VoiceLoungeMain.post {
                if (guestStatus == GuestStatus.APPLYING) {
                    guestStatus = GuestStatus.IDLE
                    apply(seatIndex)
                }
            }
        }
        store.cancelApplication(object : CompletionHandler {
            override fun onSuccess() = restart()
            override fun onFailure(code: Int, desc: String) = restart()
        })
    }

    private fun announceSeatIntent(seatIndex: Int) {
        barrageStore?.sendCustomMessage(SEAT_INTENT_BUSINESS_ID, "{\"seat\":${maxOf(-1, seatIndex)}}", null)
    }

    fun cancelApplication() {
        val store = store ?: return
        if (role != VoiceLoungeRole.AUDIENCE || guestStatus != GuestStatus.APPLYING) return
        store.cancelApplication(null)
        guestStatus = GuestStatus.IDLE
        pendingApplySeatIndex = -1
        fadedSeatReleases.send()
    }

    fun leaveSeat() {
        val store = store ?: return
        if (role != VoiceLoungeRole.AUDIENCE || guestStatus != GuestStatus.ON_SEAT) return
        store.disconnect(object : CompletionHandler {
            override fun onSuccess() = Unit
            override fun onFailure(code: Int, desc: String) {
                VoiceLoungeMain.post { onToast?.invoke(VoiceLoungeErrorText.coGuest(code)) }
            }
        })
        DeviceStore.shared().closeLocalMicrophone()
        guestStatus = GuestStatus.IDLE
    }

    fun acceptInvitation() {
        val store = store ?: return
        val inviterID = pendingInviterID ?: return
        if (role != VoiceLoungeRole.AUDIENCE) return
        clearPendingInvitation()
        if (guestStatus == GuestStatus.APPLYING) {
            store.cancelApplication(null)
            guestStatus = GuestStatus.IDLE
        }
        store.acceptInvitation(inviterID, object : CompletionHandler {
            override fun onSuccess() {
                VoiceLoungeMain.post {
                    guestStatus = GuestStatus.ON_SEAT
                    openMicrophone()
                }
            }

            override fun onFailure(code: Int, desc: String) {
                VoiceLoungeMain.post { onToast?.invoke(VoiceLoungeErrorText.coGuest(code)) }
            }
        })
    }

    fun rejectInvitation() {
        val store = store ?: return
        val inviterID = pendingInviterID ?: return
        if (role != VoiceLoungeRole.AUDIENCE) return
        clearPendingInvitation()
        store.rejectInvitation(inviterID, null)
        if (guestStatus == GuestStatus.IDLE) fadedSeatReleases.send()
    }

    private fun clearPendingInvitation() {
        pendingInviterID = null
        pendingInviter = null
        pendingInviteDeadline = 0L
    }

    fun invite(userID: String, seatIndex: Int) {
        if (role != VoiceLoungeRole.HOST || userID.isEmpty() || userID in pendingUserIDs) return
        inviteeSeatIndex = if (seatIndex >= 0) inviteeSeatIndex + (userID to seatIndex) else inviteeSeatIndex - userID
        perform(userID) { store, completion ->
            store.inviteToSeat(userID, seatIndex, INVITE_TIMEOUT_SECONDS, "", completion)
        }
    }

    fun forgetUser(userID: String) {
        if (role != VoiceLoungeRole.HOST || userID.isEmpty()) return
        applicantSeatIndex = applicantSeatIndex - userID
        inviteeSeatIndex = inviteeSeatIndex - userID
    }

    fun cancelInvitation(userID: String) {
        perform(userID) { store, completion -> store.cancelInvitation(userID, completion) }
    }

    fun accept(userID: String) {
        perform(userID) { store, completion -> store.acceptApplication(userID, completion) }
    }

    fun reject(userID: String) {
        perform(userID) { store, completion -> store.rejectApplication(userID, completion) }
    }

    private fun perform(userID: String, action: (CoGuestStore, CompletionHandler) -> Unit) {
        val store = store ?: return
        if (role != VoiceLoungeRole.HOST || userID.isEmpty() || userID in pendingUserIDs) return
        pendingUserIDs = pendingUserIDs + userID
        notifyApplicants()
        val liveID = liveID
        val finish = { code: Int? ->
            VoiceLoungeMain.post {
                if (this.liveID != liveID) return@post
                pendingUserIDs = pendingUserIDs - userID
                if (code != null) {
                    onToast?.invoke(VoiceLoungeErrorText.coGuest(code))
                }
                notifyApplicants()
            }
        }
        action(store, object : CompletionHandler {
            override fun onSuccess() = finish(null)
            override fun onFailure(code: Int, desc: String) = finish(code)
        })
    }

    private fun apply(connected: List<SeatUserInfo>, invitees: List<LiveUserInfo>, applicants: List<LiveUserInfo>) {
        val selfID = selfUserID
        when (role) {
            VoiceLoungeRole.HOST -> {
                val seen = HashSet<String>()
                this.applicants =
                    applicants.filter { it.userID.isNotEmpty() && it.userID != selfID && seen.add(it.userID) }
                val inviteeSeen = HashSet<String>()
                this.invitees =
                    invitees.filter { it.userID.isNotEmpty() && it.userID != selfID && inviteeSeen.add(it.userID) }
                inviteeIDs = this.invitees.map { it.userID }.toSet()
                inviteeSeatIndex = inviteeSeatIndex.filterKeys { it in inviteeIDs }
                pendingUserIDs = pendingUserIDs.intersect(this.applicants.map { it.userID }.toSet() + inviteeIDs)
                connectedUsers = connected.filter { it.userID.isNotEmpty() && it.userID != selfID }
                notifyApplicants()
            }

            VoiceLoungeRole.AUDIENCE -> {
                if (selfID.isEmpty()) return
                val onSeat = connected.any { it.userID == selfID }
                if (onSeat) {
                    guestStatus = GuestStatus.ON_SEAT
                } else if (guestStatus == GuestStatus.ON_SEAT) {
                    guestStatus = GuestStatus.IDLE
                }
            }
        }
    }

    private fun notifyApplicants() = changes.send()

    fun requestOpenMicrophone(userID: String) {
        if (role != VoiceLoungeRole.HOST || userID.isEmpty()) return
        barrageStore?.sendCustomMessage(MIC_OPEN_BUSINESS_ID, JSONObject().put("user", userID).toString(), null)
    }

    private fun handleCustomMessage(barrage: Barrage) {
        when {
            role == VoiceLoungeRole.HOST && barrage.businessID == SEAT_INTENT_BUSINESS_ID -> handleSeatIntent(barrage)
            role == VoiceLoungeRole.AUDIENCE && barrage.businessID == MIC_OPEN_BUSINESS_ID -> handleMicOpenRequest(
                barrage
            )
        }
    }

    private fun handleMicOpenRequest(barrage: Barrage) {
        if (guestStatus != GuestStatus.ON_SEAT) return
        val json = parse(barrage.data) ?: return
        if (json.optString("user") != selfUserID) return
        onMicOpenRequested?.invoke()
    }

    private fun handleSeatIntent(barrage: Barrage) {
        val userID = barrage.sender.userID
        if (userID.isEmpty()) return
        val json = parse(barrage.data) ?: return
        val seat = json.opt("seat") as? Int ?: return
        applicantSeatIndex = if (seat >= 0) applicantSeatIndex + (userID to seat) else applicantSeatIndex - userID
        notifyApplicants()
    }

    private fun parse(data: String): JSONObject? = runCatching { JSONObject(data) }.getOrNull()

    private fun toast(@StringRes id: Int) {
        onToast?.invoke(vlString(id))
    }

    private fun takeSeatAfterAccept() {
        if (liveID.isEmpty()) return
        val seatIndex = pendingApplySeatIndex
        pendingApplySeatIndex = -1
        if (seatIndex < 0) {
            logger.warn("takeSeatAfterAccept skipped: pendingApplySeatIndex missing")
            return
        }
        LiveSeatStore.create(liveID).takeSeat(seatIndex, object : CompletionHandler {
            override fun onSuccess() {
                logger.info("takeSeatAfterAccept success seatIndex=$seatIndex")
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("takeSeatAfterAccept failed code=$code desc=$desc")
            }
        })
    }

    private fun openMicrophone() {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            if (LiveDevicePermission.requestMicrophonePermission(voiceLoungeAppContext)) {
                DeviceStore.shared().openLocalMicrophone(null)
            } else {
                onMicrophoneDenied?.invoke()
            }
        }
    }

    companion object {
        const val SEAT_INTENT_BUSINESS_ID = "vl_seat_apply"
        const val MIC_OPEN_BUSINESS_ID = "vl_mic_open"
        private const val APPLY_TIMEOUT_SECONDS = 60
        private const val INVITE_TIMEOUT_SECONDS = 30

        private fun displayName(user: LiveUserInfo): String = user.userName.ifEmpty { user.userID }
    }
}

internal object VoiceLoungeErrorText {
    fun coGuest(code: Int): String = vlString(keys[code] ?: R.string.livekit_player_coguest_error_operation_failed)

    fun coGuestApply(code: Int): String = vlString(keys[code] ?: R.string.livekit_player_coguest_error_apply_failed)

    private val keys: Map<Int, Int> = mapOf(
        100_004 to R.string.livekit_player_coguest_error_room_not_exist,
        100_005 to R.string.livekit_player_coguest_error_not_room_member,
        100_200 to R.string.livekit_player_coguest_error_seat_locked,
        100_202 to R.string.livekit_player_coguest_error_already_requesting,
        100_203 to R.string.livekit_player_coguest_error_already_on_seat,
        100_205 to R.string.livekit_player_coguest_error_seat_full,
        100_210 to R.string.livekit_player_coguest_error_seat_occupied,
        100_211 to R.string.livekit_player_coguest_error_not_supported,
    )
}
