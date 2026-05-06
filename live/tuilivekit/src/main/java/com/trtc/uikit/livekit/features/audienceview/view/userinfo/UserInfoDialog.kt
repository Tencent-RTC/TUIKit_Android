package com.trtc.uikit.livekit.features.audienceview.view.userinfo

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.tencent.imsdk.v2.V2TIMFollowInfo
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.ui.setDebounceClickListener
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicx.widget.basicwidget.button.AtomicButton
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonColorType
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
class UserInfoDialog(
    private val context: Context,
    private val audienceStore: AudienceStore
) : AtomicPopover(context) {

    private lateinit var buttonFollow: AtomicButton
    private lateinit var textUserName: TextView
    private lateinit var textUserId: TextView
    private lateinit var imageAvatar: AtomicAvatar
    private lateinit var textFans: TextView
    private var userInfo: LiveUserInfo? = null
    private var subscribeStateJob: Job? = null

    init {
        initView()
    }

    fun init(userInfo: LiveUserInfo) {
        this.userInfo = userInfo
        audienceStore.getIMStore().checkFollowUser(userInfo.userID)
        updateView()
    }

    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            audienceStore.getIMState().followingUserList.collect {
                onFollowingUserChanged()
            }
        }
    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun initView() {
        val view = LayoutInflater.from(context).inflate(R.layout.livekit_user_info, null)
        bindViewId(view)
        updateView()
        setContent(view)
    }

    private fun bindViewId(view: View) {
        buttonFollow = view.findViewById(R.id.atomic_btn_follow)
        textUserName = view.findViewById(R.id.tv_anchor_name)
        textUserId = view.findViewById(R.id.tv_user_id)
        imageAvatar = view.findViewById(R.id.iv_avatar)
        textFans = view.findViewById(R.id.tv_fans)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addObserver()
        getFansNumber()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeObserver()
    }

    @SuppressLint("SetTextI18n")
    private fun updateView() {
        val userInfo = this.userInfo ?: return
        if (TextUtils.isEmpty(userInfo.userID)) {
            return
        }
        textUserName.text =
            if (TextUtils.isEmpty(userInfo.userName)) userInfo.userID else userInfo.userName
        textUserId.text = context.getString(R.string.common_user_id, userInfo.userID)
        val avatarUrl = userInfo.avatarURL
        imageAvatar.setContent(AvatarContent.URL(avatarUrl, R.drawable.livekit_ic_avatar))

        refreshFollowButton()
        buttonFollow.setDebounceClickListener { onFollowButtonClick() }
    }

    private fun getFansNumber() {
        val userInfo = this.userInfo ?: return
        if (TextUtils.isEmpty(userInfo.userID)) {
            return
        }
        val userIDList = ArrayList<String>()
        userIDList.add(userInfo.userID)
        V2TIMManager.getFriendshipManager().getUserFollowInfo(
            userIDList,
            object : V2TIMValueCallback<List<V2TIMFollowInfo>> {
                override fun onSuccess(v2TIMFollowInfos: List<V2TIMFollowInfo>?) {
                    if (v2TIMFollowInfos != null && v2TIMFollowInfos.isNotEmpty()) {
                        textFans.text = v2TIMFollowInfos[0].followersCount.toString()
                    }
                }

                override fun onError(code: Int, desc: String) {
                    LOGGER.error("UserInfoDialog getUserFollowInfo failed:errorCode:message:$desc")
                    ErrorLocalized.onError(code)
                }
            })
    }

    private fun refreshFollowButton() {
        val userInfo = this.userInfo ?: return
        val isFollowed = audienceStore.getIMState().followingUserList.value.contains(userInfo.userID)

        if (isFollowed) {
            buttonFollow.apply {
                text = context.getString(R.string.common_unfollow_anchor)
                colorType = ButtonColorType.SECONDARY
            }
        } else {
            buttonFollow.apply {
                text = context.getString(R.string.common_follow_anchor)
                colorType = ButtonColorType.PRIMARY
            }
        }
        getFansNumber()
    }

    private fun onFollowingUserChanged() {
        val userInfo = this.userInfo ?: return
        if (TextUtils.isEmpty(userInfo.userID)) {
            return
        }
        refreshFollowButton()
    }

    private fun onFollowButtonClick() {
        val userInfo = this.userInfo ?: return
        if (audienceStore.getIMState().followingUserList.value.contains(userInfo.userID) == true) {
            audienceStore.getIMStore().unfollowUser(userInfo.userID)
        } else {
            audienceStore.getIMStore().followUser(userInfo.userID)
        }
    }

    companion object {
        private val LOGGER = LiveKitLogger.getLiveStreamLogger("UserInfoDialog")
    }
}
