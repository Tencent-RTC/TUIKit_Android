package com.trtc.uikit.livekit.features.anchorview.store

import android.text.TextUtils
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class CoHostAnchor(
    var roomId: String = "",
    var userId: String = "",
    var userName: String = "",
    var avatarUrl: String = "",
    var joinConnectionTime: Long = 0,
    var connectionStatus: ConnectionStatus = ConnectionStatus.UNKNOWN,
)

enum class ConnectionStatus {
    UNKNOWN,
    INVITING
}

fun convertToConnectionUser(seatUserInfo: SeatUserInfo): CoHostAnchor {
    return CoHostAnchor(
        seatUserInfo.liveID,
        seatUserInfo.userID,
        seatUserInfo.userName,
        seatUserInfo.avatarURL,
        0,
        ConnectionStatus.UNKNOWN,
    )
}

data class AnchorCoHostState(
    val recommendUsers: StateFlow<List<CoHostAnchor>>,
    var isLoadMore: Boolean,
    var isLastPage: Boolean,
    var coHostTemplateId: Int,
)

class AnchorCoHostStore(val liveInfo: LiveInfo) {
    private val _recommendUsers = MutableStateFlow<List<CoHostAnchor>>(arrayListOf())

    internal val coHostState = AnchorCoHostState(
        _recommendUsers,
        isLoadMore = false,
        isLastPage = false,
        coHostTemplateId = 600
    )

    fun isSelfInCoHost(): Boolean {
        val userList = CoHostStore.create(liveInfo.liveID).coHostState.connected.value
        val selfUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: return false
        return userList.any { TextUtils.equals(selfUserId, it.userID) }
    }

    fun getCoHostCandidates(isRefresh: Boolean) {
        val coHostStore = CoHostStore.create(liveInfo.liveID)
        val recommendedCursor = if (isRefresh) {
            ""
        } else {
            coHostStore.coHostState.candidatesCursor.value
        }
        coHostState.isLoadMore = true
        if (isRefresh) {
            coHostState.isLastPage = false
        }
        CoHostStore.create(liveInfo.liveID).getCoHostCandidates(
            recommendedCursor,
            object : CompletionHandler {
                override fun onSuccess() {
                    coHostState.isLoadMore = false
                    coHostState.isLastPage = coHostStore.coHostState.candidatesCursor.value.isEmpty()
                }

                override fun onFailure(code: Int, desc: String) {
                    ErrorLocalized.onError(code)
                    coHostState.isLoadMore = false
                }
            })
    }

    fun isConnected(roomId: String): Boolean {
        val connectedList = CoHostStore.create(liveInfo.liveID).coHostState.connected.value
        return connectedList.any { TextUtils.equals(it.liveID, roomId) }
    }

    fun setCoHostTemplateId(id: Int) {
        coHostState.coHostTemplateId = id
    }

    /******************************************  Observer *******************************************/

    fun onConnectionRequestReject(invitee: SeatUserInfo?) {
        invitee?.let {
            updateRecommendListStatus(it)
            val userName = invitee.userName.ifEmpty {
                invitee.userID
            }
            ContextProvider.getApplicationContext()?.apply {
                AtomicToast.show(
                    this,
                    this.resources.getString(R.string.common_request_rejected, userName),
                    AtomicToast.Style.INFO
                )
            }
        }
    }

    fun onConnectionRequestTimeout(inviter: SeatUserInfo?, invitee: SeatUserInfo?) {
        invitee?.let {
            updateRecommendListStatus(it)
        }

        inviter?.let {
            if (it.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
                ContextProvider.getApplicationContext()?.apply {
                    AtomicToast.show(
                        this,
                        this.resources.getString(R.string.common_connect_invitation_timeout),
                        AtomicToast.Style.INFO
                    )
                }
            }
        }
    }

    private fun isInviting(roomId: String): Boolean {
        val sentRequestList = CoHostStore.create(liveInfo.liveID).coHostState.invitees.value
        return sentRequestList.any { TextUtils.equals(it.liveID, roomId) }
    }

    private fun updateRecommendListStatus(invitee: SeatUserInfo) {
        _recommendUsers.update { list ->
            list.map { user ->
                if (user.roomId == invitee.liveID) {
                    user.copy(connectionStatus = ConnectionStatus.UNKNOWN)
                } else {
                    user
                }
            }
        }
    }

    fun handleCandidatesChange(candidatesList: List<SeatUserInfo>) {
        val list = mutableListOf<CoHostAnchor>()
        val currentConnectionUsers = CoHostStore.create(liveInfo.liveID).coHostState.connected.value
        val currentConnectionUsersLiveIDSet = currentConnectionUsers.map { it.liveID }.toSet()
        val newCandidatesList = (candidatesList.filterNot { it.liveID in currentConnectionUsersLiveIDSet }.map {
            convertToConnectionUser(it)
        })

        for (seatUserInfo in newCandidatesList) {
            if (isInviting(seatUserInfo.roomId)) {
                seatUserInfo.connectionStatus = ConnectionStatus.INVITING
            } else {
                seatUserInfo.connectionStatus = ConnectionStatus.UNKNOWN
            }
            list.add(seatUserInfo)
        }
        _recommendUsers.update { list }
    }

    fun restoreConnectionStatus(roomId: String, connectionStatus: ConnectionStatus) {
        _recommendUsers.update { list ->
            list.map { user ->
                if (user.roomId == roomId) {
                    user.copy(connectionStatus = connectionStatus)
                } else {
                    user
                }
            }.toList()
        }
    }
}
