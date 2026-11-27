package com.trtc.uikit.livekit.voicelounge.living.seat

import androidx.annotation.ColorInt
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.log.LiveKitLog
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeBag
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeSignal
import com.trtc.uikit.livekit.voicelounge.addTo
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeRole
import com.trtc.uikit.livekit.voicelounge.living.coguest.VoiceLoungeCoGuestStore
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeSeatLayout
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeSettings
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeSkin
import com.trtc.uikit.livekit.voicelounge.vlString
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.Role
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal class VoiceLoungeSeatStore(
    val liveID: String,
    val role: VoiceLoungeRole,
    val coGuest: VoiceLoungeCoGuestStore,
    val muteCache: VoiceLoungeMuteCache,
    val settings: VoiceLoungeSettings,
) {
    val layout: VoiceLoungeSeatLayout get() = settings.seatLayout
    val skin: VoiceLoungeSkin get() = settings.skin

    var seats: List<VoiceLoungeSeat> = VoiceLoungeSeatTemplate.seats(VoiceLoungeSeatLayout.ONE_V8, "", "")
        private set
    var seatList: List<SeatInfo> = emptyList()
        private set
    var audienceFadedSeatIndex: Int? = null
        private set
    var isHostMicOff = false
        set(value) {
            if (field == value) return
            field = value
            refresh()
        }

    val changes = VoiceLoungeSignal()
    val cancelApplicationPrompts = VoiceLoungeSignal()
    val leavePrompts = VoiceLoungeSignal()

    private var speakingUsers: Map<String, Int> = emptyMap()
    private var localMicVolume = 0
    private var adminUserIDs: Set<String> = emptySet()
    private var knownAudienceIDs: Set<String> = emptySet()
    private var showsAudienceFadedSeat = true
    private var isStaleCleanupEnabled = false
    private var hasCleanedStaleSeats = false
    private var hasCleanedStaleAdmins = false

    private var seatStore: LiveSeatStore? = null
    private var audienceStore: LiveAudienceStore? = null
    private var sessionScope: CoroutineScope? = null
    private var audienceScope: CoroutineScope? = null
    private val bindings = VoiceLoungeBag()
    private val logger = LiveKitLog.getVoiceRoomLogger("VoiceLoungeSeatStore")

    init {
        settings.changes.subscribe { refresh(force = true) }.addTo(bindings)
        coGuest.changes.subscribe { handleCoGuestChange() }.addTo(bindings)
        coGuest.fadedSeatReleases.subscribe { hideAudienceFadedSeat() }.addTo(bindings)
        muteCache.changes.subscribe { refresh() }.addTo(bindings)
    }

    fun activate() {
        if (seatStore != null) return
        val store = LiveSeatStore.create(liveID)
        seatStore = store
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        sessionScope = scope
        val state = store.liveSeatState
        scope.launch {
            combine(
                state.seatList, state.speakingUsers
            ) { list, speaking -> list to speaking }.collect { (list, speaking) -> applySeatState(list, speaking) }
        }
        val device = DeviceStore.shared().deviceState
        scope.launch {
            combine(device.microphoneStatus, device.currentMicVolume) { status, volume ->
                if (status == DeviceStatus.ON) volume else 0
            }.distinctUntilChanged().collect { volume ->
                localMicVolume = volume
                refresh()
            }
        }
    }

    fun activateAudienceList() {
        if (audienceStore != null) return
        val store = LiveAudienceStore.create(liveID)
        audienceStore = store
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        audienceScope = scope
        val state = store.liveAudienceState
        scope.launch {
            combine(
                state.audienceList, state.adminList
            ) { audiences, admins -> audiences to admins }.collect { (audiences, admins) ->
                adminUserIDs = admins.map { it.userID }.filter { it.isNotEmpty() }.toSet()
                forgetLeftAudiences(audiences)
                cleanStaleAdminsIfNeeded()
                refresh()
            }
        }
        store.fetchAudienceList(null)
    }

    private fun forgetLeftAudiences(audiences: List<LiveUserInfo>) {
        if (role != VoiceLoungeRole.HOST) return
        val currentIDs = audiences.map { it.userID }.filter { it.isNotEmpty() }.toSet()
        val gone = knownAudienceIDs - currentIDs
        knownAudienceIDs = currentIDs
        gone.forEach { coGuest.forgetUser(it) }
    }

    fun enableStaleCleanup() {
        if (role != VoiceLoungeRole.HOST) return
        isStaleCleanupEnabled = true
        cleanStaleSeatsIfNeeded()
        cleanStaleAdminsIfNeeded()
    }

    fun deactivate() {
        sessionScope?.cancel()
        sessionScope = null
        audienceScope?.cancel()
        audienceScope = null
        seatStore = null
        audienceStore = null
        seatList = emptyList()
        speakingUsers = emptyMap()
        localMicVolume = 0
        adminUserIDs = emptySet()
        knownAudienceIDs = emptySet()
        showsAudienceFadedSeat = true
        audienceFadedSeatIndex = null
        isStaleCleanupEnabled = false
        hasCleanedStaleSeats = false
        hasCleanedStaleAdmins = false
        muteCache.reset()
        isHostMicOff = false
        refresh()
    }

    fun release() {
        deactivate()
        bindings.clear()
    }

    fun moveAudienceFadedSeat(index: Int) {
        audienceFadedSeatIndex = index
        refresh()
    }

    fun promptCancelApplication() = cancelApplicationPrompts.send()

    fun promptLeave() = leavePrompts.send()

    val hasNoFreeGuestSeat: Boolean
        get() = seats.none {
            it.role != VoiceLoungeSeat.Role.HOST && (it.state == VoiceLoungeSeat.State.EMPTY || it.state == VoiceLoungeSeat.State.FADED)
        }

    val isSelfMicOff: Boolean
        get() = selfSeatUser?.let { muteCache.isMicOff(it) } ?: true

    fun toggleSelfMicrophone(): Boolean {
        val user = selfSeatUser ?: return true
        val store = seatStore ?: return true
        if (user.microphoneStatus == DeviceStatus.ON) {
            store.muteMicrophone()
            return true
        }
        if (!user.allowOpenMicrophone) return false
        store.unmuteMicrophone(object : CompletionHandler {
            override fun onSuccess() = Unit
            override fun onFailure(code: Int, desc: String) {
                logger.error("unmute self failed: code=$code, message=$desc")
            }
        })
        return true
    }

    private val selfUserID: String get() = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()

    private val selfSeatUser: SeatUserInfo?
        get() {
            val id = selfUserID
            if (id.isEmpty()) return null
            return seatList.firstOrNull { it.userInfo.userID == id }?.userInfo
        }

    private fun applySeatState(seatList: List<SeatInfo>, speakingUsers: Map<String, Int>) {
        this.seatList = seatList
        this.speakingUsers = speakingUsers
        if (role == VoiceLoungeRole.HOST) {
            muteCache.reconcile(seatList)
            cleanStaleSeatsIfNeeded()
        }
        refresh()
    }

    private fun handleCoGuestChange() {
        if (role == VoiceLoungeRole.AUDIENCE && coGuest.guestStatus == VoiceLoungeCoGuestStore.GuestStatus.ON_SEAT) {
            showsAudienceFadedSeat = false
            audienceFadedSeatIndex = null
        }
        refresh()
    }

    private fun hideAudienceFadedSeat() {
        if (role != VoiceLoungeRole.AUDIENCE || coGuest.guestStatus != VoiceLoungeCoGuestStore.GuestStatus.IDLE) return
        showsAudienceFadedSeat = false
        audienceFadedSeatIndex = null
        refresh()
    }

    private fun cleanStaleSeatsIfNeeded() {
        val seatStore = seatStore ?: return
        if (role != VoiceLoungeRole.HOST || !isStaleCleanupEnabled || hasCleanedStaleSeats) return
        hasCleanedStaleSeats = true
        val selfID = selfUserID
        val stale = seatList.map { it.userInfo.userID }.filter { it.isNotEmpty() && it != selfID }
        if (stale.isEmpty()) return
        logger.info("clean stale seats on start: $stale")
        stale.forEach { seatStore.kickUserOutOfSeat(it, null) }
    }

    private fun cleanStaleAdminsIfNeeded() {
        val audienceStore = audienceStore ?: return
        if (role != VoiceLoungeRole.HOST || !isStaleCleanupEnabled || hasCleanedStaleAdmins) return
        hasCleanedStaleAdmins = true
        val selfID = selfUserID
        val stale = adminUserIDs.filter { it != selfID }
        if (stale.isEmpty()) return
        logger.info("clean stale admins on start: $stale")
        stale.forEach { audienceStore.revokeAdministrator(it, null) }
    }

    private fun refresh(force: Boolean = false) {
        val base = VoiceLoungeSeatTemplate.seats(layout, settings.hostName, settings.hostAvatarURL)
        val next = projectFadedSeats(overlaySeats(base, seatList)).toMutableList()
        if (role == VoiceLoungeRole.HOST) {
            val index = next.indexOfFirst { it.role == VoiceLoungeSeat.Role.HOST }
            if (index >= 0) next[index] = next[index].copy(isMuted = isHostMicOff)
        }
        val accent = skin.mid
        for (i in next.indices) next[i] = next[i].copy(accentColor = accent)
        if (!force && next == seats) return
        seats = next
        changes.send()
    }

    private fun overlaySeats(base: List<VoiceLoungeSeat>, seatList: List<SeatInfo>): List<VoiceLoungeSeat> =
        base.map { seat ->
            var result = seat
            val sdk = seatList.firstOrNull { it.index == seat.index }
            if (sdk != null) {
                val info = sdk.userInfo
                if (info.userID.isNotEmpty()) {
                    result = result.copy(
                        state = VoiceLoungeSeat.State.OCCUPIED,
                        userID = info.userID,
                        name = info.userName.ifEmpty { info.userID },
                        avatarURL = info.avatarURL,
                        isMuted = muteCache.isMicOff(info),
                        isAdmin = isAdministrator(info),
                    )
                } else if (sdk.isLocked) {
                    result = result.copy(state = VoiceLoungeSeat.State.LOCKED)
                }
            }
            if (result.role == VoiceLoungeSeat.Role.HOST && result.userID.isEmpty()) {
                result = result.copy(userID = settings.hostUserID)
            }
            result.copy(isSpeaking = result.state == VoiceLoungeSeat.State.OCCUPIED && isSpeaking(result.userID))
        }

    private fun isAdministrator(info: SeatUserInfo): Boolean = info.userID in adminUserIDs || info.role == Role.ADMIN

    private fun isSpeaking(userID: String): Boolean {
        if (userID.isEmpty()) return false
        if (userID == selfUserID) return localMicVolume > SPEAKING_VOLUME_THRESHOLD
        return (speakingUsers[userID] ?: 0) > SPEAKING_VOLUME_THRESHOLD
    }

    private fun isFreeSlot(seat: VoiceLoungeSeat): Boolean =
        seat.state == VoiceLoungeSeat.State.EMPTY && seat.role != VoiceLoungeSeat.Role.HOST

    private fun projectFadedSeats(seats: List<VoiceLoungeSeat>): List<VoiceLoungeSeat> {
        val result = seats.toMutableList()
        val selfID = selfUserID
        val onSeatIDs = seats.filter { it.state == VoiceLoungeSeat.State.OCCUPIED }.map { it.userID }.toSet()
        when (role) {
            VoiceLoungeRole.AUDIENCE -> projectAudienceFadedSeat(result, selfID, onSeatIDs)
            VoiceLoungeRole.HOST -> projectHostFadedSeats(result, selfID, onSeatIDs)
        }
        return result
    }

    private fun projectAudienceFadedSeat(
        result: MutableList<VoiceLoungeSeat>,
        selfID: String,
        onSeatIDs: Set<String>,
    ) {
        val status = coGuest.guestStatus
        val applying = status == VoiceLoungeCoGuestStore.GuestStatus.APPLYING
        val eligible =
            selfID.isNotEmpty() && status != VoiceLoungeCoGuestStore.GuestStatus.ON_SEAT && selfID !in onSeatIDs && (showsAudienceFadedSeat || applying)
        if (!eligible) {
            val index = audienceFadedSeatIndex
            if (index != null && result.none { it.index == index && isFreeSlot(it) }) audienceFadedSeatIndex = null
            return
        }
        val preferred = audienceFadedSeatIndex?.let { index ->
            result.indexOfFirst { it.index == index && isFreeSlot(it) }.takeIf { it >= 0 }
        }
        val slot = preferred ?: result.indexOfFirst { isFreeSlot(it) }.takeIf { it >= 0 } ?: return
        audienceFadedSeatIndex = result[slot].index
        val profile = LoginStore.shared.loginState.loginUserInfo.value
        result[slot] = result[slot].copy(
            state = VoiceLoungeSeat.State.FADED,
            userID = selfID,
            name = profile?.nickname ?: selfID,
            avatarURL = profile?.avatarURL.orEmpty(),
            pillText = vlString(
                if (applying) R.string.livekit_voicelounge_seat_pill_pending else R.string.livekit_voicelounge_seat_pill_apply,
            ),
            isPillHighlighted = false,
        )
    }

    private fun projectHostFadedSeats(
        result: MutableList<VoiceLoungeSeat>,
        selfID: String,
        onSeatIDs: Set<String>,
    ) {
        val applicants = coGuest.applicants.filter { it.userID !in onSeatIDs && it.userID != selfID }
        val applicantIDs = applicants.map { it.userID }.toSet()
        val invitees = coGuest.invitees.filter {
            it.userID !in onSeatIDs && it.userID != selfID && it.userID !in applicantIDs
        }
        val slots = result.indices.filter { isFreeSlot(result[it]) }.toMutableList()

        fun place(user: LiveUserInfo, slot: Int, isApplicant: Boolean) {
            result[slot] = result[slot].copy(
                state = VoiceLoungeSeat.State.FADED,
                userID = user.userID,
                name = user.userName.ifEmpty { user.userID },
                avatarURL = user.avatarURL,
                pillText = vlString(
                    if (isApplicant) {
                        R.string.livekit_voicelounge_seat_pill_start
                    } else {
                        R.string.livekit_voicelounge_seat_pill_invite_pending
                    },
                ),
                isPillHighlighted = isApplicant,
            )
        }

        val requests = applicants.map { Triple(it, true, coGuest.applicantSeatIndex[it.userID]) } + invitees.map {
            Triple(
                it, false, coGuest.inviteeSeatIndex[it.userID]
            )
        }
        val unplaced = mutableListOf<Pair<LiveUserInfo, Boolean>>()
        for ((user, isApplicant, seatIndex) in requests) {
            val position = if (seatIndex == null) -1 else slots.indexOfFirst { result[it].index == seatIndex }
            if (position >= 0) {
                place(user, slots.removeAt(position), isApplicant)
            } else {
                unplaced.add(user to isApplicant)
            }
        }
        unplaced.zip(slots).forEach { (entry, slot) -> place(entry.first, slot, entry.second) }
    }

    companion object {
        private const val SPEAKING_VOLUME_THRESHOLD = 5
    }
}

internal data class VoiceLoungeSeat(
    val index: Int,
    val role: Role = Role.GUEST,
    val state: State = State.EMPTY,
    val name: String = "",
    val avatarURL: String = "",
    val userID: String = "",
    val isSpeaking: Boolean = false,
    val isMuted: Boolean = false,
    val pillText: String = "",
    val isPillHighlighted: Boolean = false,
    val isAdmin: Boolean = false,
    @ColorInt val accentColor: Int? = null,
) {
    enum class Role {
        HOST, COHOST, GUEST,
    }

    enum class State {
        EMPTY, OCCUPIED, LOCKED, FADED,
    }
}

internal object VoiceLoungeSeatTemplate {
    fun seats(layout: VoiceLoungeSeatLayout, hostName: String, hostAvatarURL: String): List<VoiceLoungeSeat> {
        val hostState = if (hostName.isEmpty()) VoiceLoungeSeat.State.EMPTY else VoiceLoungeSeat.State.OCCUPIED
        val host = VoiceLoungeSeat(0, VoiceLoungeSeat.Role.HOST, hostState, hostName, hostAvatarURL)
        return when (layout) {
            VoiceLoungeSeatLayout.ONE_V8, VoiceLoungeSeatLayout.ONE_V8X -> listOf(host) + (1..8).map {
                VoiceLoungeSeat(
                    it
                )
            }

            VoiceLoungeSeatLayout.TWO_V8 -> listOf(
                host, VoiceLoungeSeat(1, VoiceLoungeSeat.Role.COHOST)
            ) + (2..9).map { VoiceLoungeSeat(it) }

            VoiceLoungeSeatLayout.DUO -> listOf(host, VoiceLoungeSeat(1))
        }
    }

    fun oneV8(hostName: String, hostAvatarURL: String): List<VoiceLoungeSeat> =
        seats(VoiceLoungeSeatLayout.ONE_V8, hostName, hostAvatarURL)
}

internal class VoiceLoungeMuteCache {
    var values: Map<String, Boolean> = emptyMap()
        private set

    val changes = VoiceLoungeSignal()

    fun isMicOff(info: SeatUserInfo): Boolean = values[info.userID] ?: info.isMicOff

    fun set(disabled: Boolean, userID: String) {
        values = values + (userID to disabled)
        changes.send()
    }

    fun reconcile(seatList: List<SeatInfo>) {
        if (values.isEmpty()) return
        val seatUsers = LinkedHashMap<String, SeatUserInfo>()
        seatList.filter { it.userInfo.userID.isNotEmpty() }
            .forEach { seatUsers.putIfAbsent(it.userInfo.userID, it.userInfo) }
        values = values.filter { (userID, disabled) ->
            val info = seatUsers[userID] ?: return@filter false
            info.isMicOff != disabled
        }
    }

    fun reset() {
        values = emptyMap()
    }
}

internal val SeatUserInfo.isMicOff: Boolean
    get() = microphoneStatus != DeviceStatus.ON || !allowOpenMicrophone
