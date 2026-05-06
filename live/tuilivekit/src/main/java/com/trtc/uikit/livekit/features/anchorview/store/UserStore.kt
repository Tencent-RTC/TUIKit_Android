package com.trtc.uikit.livekit.features.anchorview.store

import android.text.TextUtils
import com.google.gson.Gson
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.tencent.imsdk.v2.V2TIMFollowOperationResult
import com.tencent.imsdk.v2.V2TIMFollowTypeCheckResult
import com.tencent.imsdk.v2.V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_BOTH_FOLLOWERS_LIST
import com.tencent.imsdk.v2.V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_MY_FOLLOWING_LIST
import com.tencent.imsdk.v2.V2TIMFriendshipListener
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class UserState(
    val followingUserList: StateFlow<LinkedHashSet<String>>,
)

class UserStore() {
    private val logger = LiveKitLogger.getFeaturesLogger("UserManager")
    private val _followingUserList = MutableStateFlow<LinkedHashSet<String>>(LinkedHashSet())
    internal val userState = UserState(
        followingUserList = _followingUserList
    )

    private val imFriendshipListener = object : V2TIMFriendshipListener() {
        override fun onMyFollowingListChanged(userInfoList: List<V2TIMUserFullInfo>, isAdd: Boolean) {
            val userIdList = userInfoList.map { it.userID }
            checkFollowUserList(userIdList)
        }
    }

    fun addObserver() {
        V2TIMManager.getFriendshipManager().addFriendListener(imFriendshipListener)
    }

    fun removeObserver() {
        V2TIMManager.getFriendshipManager().removeFriendListener(imFriendshipListener)
    }

    fun disableSendingMessageByAdmin(userId: String, isDisable: Boolean) {
        LiveAudienceStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).disableSendMessage(
            userId, isDisable,
            object : CompletionHandler {
                override fun onSuccess() {
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("disableSendingMessageByAdmin failed:code:$code,desc:$desc")
                    ErrorLocalized.onError(code)
                }

            })
    }

    fun kickRemoteUserOutOfRoom(userId: String) {
        LiveAudienceStore.create(LiveListStore.shared().liveState.currentLive.value.liveID)
            .kickUserOutOfRoom(userId, object : CompletionHandler {
                override fun onSuccess() {}

                override fun onFailure(code: Int, desc: String) {
                    logger.error("disableSendingMessageByAdmin failed:code:$code,desc:$desc")
                    ErrorLocalized.onError(code)
                }
            })
    }

    fun followUser(userId: String) {
        val userIDList = listOf(userId)
        logger.info("${hashCode()} followUser:[userIDList:$userIDList]")
        V2TIMManager.getFriendshipManager().followUser(
            userIDList,
            object : V2TIMValueCallback<List<V2TIMFollowOperationResult>> {
                override fun onSuccess(results: List<V2TIMFollowOperationResult>) {
                    logger.info("${hashCode()} followUser:[onSuccess:[results:${Gson().toJson(results)}]]")
                    updateFollowUserList(userId, true)
                }

                override fun onError(code: Int, message: String) {
                    logger.error("${hashCode()} followUser:[onSuccess:[code:$code,message:$message]]")
                    ErrorLocalized.onError(code)
                }
            })
    }

    fun unfollowUser(userId: String) {
        val userIDList = listOf(userId)
        logger.info("${hashCode()} unfollowUser:[userIDList:$userIDList]")
        V2TIMManager.getFriendshipManager().unfollowUser(
            userIDList,
            object : V2TIMValueCallback<List<V2TIMFollowOperationResult>> {
                override fun onSuccess(results: List<V2TIMFollowOperationResult>) {
                    logger.info("${hashCode()} unfollowUser:[onSuccess:[results:${Gson().toJson(results)}]]")
                    updateFollowUserList(userId, false)
                }

                override fun onError(code: Int, message: String) {
                    logger.error("${hashCode()} unfollowUser:[onSuccess:[code:$code,message:$message]]")
                    ErrorLocalized.onError(code)
                }
            })
    }

    fun checkFollowUser(userId: String) {
        if (userId == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
            return
        }
        val userIDList = listOf(userId)
        checkFollowUserList(userIDList)
    }

    private fun checkFollowUserList(userIDList: List<String>) {
        logger.info("${hashCode()} checkFollowType:[userIDList:$userIDList]")
        V2TIMManager.getFriendshipManager().checkFollowType(
            userIDList,
            object : V2TIMValueCallback<List<V2TIMFollowTypeCheckResult>> {
                override fun onSuccess(results: List<V2TIMFollowTypeCheckResult>) {
                    logger.info("${hashCode()} checkFollowType:[onSuccess:[results:${Gson().toJson(results)}]]")
                    if (results.isNotEmpty()) {
                        val result = results[0]
                        val isAdd = V2TIM_FOLLOW_TYPE_IN_MY_FOLLOWING_LIST == result.followType ||
                                V2TIM_FOLLOW_TYPE_IN_BOTH_FOLLOWERS_LIST == result.followType
                        updateFollowUserList(result.userID, isAdd)
                    }
                }

                override fun onError(code: Int, message: String) {
                    logger.error("${hashCode()} checkFollowType:[onSuccess:[code:$code,message:$message]]")
                    ErrorLocalized.onError(code)
                }
            })
    }

    private fun updateFollowUserList(userId: String, isAdd: Boolean) {
        if (TextUtils.isEmpty(userId)) {
            return
        }
        val newList = userState.followingUserList.value.toMutableSet().apply {
            if (isAdd) {
                add(userId)
            } else {
                remove(userId)
            }
        }.let { LinkedHashSet(it) }

        _followingUserList.update { newList }
    }
}