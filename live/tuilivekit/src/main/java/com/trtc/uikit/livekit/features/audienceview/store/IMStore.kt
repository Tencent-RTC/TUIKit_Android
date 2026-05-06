package com.trtc.uikit.livekit.features.audienceview.store

import android.text.TextUtils
import com.tencent.imsdk.v2.V2TIMFollowOperationResult
import com.tencent.imsdk.v2.V2TIMFollowTypeCheckResult
import com.tencent.imsdk.v2.V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_BOTH_FOLLOWERS_LIST
import com.tencent.imsdk.v2.V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_MY_FOLLOWING_LIST
import com.tencent.imsdk.v2.V2TIMFriendshipListener
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.trtc.uikit.livekit.common.ErrorLocalized
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class IMState(
    val followingUserList: StateFlow<MutableSet<String>>,
)

class IMStore() {

    private val imFriendshipManager = V2TIMManager.getFriendshipManager()
    private val _followingUserList = MutableStateFlow<MutableSet<String>>(LinkedHashSet())
    val imState = IMState(followingUserList = _followingUserList)
    private val imFriendshipListener = object : V2TIMFriendshipListener() {
        override fun onMyFollowingListChanged(
            userInfoList: List<V2TIMUserFullInfo>,
            isAdd: Boolean,
        ) {
            onFollowingListChanged(userInfoList.toMutableList(), isAdd)
        }
    }

    init {
        imFriendshipManager.addFriendListener(imFriendshipListener)
    }

    fun destroy() {
        imFriendshipManager.removeFriendListener(imFriendshipListener)
        _followingUserList.value = mutableSetOf()
    }

    fun followUser(userId: String) {
        val userIDList = ArrayList<String>()
        userIDList.add(userId)
        imFriendshipManager.followUser(
            userIDList,
            object : V2TIMValueCallback<List<V2TIMFollowOperationResult>> {
                override fun onSuccess(v2TIMFollowOperationResults: List<V2TIMFollowOperationResult>) {
                    updateFollowUserList(userId, true)
                }

                override fun onError(code: Int, desc: String) {
                    LOGGER.error("followUser failed:errorCode:message:$desc")
                    ErrorLocalized.onError(code)
                }
            })
    }

    fun unfollowUser(userId: String) {
        val userIDList = ArrayList<String>()
        userIDList.add(userId)
        imFriendshipManager.unfollowUser(
            userIDList,
            object : V2TIMValueCallback<List<V2TIMFollowOperationResult>> {
                override fun onSuccess(v2TIMFollowOperationResults: List<V2TIMFollowOperationResult>) {
                    updateFollowUserList(userId, false)
                }

                override fun onError(code: Int, desc: String) {
                    LOGGER.error("unfollowUser failed:errorCode:message:$desc")
                    ErrorLocalized.onError(code)
                }
            })
    }

    fun checkFollowUser(userId: String) {
        val userIDList = ArrayList<String>()
        userIDList.add(userId)
        checkFollowUserList(userIDList)
    }

    fun onAudienceMessageDisabled(userId: String, isDisable: Boolean) {
        if (userId != LoginStore.shared.loginState.loginUserInfo.value?.userID) {
            return
        }
        ContextProvider.getApplicationContext()?.apply {
            if (isDisable) {
                AtomicToast.show(
                    this,
                    this.resources.getString(R.string.common_send_message_disabled),
                    AtomicToast.Style.INFO
                )
            } else {
                AtomicToast.show(
                    this,
                    this.resources.getString(R.string.common_send_message_enable),
                    AtomicToast.Style.INFO
                )
            }
        }
    }

    private fun checkFollowUserList(userIDList: List<String>) {
        imFriendshipManager.checkFollowType(
            userIDList,
            object : V2TIMValueCallback<List<V2TIMFollowTypeCheckResult>> {
                override fun onSuccess(v2TIMFollowTypeCheckResults: List<V2TIMFollowTypeCheckResult>?) {
                    if (v2TIMFollowTypeCheckResults != null && v2TIMFollowTypeCheckResults.isNotEmpty()) {
                        val result = v2TIMFollowTypeCheckResults[0] ?: return
                        val isAdd = V2TIM_FOLLOW_TYPE_IN_MY_FOLLOWING_LIST == result.followType ||
                                V2TIM_FOLLOW_TYPE_IN_BOTH_FOLLOWERS_LIST == result.followType
                        updateFollowUserList(result.userID, isAdd)
                    }
                }

                override fun onError(code: Int, desc: String) {
                    LOGGER.error("checkFollowType failed:errorCode:message:$desc")
                    ErrorLocalized.onError(code)
                }
            })
    }


    private fun onFollowingListChanged(userInfoList: List<V2TIMUserFullInfo>, isAdd: Boolean) {
        val userIdList = ArrayList<String>()
        for (userInfo in userInfoList) {
            userIdList.add(userInfo.userID)
        }
        checkFollowUserList(userIdList)
    }

    private fun updateFollowUserList(userId: String, isAdd: Boolean) {
        if (TextUtils.isEmpty(userId)) {
            return
        }
        _followingUserList.update { currentSet ->
            val newSet = currentSet.toMutableSet()
            if (isAdd) {
                newSet.add(userId)
            } else {
                newSet.remove(userId)
            }
            newSet
        }
    }

    companion object {
        private val LOGGER = LiveKitLogger.getLiveStreamLogger("UserManager")
    }
}
