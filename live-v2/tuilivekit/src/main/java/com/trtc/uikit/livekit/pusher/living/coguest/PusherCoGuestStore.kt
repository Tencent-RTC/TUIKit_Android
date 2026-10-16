package com.trtc.uikit.livekit.pusher.living.coguest

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.basic.ContextProvider
import com.trtc.uikit.livekit.common.basic.LiveKitLogger
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.live.CoGuestLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.DeviceControlPolicy
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.Role
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import java.text.BreakIterator

interface PusherCoGuestActionHandling {
    fun selectLayout(layout: PusherCoGuestStore.Layout)
    fun acceptApplication(userID: String)
    fun invite(userID: String)
    fun cancelInvitation(userID: String)
    fun disconnect(userID: String)
    fun endAllConnections()
    fun markApplicantsSeen()
    fun toggleRemoteMicrophone(userID: String)
}

class PusherCoGuestStore(
    private val liveID: String,
    private val seatStore: LiveSeatStore,
) : PusherCoGuestActionHandling {
    enum class Layout(
        @StringRes val title: Int,
        @DrawableRes val icon: Int,
        val layoutTemplate: CoGuestLayoutTemplate,
        val guestCapacity: Int,
        val showsEmptySeats: Boolean,
    ) {
        FLOATING(
            R.string.livekit_coguest_layout_floating,
            R.drawable.livekit_pusher_coguest_layout_floating,
            CoGuestLayoutTemplate(601),
            6,
            false,
        ),
        GRID_2(
            R.string.livekit_coguest_layout_grid2,
            R.drawable.livekit_pusher_coguest_layout_grid2,
            CoGuestLayoutTemplate(600),
            8,
            false,
        ),
        FIXED(
            R.string.livekit_coguest_layout_fixed,
            R.drawable.livekit_pusher_coguest_layout_fixed,
            CoGuestLayoutTemplate(801),
            6,
            true,
        ),
        GRID_9(
            R.string.livekit_coguest_layout_grid9,
            R.drawable.livekit_pusher_coguest_layout_grid9,
            CoGuestLayoutTemplate(800),
            8,
            true,
        );

    }

    data class User(
        val userID: String,
        val userName: String,
        val avatarURL: String,
        val microphoneStatus: DeviceStatus = DeviceStatus.OFF,
        val allowOpenMicrophone: Boolean = true,
        val cameraStatus: DeviceStatus = DeviceStatus.OFF,
        val allowOpenCamera: Boolean = true,
    )

    data class State(
        val layout: Layout = Layout.GRID_2,
        val connected: List<User> = emptyList(),
        val applicants: List<User> = emptyList(),
        val candidates: List<User> = emptyList(),
        val invitees: List<User> = emptyList(),
        val pendingUserIDs: Set<String> = emptySet(),
        val isUpdatingLayout: Boolean = false,
        val hasUnreadApplicant: Boolean = false,
        // Retained for source compatibility; errors are delivered through toasts.
        val errorMessage: String? = null,
    ) {
        val isFull: Boolean get() = connected.size >= layout.guestCapacity
    }

    private val sdkStore = CoGuestStore.create(liveID)
    private val logger = LiveKitLogger.getComponentLogger("PusherCoGuestStore")
    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    internal val toasts = toastMessages.asSharedFlow()

    private var scope: CoroutineScope? = null
    private var subscribeJob: Job? = null
    private var layoutOperationID: UUID? = null
    private var isConnected = false
    private var pendingDesiredLayout: Layout? = Layout.GRID_2
    private var seenApplicantIDs = emptySet<String>()

    fun activate() {
        if (scope != null) return
        val activeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = activeScope
        pendingDesiredLayout = state.value.layout
        val sdkState = sdkStore.coGuestState
        subscribeJob = activeScope.launch {
            combine(
                sdkState.connected,
                sdkState.invitees,
                sdkState.applicants,
                sdkState.candidates,
                seatStore.liveSeatState.seatList,
            ) { connected, invitees, applicants, candidates, seats ->
                CoGuestSnapshot(connected, invitees, applicants, candidates, seats)
            }.collect { snapshot ->
                if (scope === activeScope) apply(snapshot)
            }
        }
    }

    fun deactivate() {
        isConnected = false
        layoutOperationID = null
        pendingDesiredLayout = null
        subscribeJob?.cancel()
        subscribeJob = null
        scope?.cancel()
        scope = null
        seenApplicantIDs = emptySet()
        mutableState.value = State(layout = mutableState.value.layout)
    }

    override fun markApplicantsSeen() {
        val current = mutableState.value
        seenApplicantIDs = current.applicants.mapTo(mutableSetOf()) { it.userID }
        if (current.hasUnreadApplicant) mutableState.value = current.copy(hasUnreadApplicant = false)
    }

    override fun selectLayout(layout: Layout) {
        val current = mutableState.value
        if (scope == null || layout == current.layout || current.isUpdatingLayout) return
        if (current.connected.size > layout.guestCapacity) {
            toastMessages.tryEmit(string(R.string.livekit_pusher_coguest_error_capacity))
            return
        }
        pendingDesiredLayout = layout
        if (!isConnected) return
        updateLayout(layout, current.layout)
    }

    fun didConnect() {
        if (scope == null) return
        isConnected = true
        synchronizeLayoutIfNeeded()
    }

    private fun synchronizeLayoutIfNeeded() {
        val desired = pendingDesiredLayout ?: return
        if (scope == null || !isConnected || mutableState.value.isUpdatingLayout) return
        updateLayout(desired, state.value.layout)
    }

    override fun acceptApplication(userID: String) {
        if (rejectWhenFull()) return
        perform(userID) { sdkStore.acceptApplication(userID, it) }
    }

    override fun invite(userID: String) {
        if (rejectWhenFull()) return
        perform(userID) { sdkStore.inviteToSeat(userID, -1, 30, "", it) }
    }

    override fun cancelInvitation(userID: String) =
        perform(userID) { sdkStore.cancelInvitation(userID, it) }

    override fun disconnect(userID: String) =
        perform(userID) { seatStore.kickUserOutOfSeat(userID, it) }

    override fun toggleRemoteMicrophone(userID: String) {
        if (scope == null || userID in mutableState.value.pendingUserIDs ||
            mutableState.value.connected.none { it.userID == userID }) return
        val user = seatStore.liveSeatState.seatList.value.firstOrNull { it.userInfo.userID == userID }?.userInfo ?: return
        if (user.allowOpenMicrophone && user.microphoneStatus != DeviceStatus.ON) {
            toastMessages.tryEmit(string(R.string.livekit_pusher_coguest_microphone_manual_open))
            return
        }
        val connectedUser = mutableState.value.connected.first { it.userID == userID }
        val name = microphoneToastName(connectedUser)
        val successMessage = string(
            if (user.allowOpenMicrophone) R.string.livekit_pusher_coguest_microphone_closed
            else R.string.livekit_pusher_coguest_microphone_unlocked,
            name,
        )
        perform(userID, successMessage) { callback ->
            if (!user.allowOpenMicrophone) seatStore.openRemoteMicrophone(userID, DeviceControlPolicy.UNLOCK_ONLY, callback)
            else seatStore.closeRemoteMicrophone(userID, callback)
        }
    }

    private fun microphoneToastName(user: User): String {
        val normalized = user.userName.splitToSequence(Regex("[\\s\\p{Z}]+"))
            .filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { user.userID }
        val iterator = BreakIterator.getCharacterInstance().apply { setText(normalized) }
        var end = iterator.first()
        repeat(8) {
            val next = iterator.next()
            if (next == BreakIterator.DONE) return normalized
            end = next
        }
        return if (end < normalized.length) normalized.substring(0, end) + "…" else normalized
    }

    override fun endAllConnections() {
        mutableState.value.connected.forEach { disconnect(it.userID) }
    }

    private fun updateLayout(
        layout: Layout,
        rollbackLayout: Layout,
    ) {
        val activeScope = scope ?: return
        val operationID = UUID.randomUUID()
        layoutOperationID = operationID
        mutableState.value = mutableState.value.copy(isUpdatingLayout = true)
        sdkStore.setLayoutTemplate(layout.layoutTemplate, object : CompletionHandler {
            override fun onSuccess() = finishLayoutUpdate(operationID, layout, null, activeScope)

            override fun onFailure(code: Int, desc: String) {
                logger.error("set layout failed template=${layout.layoutTemplate.templateId} code=$code message=$desc")
                finishLayoutUpdate(operationID, rollbackLayout, layoutErrorMessage(code), activeScope)
            }
        })
    }

    private fun layoutErrorMessage(code: Int): String = string(
        when (code) {
            -5, 7008, 9506, 100012 -> R.string.livekit_pusher_coguest_error_layout_too_frequent
            -2 -> R.string.livekit_pusher_coguest_error_layout_unavailable
            -3, 6013 -> R.string.livekit_pusher_coguest_error_layout_not_ready
            -4 -> R.string.livekit_pusher_coguest_error_layout_unsupported
            -7 -> R.string.livekit_pusher_coguest_error_layout_room_unsupported
            6014 -> R.string.livekit_pusher_coguest_error_layout_login_required
            9508, 9512, in 9523..9525 -> R.string.livekit_pusher_coguest_error_layout_network
            9510, in 9518..9521 -> R.string.livekit_pusher_coguest_error_layout_timeout
            100004 -> R.string.livekit_pusher_coguest_error_layout_room_missing
            100006 -> R.string.livekit_pusher_coguest_error_layout_permission_denied
            // Includes generic failure, invalid responses, and unknown upstream errors.
            else -> R.string.livekit_pusher_coguest_error_layout_failed
        },
    )

    private fun finishLayoutUpdate(operationID: UUID, layout: Layout, error: String?, activeScope: CoroutineScope) {
        activeScope.launch {
            if (scope !== activeScope || layoutOperationID != operationID) return@launch
            layoutOperationID = null
            pendingDesiredLayout = null
            mutableState.value = mutableState.value.copy(
                layout = layout,
                isUpdatingLayout = false,
            )
            if (error != null) toastMessages.tryEmit(error)
        }
    }

    private fun perform(userID: String, successMessage: String? = null, action: (CompletionHandler) -> Unit) {
        val current = mutableState.value
        if (scope == null || userID.isEmpty() || userID in current.pendingUserIDs) return
        mutableState.value = current.copy(
            pendingUserIDs = current.pendingUserIDs + userID,
        )
        val activeScope = scope ?: return
        action(object : CompletionHandler {
            override fun onSuccess() = finishUserOperation(userID, null, activeScope, successMessage)

            override fun onFailure(code: Int, desc: String) {
                logger.error("co-guest operation failed: userID=$userID, code=$code, desc=$desc")
                finishUserOperation(userID, string(R.string.livekit_pusher_coguest_error_operation_failed), activeScope, null)
            }
        })
    }

    private fun finishUserOperation(userID: String, error: String?, activeScope: CoroutineScope, successMessage: String?) {
        activeScope.launch {
            if (scope !== activeScope) return@launch
            val current = mutableState.value
            mutableState.value = current.copy(
                pendingUserIDs = current.pendingUserIDs - userID,
            )
            if (error != null) toastMessages.tryEmit(error)
            else if (successMessage != null) toastMessages.tryEmit(successMessage)
        }
    }

    private fun apply(snapshot: CoGuestSnapshot) {
        val loginUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        val seatUsers = snapshot.seats.associate { it.userInfo.userID to it.userInfo }
        val connected = unique(snapshot.connected.mapNotNull { original ->
            val user = seatUsers[original.userID] ?: original
            if (user.userID.isEmpty() || user.userID == loginUserID ||
                (user.liveID.isNotEmpty() && user.liveID != liveID) || user.role == Role.OWNER
            ) null else user.toPusherUser()
        })
        val connectedIDs = connected.mapTo(mutableSetOf()) { it.userID }
        val applicants = unique(snapshot.applicants.map { it.toPusherUser() })
            .filter { it.userID !in connectedIDs && it.userID != loginUserID }
        val applicantIDs = applicants.mapTo(mutableSetOf()) { it.userID }
        val invitees = unique(snapshot.invitees.map { it.toPusherUser() })
            .filter { it.userID !in connectedIDs && it.userID != loginUserID }
        val inviteeIDs = invitees.mapTo(mutableSetOf()) { it.userID }
        val candidates = unique(snapshot.candidates.map { it.toPusherUser() }).filter {
            it.userID !in connectedIDs && it.userID !in applicantIDs &&
                    it.userID !in inviteeIDs && it.userID != loginUserID
        }
        seenApplicantIDs = seenApplicantIDs.intersect(applicantIDs)
        mutableState.value = mutableState.value.copy(
            connected = connected,
            applicants = applicants,
            invitees = invitees,
            candidates = candidates,
            hasUnreadApplicant = applicants.any { it.userID !in seenApplicantIDs },
        )
    }

    private fun rejectWhenFull(): Boolean {
        val current = mutableState.value
        if (!current.isFull) return false
        toastMessages.tryEmit(
            string(R.string.livekit_pusher_coguest_error_max_capacity, current.layout.guestCapacity),
        )
        return true
    }

    private fun unique(users: List<User>): List<User> =
        users.distinctBy { it.userID }.filter { it.userID.isNotEmpty() }

    private fun string(resource: Int, vararg args: Any): String =
        ContextProvider.applicationContext?.getString(resource, *args).orEmpty()
}

private data class CoGuestSnapshot(
    val connected: List<SeatUserInfo>,
    val invitees: List<LiveUserInfo>,
    val applicants: List<LiveUserInfo>,
    val candidates: List<LiveUserInfo>,
    val seats: List<SeatInfo>,
)

private fun LiveUserInfo.toPusherUser() =
    PusherCoGuestStore.User(userID, userName.ifEmpty { userID }, avatarURL)

private fun SeatUserInfo.toPusherUser() =
    PusherCoGuestStore.User(
        userID,
        userName.ifEmpty { userID },
        avatarURL,
        microphoneStatus,
        allowOpenMicrophone,
        cameraStatus,
        allowOpenCamera,
    )
