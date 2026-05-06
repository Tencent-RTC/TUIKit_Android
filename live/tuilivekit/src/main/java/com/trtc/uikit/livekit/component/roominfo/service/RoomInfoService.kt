package com.trtc.uikit.livekit.component.roominfo.service

import android.text.TextUtils
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.tencent.imsdk.v2.V2TIMFollowInfo
import com.tencent.imsdk.v2.V2TIMFollowOperationResult
import com.tencent.imsdk.v2.V2TIMFollowTypeCheckResult
import com.tencent.imsdk.v2.V2TIMFriendshipListener
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.component.roominfo.store.RoomInfoState
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

class RoomInfoService {
    private val logger = LiveKitLogger.getComponentLogger("RoomInfoService")
    val roomInfoState = RoomInfoState()

    fun init(liveInfo: LiveInfo) {
        roomInfoState.roomId = liveInfo.liveID
        roomInfoState.selfUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
        roomInfoState.ownerId.value = liveInfo.liveOwner.userID
        roomInfoState.ownerName.value = liveInfo.liveOwner.userName
        roomInfoState.ownerAvatarUrl.value = liveInfo.liveOwner.avatarURL
        V2TIMManager.getFriendshipManager().addFriendListener(mTIMFriendshipListener)
    }

    fun unInit() {
        V2TIMManager.getFriendshipManager().removeFriendListener(mTIMFriendshipListener)
    }

    fun getFansNumber() {
        val userIDList = listOf(roomInfoState.ownerId.value)

        V2TIMManager.getFriendshipManager().getUserFollowInfo(
            userIDList,
            object : V2TIMValueCallback<List<V2TIMFollowInfo>> {
                override fun onSuccess(v2TIMFollowInfos: List<V2TIMFollowInfo>?) {
                    if (!v2TIMFollowInfos.isNullOrEmpty()) {
                        val result = v2TIMFollowInfos[0]
                        roomInfoState.fansNumber.value = result.followersCount
                    }
                }

                override fun onError(code: Int, desc: String?) {
                    logger.error("getUserFollowInfo failed:errorCode:$code message:$desc")
                    val context = ContextProvider.getApplicationContext()
                    AtomicToast.show(context, "$code,$desc", AtomicToast.Style.ERROR)
                }
            }
        )
    }

    fun checkFollowUserList(userIDList: List<String>) {
        V2TIMManager.getFriendshipManager().checkFollowType(
            userIDList,
            object : V2TIMValueCallback<List<V2TIMFollowTypeCheckResult>> {
                override fun onSuccess(v2TIMFollowTypeCheckResults: List<V2TIMFollowTypeCheckResult>?) {
                    if (!v2TIMFollowTypeCheckResults.isNullOrEmpty()) {
                        val result = v2TIMFollowTypeCheckResults[0]
                        val isAdd =
                            result.followType == V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_MY_FOLLOWING_LIST ||
                                    result.followType == V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_BOTH_FOLLOWERS_LIST
                        updateFollowUserList(result.userID, isAdd)
                    }
                }

                override fun onError(code: Int, desc: String?) {
                    logger.error("checkFollowType failed:errorCode:$code message:$desc")
                    ErrorLocalized.onError(code)
                }
            }
        )
    }

    fun checkFollowUser(userId: String) {
        val userIDList = listOf(userId)
        checkFollowUserList(userIDList)
    }

    fun followUser(userId: String) {
        val userIDList = listOf(userId)
        V2TIMManager.getFriendshipManager().followUser(
            userIDList,
            object : V2TIMValueCallback<List<V2TIMFollowOperationResult>> {
                override fun onSuccess(v2TIMFollowOperationResults: List<V2TIMFollowOperationResult>?) {
                    updateFollowUserList(userId, true)
                    getFansNumber()
                }

                override fun onError(code: Int, desc: String?) {
                    logger.error("followUser failed:errorCode:$code message:$desc")
                    ErrorLocalized.onError(code)
                }
            }
        )
    }

    fun unfollowUser(userId: String) {
        val userIDList = listOf(userId)
        V2TIMManager.getFriendshipManager().unfollowUser(
            userIDList,
            object : V2TIMValueCallback<List<V2TIMFollowOperationResult>> {
                override fun onSuccess(v2TIMFollowOperationResults: List<V2TIMFollowOperationResult>?) {
                    updateFollowUserList(userId, false)
                    getFansNumber()
                }

                override fun onError(code: Int, desc: String?) {
                    logger.error("unfollowUser failed:errorCode:$code message:$desc")
                    ErrorLocalized.onError(code)
                }
            }
        )
    }

    private fun updateFollowUserList(userId: String?, isAdd: Boolean) {
        if (TextUtils.isEmpty(userId)) {
            return
        }

        val followingUserList = roomInfoState.followingList.value.toMutableSet()

        if (isAdd) {
            userId?.let { followingUserList.add(it) }
        } else {
            followingUserList.remove(userId)
        }

        roomInfoState.followingList.value = followingUserList
    }

    private val mTIMFriendshipListener = object : V2TIMFriendshipListener() {
        override fun onMyFollowingListChanged(userInfoList: List<V2TIMUserFullInfo>?, isAdd: Boolean) {
            val userIdList = userInfoList?.map { it.userID } ?: emptyList()
            if (userIdList.isNotEmpty()) {
                checkFollowUserList(userIdList)
            }
        }
    }
}