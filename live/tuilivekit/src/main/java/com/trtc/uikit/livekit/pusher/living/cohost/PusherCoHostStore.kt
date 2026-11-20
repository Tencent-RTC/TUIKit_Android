package com.trtc.uikit.livekit.pusher.living.cohost

import com.tencent.imsdk.v2.V2TIMFollowTypeCheckResult
import com.tencent.imsdk.v2.V2TIMFriendshipListener
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.CoHostLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.live.CoHostListener
import io.trtc.tuikit.atomicxcore.api.live.CoHostStatus
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

internal class PusherCoHostStore(private val liveID: String) {
    enum class FollowStatus { UNKNOWN, NOT_FOLLOWING, FOLLOWING, MUTUAL }
    data class User(
        val liveID: String, val userID: String, val userName: String, val avatarURL: String,
        val followStatus: FollowStatus = FollowStatus.UNKNOWN,
    ) {
        val isFollowing: Boolean get() = followStatus == FollowStatus.FOLLOWING || followStatus == FollowStatus.MUTUAL
        val isMutual: Boolean get() = followStatus == FollowStatus.MUTUAL
    }

    data class State(
        val candidates: List<User> = emptyList(), val connected: List<User> = emptyList(),
        val invitees: List<User> = emptyList(), val applicant: User? = null, val applicantDeadline: Long? = null,
        val pendingLiveIDs: Set<String> = emptySet(), val isLoading: Boolean = false, val hasMore: Boolean = true,
        val loadError: String? = null, val isConnected: Boolean = false, val isExiting: Boolean = false,
        val connectionStartedAt: Long? = null,
    ) {
        fun isInvited(id: String) = invitees.any { it.liveID == id }
        fun isBusy(id: String) = isExiting || id in pendingLiveIDs
    }

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors = mutableErrors.asSharedFlow()
    private var scope: CoroutineScope? = null
    private var sdk: CoHostStore? = null
    private var activation: UUID? = null
    private var cursor = ""
    private var candidateRequest: UUID? = null
    private val operations = mutableMapOf<String, UUID>()
    private val unconfirmedCancellations = mutableMapOf<String, User>()
    private val follows = mutableMapOf<String, FollowStatus>()
    private val followRequests = mutableMapOf<String, UUID>()
    private var applicantID: UUID? = null
    private var respondingID: UUID? = null
    private var expiredLiveID: String? = null
    private var timeout: Job? = null

    private var listener: CoHostListener? = null
    private fun createListener(token: UUID) = object : CoHostListener() {
        override fun onCoHostRequestReceived(inviter: SeatUserInfo, extensionInfo: String) = onMain(token) {
            expiredLiveID = null
            updateApplicant(user(inviter))
            applyState()
        }

        override fun onCoHostRequestCancelled(inviter: SeatUserInfo, invitee: SeatUserInfo?) = onMain(token) {
            if (state.value.applicant?.liveID == inviter.liveID) resetApplicant()
            applyState()
        }

        override fun onCoHostRequestTimeout(inviter: SeatUserInfo, invitee: SeatUserInfo) = onMain(token) {
            if (state.value.applicant?.liveID == inviter.liveID) resetApplicant()
            val id = if (inviter.liveID == liveID) invitee.liveID else inviter.liveID
            unconfirmedCancellations.remove(id)
            operations.remove(id)
            update { it.copy(pendingLiveIDs = it.pendingLiveIDs - id) }
            errorResource(R.string.livekit_cohost_request_timeout)
            applyState()
        }

        override fun onCoHostRequestRejected(invitee: SeatUserInfo) = onMain(token) {
            unconfirmedCancellations.remove(invitee.liveID)
            errorResource(R.string.livekit_cohost_request_rejected)
            applyState()
        }

        override fun onCoHostRequestAccepted(invitee: SeatUserInfo) = onMain(token) {
            unconfirmedCancellations.remove(invitee.liveID)
            applyState()
        }

        override fun onCoHostUserJoined(userInfo: SeatUserInfo) = onMain(token) { applyState() }
        override fun onCoHostUserLeft(userInfo: SeatUserInfo) = onMain(token) { applyState() }
    }

    private val friendshipListener = object : V2TIMFriendshipListener() {
        override fun onMyFollowingListChanged(userInfoList: List<V2TIMUserFullInfo>?, isAdd: Boolean) =
            refreshFollows(userInfoList)

        override fun onMyFollowersListChanged(userInfoList: List<V2TIMUserFullInfo>?, isAdd: Boolean) =
            refreshFollows(userInfoList)
    }

    fun activate() {
        if (activation != null) return
        activation = UUID.randomUUID()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val store = CoHostStore.create(liveID)
        sdk = store
        listener = createListener(activation!!)
        store.addCoHostListener(listener!!)
        V2TIMManager.getFriendshipManager().addFriendListener(friendshipListener)
        listOf(
            store.coHostState.connected, store.coHostState.invitees, store.coHostState.applicant,
            store.coHostState.candidates, store.coHostState.coHostStatus
        ).forEach { flow ->
            scope?.launch { flow.collect { applyState() } }
        }
        applyState()
    }

    fun deactivate() {
        activation = null
        listener?.let { sdk?.removeCoHostListener(it) }
        listener = null
        V2TIMManager.getFriendshipManager().removeFriendListener(friendshipListener)
        scope?.cancel()
        scope = null
        sdk = null
        timeout = null
        cursor = ""
        candidateRequest = null
        applicantID = null
        respondingID = null
        expiredLiveID = null
        operations.clear()
        unconfirmedCancellations.clear()
        follows.clear()
        followRequests.clear()
        mutableState.value = State()
    }

    fun refresh() {
        if (state.value.isLoading) return
        follows.clear()
        followRequests.clear()
        fetch("")
    }

    fun loadMore() {
        if (state.value.hasMore && !state.value.isLoading) fetch(cursor)
    }

    fun invite(id: String) {
        val store = sdk ?: return
        if (id == liveID || state.value.isInvited(id) || state.value.connected.any { it.liveID == id }) return
        perform(id) { store.requestHostConnection(id, CoHostLayoutTemplate.HOST_DYNAMIC_GRID, 10, "", it) }
    }

    fun cancelInvitation(id: String) {
        val store = sdk ?: return
        if (state.value.isBusy(id)) return
        val target = state.value.invitees.firstOrNull { it.liveID == id } ?: return
        unconfirmedCancellations[id] = target
        perform(id, { success ->
            if (success) unconfirmedCancellations.remove(id)
            applyState()
        }) {
            store.cancelHostConnection(id, it)
        }
    }

    fun acceptInvitation() = respond(true)
    fun rejectInvitation() = respond(false)
    fun exitConnection() {
        val store = sdk ?: return
        val token = activation ?: return
        if (!state.value.isConnected || state.value.isExiting || state.value.pendingLiveIDs.isNotEmpty()) return
        update { it.copy(isExiting = true) }
        fun exitAfterCancel(remaining: List<User>) {
            if (activation != token) return
            if (remaining.isEmpty()) {
                store.exitHostConnection(callback(token) { success, message ->
                    update { it.copy(isExiting = false) }
                    if (!success) mutableErrors.tryEmit(message)
                })
                return
            }
            val target = remaining.first()
            unconfirmedCancellations[target.liveID] = target
            store.cancelHostConnection(target.liveID, callback(token) { success, message ->
                if (success) {
                    unconfirmedCancellations.remove(target.liveID)
                    applyState()
                    exitAfterCancel(remaining.drop(1))
                } else {
                    update { it.copy(isExiting = false) }
                    applyState()
                    mutableErrors.tryEmit(message)
                }
            })
        }
        exitAfterCancel(state.value.invitees)
    }

    private fun respond(accept: Boolean) {
        val store = sdk ?: return
        val target = state.value.applicant ?: return
        val request = applicantID ?: return
        if (state.value.isBusy(target.liveID)) return
        if ((state.value.applicantDeadline ?: 0) <= System.currentTimeMillis()) {
            expire(request)
            return
        }
        if (store.coHostState.applicant.value?.liveID != target.liveID) {
            applyState()
            return
        }
        timeout?.cancel()
        respondingID = request
        perform(target.liveID, {
            if (applicantID == request) {
                respondingID = null
                applyState()
                if (applicantID == request) scheduleTimeout(request)
            }
        }) { completion ->
            if (accept) store.acceptHostConnection(target.liveID, completion)
            else store.rejectHostConnection(target.liveID, completion)
        }
    }

    private fun perform(id: String, completion: ((Boolean) -> Unit)? = null, operation: (CompletionHandler) -> Unit) {
        val token = activation ?: return
        if (id.isEmpty() || state.value.isBusy(id)) return
        val request = UUID.randomUUID()
        operations[id] = request
        update { it.copy(pendingLiveIDs = it.pendingLiveIDs + id) }
        operation(callback(token) { success, message ->
            if (operations[id] != request) return@callback
            operations.remove(id)
            update { it.copy(pendingLiveIDs = it.pendingLiveIDs - id) }
            completion?.invoke(success)
            if (!success) mutableErrors.tryEmit(message)
        })
    }

    private fun fetch(page: String) {
        val store = sdk ?: return
        val token = activation ?: return
        val request = UUID.randomUUID()
        candidateRequest = request
        update { it.copy(isLoading = true, loadError = null) }
        store.getCoHostCandidates(page, callback(token) { success, message ->
            if (candidateRequest != request) return@callback
            candidateRequest = null
            cursor = store.coHostState.candidatesCursor.value
            update {
                it.copy(
                    isLoading = false, hasMore = cursor.isNotEmpty() && cursor != page,
                    loadError = if (success) null else message
                )
            }
            if (success) {
                applyState()
                checkFollows(state.value.candidates.map { it.userID })
            }
        })
    }

    private fun user(info: SeatUserInfo): User {
        val previous = state.value.candidates.firstOrNull { it.liveID == info.liveID }
        val id = info.userID.ifEmpty { previous?.userID.orEmpty() }
        return User(
            info.liveID, id, info.userName.ifEmpty { previous?.userName ?: id },
            info.avatarURL.ifEmpty { previous?.avatarURL.orEmpty() }, follows[id] ?: FollowStatus.UNKNOWN
        )
    }

    private fun users(values: List<SeatUserInfo>) =
        values.filter { it.liveID.isNotEmpty() && it.liveID != liveID }.distinctBy { it.liveID }.map(::user)

    private fun applyState() {
        val sdkState = sdk?.coHostState ?: return
        val connected = users(sdkState.connected.value)
        connected.forEach { unconfirmedCancellations.remove(it.liveID) }
        val invitees = (users(
            sdkState.invitees.value
        ) + unconfirmedCancellations.values.sortedBy { it.liveID }).distinctBy { it.liveID }
        val candidates = (users(
            sdkState.connected.value + sdkState.candidates.value + sdkState.invitees.value
        ) + invitees).distinctBy { it.liveID }
        val connectedNow = sdkState.coHostStatus.value == CoHostStatus.CONNECTED
        update {
            it.copy(
                connected = connected, invitees = invitees, candidates = candidates, isConnected = connectedNow,
                connectionStartedAt = if (connectedNow) it.connectionStartedAt ?: System.currentTimeMillis() else null
            )
        }
        val applicant = sdkState.applicant.value
        if (applicant != null && applicant.liveID != liveID && applicant.liveID.isNotEmpty()) {
            if (expiredLiveID != applicant.liveID) updateApplicant(user(applicant))
        } else if (respondingID == null) {
            resetApplicant()
            expiredLiveID = null
        }
    }

    private fun updateApplicant(user: User) {
        val isNew = state.value.applicant?.let { it.liveID != user.liveID || it.userID != user.userID } ?: true
        update { it.copy(applicant = user) }
        if (!isNew || activation == null) return
        timeout?.cancel()
        val id = UUID.randomUUID()
        applicantID = id
        respondingID = null
        update { it.copy(applicantDeadline = System.currentTimeMillis() + 10000) }
        scheduleTimeout(id)
        checkFollows(listOf(user.userID))
    }

    private fun resetApplicant() {
        timeout?.cancel()
        timeout = null
        applicantID = null
        respondingID = null
        update { it.copy(applicant = null, applicantDeadline = null) }
    }

    private fun scheduleTimeout(id: UUID) {
        timeout?.cancel()
        timeout = scope?.launch {
            delay(
                ((state.value.applicantDeadline ?: 0) - System.currentTimeMillis()).coerceAtLeast(0)
            )
            expire(id)
        }
    }

    private fun expire(id: UUID) {
        val store = sdk ?: return
        val token = activation ?: return
        val target = state.value.applicant ?: return
        if (applicantID != id || respondingID != null) return
        val current = store.coHostState.applicant.value
        expiredLiveID = target.liveID
        resetApplicant()
        if (current?.liveID == target.liveID && current.userID == target.userID) {
            store.rejectHostConnection(target.liveID, callback(token) { _, _ -> })
        } else applyState()
    }

    private fun refreshFollows(users: List<V2TIMUserFullInfo>?) = onMain {
        val visible = state.value.candidates.map { it.userID }.toSet() + listOfNotNull(state.value.applicant?.userID)
        val ids = users.orEmpty().mapNotNull { it.userID }.filter { it in visible }
        ids.forEach {
            follows.remove(it)
            followRequests.remove(it)
        }
        checkFollows(ids)
    }

    private fun checkFollows(userIDs: List<String>) {
        val token = activation ?: return
        val ids = userIDs.distinct().filter { it.isNotEmpty() && it !in follows && it !in followRequests }
        if (ids.isEmpty()) return
        val batch = ids.take(100)
        val request = UUID.randomUUID()
        batch.forEach { followRequests[it] = request }
        V2TIMManager.getFriendshipManager()
            .checkFollowType(batch, object : V2TIMValueCallback<List<V2TIMFollowTypeCheckResult>> {
                override fun onSuccess(results: List<V2TIMFollowTypeCheckResult>?) = onMain {
                    if (activation != token) return@onMain
                    results.orEmpty().forEach {
                        if (followRequests[it.userID] == request && it.resultCode == 0) follows[it.userID] =
                            when (it.followType) {
                                1 -> FollowStatus.FOLLOWING
                                3 -> FollowStatus.MUTUAL
                                else -> FollowStatus.NOT_FOLLOWING
                            }
                    }
                    batch.filter { followRequests[it] == request }.forEach { followRequests.remove(it) }
                    applyState()
                    checkFollows(ids.drop(100))
                }

                override fun onError(code: Int, desc: String?) = onMain {
                    if (activation != token) return@onMain
                    batch.filter { followRequests[it] == request }.forEach { followRequests.remove(it) }
                    errorResource(R.string.livekit_cohost_follow_query_failed)
                }
            })
    }

    private fun callback(token: UUID, completion: (Boolean, String) -> Unit) = object : CompletionHandler {
        override fun onSuccess() = onMain { if (activation == token) completion(true, "") }
        override fun onFailure(code: Int, desc: String) = onMain {
            if (activation == token) completion(
                false, desc.ifBlank {
                    com.trtc.uikit.livekit.common.ContextProvider.applicationContext?.getString(
                        R.string.livekit_cohost_operation_failed
                    ).orEmpty()
                })
        }
    }

    private fun errorResource(id: Int) {
        mutableErrors.tryEmit(com.trtc.uikit.livekit.common.ContextProvider.applicationContext?.getString(id).orEmpty())
    }

    private fun onMain(token: UUID, action: () -> Unit) {
        scope?.launch { if (activation == token) action() }
    }

    private fun onMain(action: () -> Unit) {
        scope?.launch { action() }
    }

    private fun update(transform: (State) -> State) {
        mutableState.value = transform(state.value)
    }
}
