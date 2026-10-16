package com.trtc.uikit.livekit.pusher.living.audience

import com.trtc.uikit.livekit.common.audience.AudienceWelcome
import com.tencent.imsdk.v2.V2TIMFollowOperationResult
import com.tencent.imsdk.v2.V2TIMFollowTypeCheckResult
import com.tencent.imsdk.v2.V2TIMFriendshipListener
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceListener
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class PusherAudienceStore(liveID: String) {
    data class User(
        val userID: String,
        val userName: String,
        val avatarURL: String,
        val level: Int,
    )

    enum class FollowStatus {
        UNKNOWN,
        NOT_FOLLOWING,
        FOLLOWING,
    }

    enum class Action {
        FOLLOW,
        UNFOLLOW,
        MUTE,
        UNMUTE,
        REMOVE,
    }

    data class State(
        val users: List<User> = emptyList(),
        val count: Int = 0,
        val mutedUserIDs: Set<String> = emptySet(),
        val followStatusByUserID: Map<String, FollowStatus> = emptyMap(),
        val pendingActionByUserID: Map<String, Action> = emptyMap(),
    ) {
        fun followStatus(userID: String): FollowStatus =
            followStatusByUserID[userID] ?: FollowStatus.UNKNOWN
    }

    data class ActionResult(
        val userID: String,
        val action: Action,
        val succeeded: Boolean,
    )

    private val sdkStore = LiveAudienceStore.create(liveID)
    private val mutableState = MutableStateFlow(State())
    private val mutableAudienceEntered = MutableSharedFlow<AudienceWelcome>(extraBufferCapacity = 32)
    private val mutableActionCompleted = MutableSharedFlow<ActionResult>(extraBufferCapacity = 8)
    private val operationIDs = mutableMapOf<String, UUID>()
    private var scope: CoroutineScope? = null
    private var stateJob: Job? = null
    private var activeToken: UUID? = null
    private var friendshipListenerRegistered = false

    val state = mutableState.asStateFlow()
    val audienceEntered = mutableAudienceEntered.asSharedFlow()
    val actionCompleted = mutableActionCompleted.asSharedFlow()

    private val audienceListener = object : LiveAudienceListener() {
        override fun onAudienceJoined(audience: LiveUserInfo) {
            if (activeToken == null || audience.userID.isEmpty()) return
            mutableAudienceEntered.tryEmit(audience.toWelcome())
        }
    }

    private val friendshipListener = object : V2TIMFriendshipListener() {
        override fun onMyFollowingListChanged(
            userInfoList: List<V2TIMUserFullInfo>?,
            isAdd: Boolean,
        ) {
            val changedUserIDs = userInfoList.orEmpty()
                .mapNotNull { it.userID?.takeIf(String::isNotEmpty) }
            scope?.launch {
                val relevantIDs = changedUserIDs.filter {
                    mutableState.value.followStatusByUserID.containsKey(it)
                }
                if (relevantIDs.isNotEmpty()) checkFollowStatus(relevantIDs)
            }
        }
    }

    fun activate() {
        if (activeToken != null) return
        activeToken = UUID.randomUUID()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        sdkStore.addLiveAudienceListener(audienceListener)
        registerFriendshipListener()
        stateJob = scope?.launch {
            launch {
                sdkStore.liveAudienceState.audienceList.collectLatest { users ->
                    updateSdkState(users = users)
                }
            }
            launch {
                sdkStore.liveAudienceState.audienceCount.collectLatest { count ->
                    mutableState.value = mutableState.value.copy(count = count.coerceAtLeast(0))
                }
            }
            launch {
                sdkStore.liveAudienceState.messageBannedUserList.collectLatest { users ->
                    mutableState.value = mutableState.value.copy(
                        mutedUserIDs = users.map(LiveUserInfo::userID).filter(String::isNotEmpty).toSet(),
                    )
                }
            }
        }
    }

    fun deactivate(resetState: Boolean) {
        activeToken = null
        operationIDs.clear()
        sdkStore.removeLiveAudienceListener(audienceListener)
        unregisterFriendshipListener()
        stateJob?.cancel()
        stateJob = null
        scope?.cancel()
        scope = null
        mutableState.value = if (resetState) {
            State()
        } else {
            mutableState.value.copy(
                followStatusByUserID = emptyMap(),
                pendingActionByUserID = emptyMap(),
            )
        }
    }

    fun refresh() {
        if (activeToken == null) return
        sdkStore.fetchAudienceList(null)
    }

    fun refreshFollowStatus(userID: String) {
        if (activeToken == null || userID.isEmpty()) return
        mutableState.value = mutableState.value.copy(
            followStatusByUserID = mutableState.value.followStatusByUserID +
                (userID to FollowStatus.UNKNOWN),
        )
        checkFollowStatus(listOf(userID))
    }

    fun toggleFollow(userID: String) {
        when (mutableState.value.followStatus(userID)) {
            FollowStatus.FOLLOWING -> performIMAction(
                userID,
                Action.UNFOLLOW,
            ) { callback ->
                V2TIMManager.getFriendshipManager().unfollowUser(listOf(userID), callback)
            }
            FollowStatus.NOT_FOLLOWING -> performIMAction(
                userID,
                Action.FOLLOW,
            ) { callback ->
                V2TIMManager.getFriendshipManager().followUser(listOf(userID), callback)
            }
            FollowStatus.UNKNOWN -> refreshFollowStatus(userID)
        }
    }

    fun toggleMessagePermission(userID: String) {
        val isMuted = mutableState.value.mutedUserIDs.contains(userID)
        val action = if (isMuted) Action.UNMUTE else Action.MUTE
        performAtomicAction(userID, action) { callback ->
            sdkStore.disableSendMessage(userID, !isMuted, callback)
        }
    }

    fun removeFromRoom(userID: String) {
        performAtomicAction(userID, Action.REMOVE) { callback ->
            sdkStore.kickUserOutOfRoom(userID, callback)
        }
    }

    private fun updateSdkState(users: List<LiveUserInfo>) {
        mutableState.value = mutableState.value.copy(
            users = users.filter { it.userID.isNotEmpty() }.map(LiveUserInfo::toAudienceUser),
        )
    }

    private fun performAtomicAction(
        userID: String,
        action: Action,
        operation: (CompletionHandler) -> Unit,
    ) {
        val operationID = beginOperation(userID, action) ?: return
        val token = activeToken
        operation(object : CompletionHandler {
            override fun onSuccess() {
                scope?.launch {
                    if (activeToken != token || operationIDs[userID] != operationID) return@launch
                    finishOperation(userID, action, true)
                    sdkStore.fetchAudienceList(null)
                }
            }

            override fun onFailure(code: Int, desc: String) {
                scope?.launch {
                    if (activeToken != token || operationIDs[userID] != operationID) return@launch
                    finishOperation(userID, action, false)
                }
            }
        })
    }

    private fun performIMAction(
        userID: String,
        action: Action,
        operation: (V2TIMValueCallback<List<V2TIMFollowOperationResult>>) -> Unit,
    ) {
        val operationID = beginOperation(userID, action) ?: return
        val token = activeToken
        operation(object : V2TIMValueCallback<List<V2TIMFollowOperationResult>> {
            override fun onSuccess(results: List<V2TIMFollowOperationResult>?) {
                scope?.launch {
                    if (activeToken != token || operationIDs[userID] != operationID) return@launch
                    val status = if (action == Action.FOLLOW) {
                        FollowStatus.FOLLOWING
                    } else {
                        FollowStatus.NOT_FOLLOWING
                    }
                    mutableState.value = mutableState.value.copy(
                        followStatusByUserID = mutableState.value.followStatusByUserID + (userID to status),
                    )
                    finishOperation(userID, action, true)
                }
            }

            override fun onError(code: Int, desc: String?) {
                scope?.launch {
                    if (activeToken != token || operationIDs[userID] != operationID) return@launch
                    finishOperation(userID, action, false)
                }
            }
        })
    }

    private fun beginOperation(userID: String, action: Action): UUID? {
        if (activeToken == null || userID.isEmpty() || operationIDs.containsKey(userID)) return null
        val operationID = UUID.randomUUID()
        operationIDs[userID] = operationID
        mutableState.value = mutableState.value.copy(
            pendingActionByUserID = mutableState.value.pendingActionByUserID + (userID to action),
        )
        return operationID
    }

    private fun finishOperation(userID: String, action: Action, succeeded: Boolean) {
        operationIDs.remove(userID)
        mutableState.value = mutableState.value.copy(
            pendingActionByUserID = mutableState.value.pendingActionByUserID - userID,
        )
        mutableActionCompleted.tryEmit(ActionResult(userID, action, succeeded))
    }

    private fun checkFollowStatus(userIDs: List<String>) {
        val token = activeToken ?: return
        V2TIMManager.getFriendshipManager().checkFollowType(
            userIDs,
            object : V2TIMValueCallback<List<V2TIMFollowTypeCheckResult>> {
                override fun onSuccess(results: List<V2TIMFollowTypeCheckResult>?) {
                    scope?.launch {
                        if (activeToken != token) return@launch
                        val updated = mutableState.value.followStatusByUserID.toMutableMap()
                        results.orEmpty().forEach { result ->
                            val userID = result.userID?.takeIf(String::isNotEmpty) ?: return@forEach
                            updated[userID] = if (result.followType.isFollowing()) {
                                FollowStatus.FOLLOWING
                            } else {
                                FollowStatus.NOT_FOLLOWING
                            }
                        }
                        mutableState.value = mutableState.value.copy(followStatusByUserID = updated)
                    }
                }

                override fun onError(code: Int, desc: String?) = Unit
            },
        )
    }

    private fun registerFriendshipListener() {
        if (friendshipListenerRegistered) return
        V2TIMManager.getFriendshipManager().addFriendListener(friendshipListener)
        friendshipListenerRegistered = true
    }

    private fun unregisterFriendshipListener() {
        if (!friendshipListenerRegistered) return
        V2TIMManager.getFriendshipManager().removeFriendListener(friendshipListener)
        friendshipListenerRegistered = false
    }
}

private fun LiveUserInfo.toAudienceUser() = PusherAudienceStore.User(
    userID = userID,
    userName = userName,
    avatarURL = avatarURL,
    level = level.coerceAtLeast(0),
)

private fun LiveUserInfo.toWelcome() = AudienceWelcome(
    userID = userID,
    userName = userName.ifEmpty { userID },
    level = level.coerceAtLeast(0),
)

private fun Int.isFollowing(): Boolean =
    this == V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_MY_FOLLOWING_LIST ||
        this == V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_BOTH_FOLLOWERS_LIST
