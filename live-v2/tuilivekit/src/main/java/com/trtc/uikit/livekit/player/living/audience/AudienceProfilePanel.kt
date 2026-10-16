package com.trtc.uikit.livekit.player.living.audience

import androidx.core.content.ContextCompat
import android.content.Context
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.tencent.imsdk.v2.V2TIMFollowOperationResult
import com.tencent.imsdk.v2.V2TIMFollowTypeCheckResult
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.basic.ErrorLocalized
import com.trtc.uikit.livekit.common.basic.LiveKitLogger
import com.trtc.uikit.livekit.common.basic.setDebounceClickListener
import com.trtc.uikit.livekit.common.theme.avatar.AtomicAvatar
import com.trtc.uikit.livekit.common.theme.label.AtomicLabel
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo

class AudienceProfilePanel(
    context: Context,
    private val member: LiveUserInfo,
) : BottomSheetView(context) {

    private val logger = LiveKitLogger.getComponentLogger("AudienceProfilePanel")
    private val root = LayoutInflater.from(context)
        .inflate(R.layout.livekit_audience_profile_panel, contentView, false) as LinearLayout
    private val avatarView: AtomicAvatar = root.findViewById(R.id.iv_profile_avatar)
    private val nameView: AtomicLabel = root.findViewById(R.id.tv_profile_name)
    private val followButton: AtomicLabel = root.findViewById(R.id.btn_profile_follow)

    private var isFollowing = false

    init {
        contentView.addView(root)
        bindMember()
        followButton.setDebounceClickListener { toggleFollow() }
        checkFollowType()
    }

    private fun bindMember() {
        val userID = member.userID
        val displayName = member.userName.takeIf { it.isNotEmpty() } ?: userID
        val avatarURL = member.avatarURL.orEmpty()
        nameView.text = if (displayName.length > 15) displayName.take(15) + "…" else displayName
        avatarView.setContent(
            AtomicAvatar.AvatarContent.URL(
                url = avatarURL,
                placeImage = R.drawable.livekit_livelist_default_avatar,
            )
        )
    }

    private fun toggleFollow() {
        followButton.isEnabled = false
        if (isFollowing) {
            unfollowUser()
        } else {
            followUser()
        }
    }

    private fun checkFollowType() {
        V2TIMManager.getFriendshipManager().checkFollowType(
            listOf(member.userID.orEmpty()),
            object : V2TIMValueCallback<List<V2TIMFollowTypeCheckResult>> {
                override fun onSuccess(results: List<V2TIMFollowTypeCheckResult>?) {
                    val result = results?.firstOrNull() ?: return
                    isFollowing = isFollowed(result.followType)
                    refreshFollowButton()
                }

                override fun onError(code: Int, desc: String?) {
                    logger.error("checkFollowType failed: code=$code, desc=$desc")
                }
            }
        )
    }

    private fun followUser() {
        V2TIMManager.getFriendshipManager().followUser(
            listOf(member.userID.orEmpty()),
            object : V2TIMValueCallback<List<V2TIMFollowOperationResult>> {
                override fun onSuccess(results: List<V2TIMFollowOperationResult>?) {
                    isFollowing = true
                    followButton.isEnabled = true
                    refreshFollowButton()
                }

                override fun onError(code: Int, desc: String?) {
                    followButton.isEnabled = true
                    logger.error("followUser failed: code=$code, desc=$desc")
                    ErrorLocalized.onError(code)
                }
            }
        )
    }

    private fun unfollowUser() {
        V2TIMManager.getFriendshipManager().unfollowUser(
            listOf(member.userID),
            object : V2TIMValueCallback<List<V2TIMFollowOperationResult>> {
                override fun onSuccess(results: List<V2TIMFollowOperationResult>?) {
                    isFollowing = false
                    followButton.isEnabled = true
                    refreshFollowButton()
                }

                override fun onError(code: Int, desc: String?) {
                    followButton.isEnabled = true
                    logger.error("unfollowUser failed: code=$code, desc=$desc")
                    ErrorLocalized.onError(code)
                }
            }
        )
    }

    private fun refreshFollowButton() {
        if (isFollowing) {
            followButton.setBackgroundResource(R.drawable.livekit_audience_profile_following_bg)
            followButton.elevation = 0f
            followButton.setTextColor(ContextCompat.getColor(context, R.color.livekit_player_profile_following_text))
            followButton.text = context.getString(R.string.common_unfollow_anchor)
        } else {
            followButton.setBackgroundResource(R.drawable.livekit_audience_profile_follow_bg)
            followButton.elevation = 7f * context.resources.displayMetrics.density
            followButton.setTextColor(0xFFFFFFFF.toInt())
            followButton.text = context.getString(R.string.common_follow_anchor)
        }
    }

    private fun isFollowed(followType: Int): Boolean {
        return followType == V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_MY_FOLLOWING_LIST ||
                followType == V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_BOTH_FOLLOWERS_LIST
    }
}
