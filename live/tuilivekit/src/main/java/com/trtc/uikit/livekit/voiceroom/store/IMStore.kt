package com.trtc.uikit.livekit.voiceroom.store

import android.text.TextUtils
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.tencent.imsdk.v2.V2TIMFollowOperationResult
import com.tencent.imsdk.v2.V2TIMFollowTypeCheckResult
import com.tencent.imsdk.v2.V2TIMFriendshipListener
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.trtc.uikit.livekit.common.ErrorLocalized
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.LiveKitLogger.Companion.getVoiceRoomLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class IMState(
    val followingUserList: StateFlow<MutableSet<String>>
)

open class IMStore() {
    private val logger: LiveKitLogger = getVoiceRoomLogger("UserManager")

    private val _followingUserList = MutableStateFlow<MutableSet<String>>(LinkedHashSet())

    val imState = IMState(
        followingUserList = _followingUserList
    )

    private val imFriendshipListener = object : V2TIMFriendshipListener() {
        override fun onMyFollowingListChanged(
            userInfoList: List<V2TIMUserFullInfo>,
            isAdd: Boolean
        ) {
            onFollowingListChanged(userInfoList.toMutableList(), isAdd)
        }
    }
    private val imFriendshipManager = V2TIMManager.getFriendshipManager()


    init {
        imFriendshipManager.addFriendListener(imFriendshipListener)
    }

    fun destroy() {
        imFriendshipManager.removeFriendListener(imFriendshipListener)
        _followingUserList.value = mutableSetOf()
    }

    fun follow(userId: String) {
        val userIDList: MutableList<String> = ArrayList<String>()
        userIDList.add(userId)
        imFriendshipManager.followUser(
            userIDList,
            object : V2TIMValueCallback<List<V2TIMFollowOperationResult>> {
                override fun onSuccess(t: List<V2TIMFollowOperationResult>?) {
                    updateFollowUserList(userId, true)
                }

                override fun onError(code: Int, desc: String?) {
                    logger.error("followUser failed:errorCode:message:$desc")
                    ErrorLocalized.onError(code)
                }
            })
    }

    fun unfollow(userId: String) {
        val userIDList: MutableList<String> = ArrayList<String>()
        userIDList.add(userId)
        imFriendshipManager.unfollowUser(
            userIDList,
            object : V2TIMValueCallback<List<V2TIMFollowOperationResult>> {
                override fun onSuccess(t: List<V2TIMFollowOperationResult>?) {
                    updateFollowUserList(userId, false)
                }

                override fun onError(code: Int, desc: String?) {
                    logger.error("unfollowUser failed:errorCode:message:$desc")
                    ErrorLocalized.onError(code)
                }
            })
    }

    fun checkFollowUser(userId: String) {
        val userIDList: MutableList<String> = ArrayList<String>()
        userIDList.add(userId)
        checkFollowUserList(userIDList)
    }

    fun onFollowingListChanged(userInfoList: MutableList<V2TIMUserFullInfo>, isAdd: Boolean) {
        val userIdList: MutableList<String> = ArrayList<String>()
        for (userInfo in userInfoList) {
            userIdList.add(userInfo.userID)
        }
        checkFollowUserList(userIdList)
    }

    private fun checkFollowUserList(userIDList: MutableList<String>) {
        imFriendshipManager.checkFollowType(
            userIDList,
            object : V2TIMValueCallback<List<V2TIMFollowTypeCheckResult>> {
                override fun onSuccess(result: List<V2TIMFollowTypeCheckResult>?) {
                    if (result != null && !result.isEmpty()) {
                        val result = result[0]
                        val isAdd =
                            V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_MY_FOLLOWING_LIST == result.followType
                            || V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_BOTH_FOLLOWERS_LIST == result.followType
                        updateFollowUserList(result.userID, isAdd)
                    }
                }

                override fun onError(code: Int, desc: String?) {
                    logger.error("checkFollowType failed:errorCode:message:$desc")
                    ErrorLocalized.onError(code)
                }
            })
    }

    private fun updateFollowUserList(userId: String, isAdd: Boolean) {
        if (TextUtils.isEmpty(userId)) {
            return
        }
        val userList = _followingUserList.value.toMutableSet()
        if (isAdd) {
            userList.add(userId)
        } else {
            userList.remove(userId)
        }
        _followingUserList.value = userList
    }
}