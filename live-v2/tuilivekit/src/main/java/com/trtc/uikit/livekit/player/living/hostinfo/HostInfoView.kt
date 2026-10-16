package com.trtc.uikit.livekit.player.living.hostinfo

import androidx.core.content.ContextCompat
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.tencent.imsdk.v2.V2TIMFollowOperationResult
import com.tencent.imsdk.v2.V2TIMFollowTypeCheckResult
import com.tencent.imsdk.v2.V2TIMFriendshipListener
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.basic.ErrorLocalized
import com.trtc.uikit.livekit.common.basic.LiveKitLogger
import com.trtc.uikit.livekit.common.basic.ScreenUtil
import com.trtc.uikit.livekit.common.basic.setDebounceClickListener
import com.trtc.uikit.livekit.common.theme.avatar.AtomicAvatar
import com.trtc.uikit.livekit.common.theme.label.AtomicLabel
import io.trtc.tuikit.atomicxcore.api.live.LikeStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HostInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val logger = LiveKitLogger.getComponentLogger("HostInfoView")

    private val ivAnchorAvatar: AtomicAvatar
    private val tvAnchorName: AtomicLabel
    private val tvLikeCount: AtomicLabel
    private val btnFollow: TextView

    private var viewScope: CoroutineScope? = null
    private var likeJob: Job? = null
    private var currentLiveID: String? = null
    private var currentOwnerID: String? = null
    private val followingSet: MutableSet<String> = mutableSetOf()
    private var friendListenerRegistered = false
    private var isNameTruncatable: Boolean = false

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_host_info_view, this, true)
        ivAnchorAvatar = findViewById(R.id.iv_anchor_avatar)
        tvAnchorName = findViewById(R.id.tv_anchor_name)
        tvLikeCount = findViewById(R.id.tv_like)
        btnFollow = findViewById(R.id.btn_follow)
        setupCapsuleBackground()
        renderLikeCount(0L)
        btnFollow.setDebounceClickListener { onFollowButtonClick() }
    }

    private fun setupCapsuleBackground() {
        setBackgroundResource(R.drawable.livekit_host_info_capsule_bg)
        val paddingV = ScreenUtil.dip2px(2f)
        val paddingStart = ScreenUtil.dip2px(2f)
        val paddingEnd = ScreenUtil.dip2px(8f)
        setPaddingRelative(paddingStart, paddingV, paddingEnd, paddingV)
        minHeight = ScreenUtil.dip2px(36f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxContainerWidth = ScreenUtil.getScreenWidth(context) / 2 - ScreenUtil.dip2px(16f)
        tvAnchorName.maxWidth = Int.MAX_VALUE
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (isNameTruncatable && measuredWidth > maxContainerWidth) {
            val extraWidth = measuredWidth - tvAnchorName.measuredWidth
            val nameMaxWidth = (maxContainerWidth - extraWidth).coerceAtLeast(0)
            tvAnchorName.maxWidth = nameMaxWidth
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    fun bind(liveInfo: LiveInfo) {
        logger.info("HostInfoView-bind liveInfo: $liveInfo")
        renderAnchor(liveInfo)
        bindFollowState(liveInfo)
    }

    fun activateLikeCount(liveID: String) {
        if (liveID.isEmpty()) {
            deactivateLikeCount()
            return
        }
        currentLiveID = liveID
        subscribeLikeCount(liveID)
    }

    fun deactivateLikeCount() {
        likeJob?.cancel()
        likeJob = null
        currentLiveID = null
        renderLikeCount(0L)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope = scope
        registerFriendListener()
        currentLiveID?.let(::subscribeLikeCount)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        likeJob?.cancel()
        likeJob = null
        viewScope?.cancel()
        viewScope = null
        currentLiveID = null
        currentOwnerID = null
        followingSet.clear()
        unregisterFriendListener()
    }

    private fun subscribeLikeCount(liveID: String) {
        likeJob?.cancel()
        likeJob = null
        val scope = viewScope ?: return
        val store = LikeStore.create(liveID)
        likeJob = scope.launch {
            store.likeState.totalLikeCount.collect { count ->
                if (currentLiveID == liveID) {
                    renderLikeCount(count)
                }
            }
        }
    }

    private fun renderAnchor(liveInfo: LiveInfo) {
        val owner = liveInfo.liveOwner
        val displayName = owner.userName.ifEmpty { owner.userID }
        isNameTruncatable = displayName.length > 7
        tvAnchorName.text = if (isNameTruncatable) displayName.take(7) + "…" else displayName

        val avatarURL = owner.avatarURL
        ivAnchorAvatar.setContent(
            AtomicAvatar.AvatarContent.URL(
                url = avatarURL,
                placeImage = R.drawable.livekit_livelist_default_avatar,
            )
        )
    }

    private fun renderLikeCount(count: Long) {
        tvLikeCount.text = context.getString(
            R.string.livekit_host_info_total_like_count,
            formatLikeCount(count)
        )
    }

    private fun formatLikeCount(count: Long): String {
        val safeCount = if (count < 0) 0L else count
        return safeCount.toString()
    }

    private fun bindFollowState(liveInfo: LiveInfo) {
        val ownerID = liveInfo.liveOwner.userID
        val selfID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        if (ownerID.isEmpty() || ownerID == selfID) {
            currentOwnerID = ownerID.takeIf { it.isNotEmpty() }
            btnFollow.visibility = View.GONE
            return
        }
        currentOwnerID = ownerID
        btnFollow.visibility = View.VISIBLE
        refreshFollowButton()
        checkFollowUser(ownerID)
    }

    private fun onFollowButtonClick() {
        val ownerID = currentOwnerID?.takeIf { it.isNotEmpty() } ?: return
        if (followingSet.contains(ownerID)) {
            unfollowUser(ownerID)
        } else {
            followUser(ownerID)
        }
    }

    private fun refreshFollowButton() {
        val ownerID = currentOwnerID
        val isFollowed = ownerID != null && followingSet.contains(ownerID)
        if (isFollowed) {
            btnFollow.setBackgroundResource(R.drawable.livekit_player_followed_bg)
            btnFollow.setTextColor(ContextCompat.getColor(context, R.color.livekit_player_host_following_text))
            btnFollow.text = context.getString(R.string.common_unfollow_anchor)
        } else {
            btnFollow.setBackgroundResource(R.drawable.livekit_player_follow_bg)
            btnFollow.setTextColor(0xFFFFFFFF.toInt())
            btnFollow.text = context.getString(R.string.common_follow_anchor)
        }
    }

    private fun checkFollowUser(userID: String) {
        V2TIMManager.getFriendshipManager().checkFollowType(
            listOf(userID),
            object : V2TIMValueCallback<List<V2TIMFollowTypeCheckResult>> {
                override fun onSuccess(results: List<V2TIMFollowTypeCheckResult>?) {
                    val result = results?.firstOrNull() ?: return
                    val isFollowed =
                        result.followType == V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_MY_FOLLOWING_LIST ||
                                result.followType == V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_BOTH_FOLLOWERS_LIST
                    updateFollowingSet(result.userID, isFollowed)
                }

                override fun onError(code: Int, desc: String?) {
                    logger.error("checkFollowType failed: code=$code, desc=$desc")
                }
            }
        )
    }

    private fun followUser(userID: String) {
        V2TIMManager.getFriendshipManager().followUser(
            listOf(userID),
            object : V2TIMValueCallback<List<V2TIMFollowOperationResult>> {
                override fun onSuccess(results: List<V2TIMFollowOperationResult>?) {
                    updateFollowingSet(userID, true)
                }

                override fun onError(code: Int, desc: String?) {
                    logger.error("followUser failed: code=$code, desc=$desc")
                    ErrorLocalized.onError(code)
                }
            }
        )
    }

    private fun unfollowUser(userID: String) {
        V2TIMManager.getFriendshipManager().unfollowUser(
            listOf(userID),
            object : V2TIMValueCallback<List<V2TIMFollowOperationResult>> {
                override fun onSuccess(results: List<V2TIMFollowOperationResult>?) {
                    updateFollowingSet(userID, false)
                }

                override fun onError(code: Int, desc: String?) {
                    logger.error("unfollowUser failed: code=$code, desc=$desc")
                    ErrorLocalized.onError(code)
                }
            }
        )
    }

    private fun updateFollowingSet(userID: String?, isFollowed: Boolean) {
        val id = userID?.takeIf { it.isNotEmpty() } ?: return
        val changed = if (isFollowed) followingSet.add(id) else followingSet.remove(id)
        if (changed && id == currentOwnerID) {
            refreshFollowButton()
        }
    }

    private fun registerFriendListener() {
        if (friendListenerRegistered) return
        V2TIMManager.getFriendshipManager().addFriendListener(friendshipListener)
        friendListenerRegistered = true
    }

    private fun unregisterFriendListener() {
        if (!friendListenerRegistered) return
        V2TIMManager.getFriendshipManager().removeFriendListener(friendshipListener)
        friendListenerRegistered = false
    }

    private val friendshipListener = object : V2TIMFriendshipListener() {
        override fun onMyFollowingListChanged(
            userInfoList: List<V2TIMUserFullInfo>?,
            isAdd: Boolean,
        ) {
            val userIDs = userInfoList?.mapNotNull { it.userID?.takeIf { id -> id.isNotEmpty() } }
                ?: emptyList()
            if (userIDs.isEmpty()) return
            V2TIMManager.getFriendshipManager().checkFollowType(
                userIDs,
                object : V2TIMValueCallback<List<V2TIMFollowTypeCheckResult>> {
                    override fun onSuccess(results: List<V2TIMFollowTypeCheckResult>?) {
                        results?.forEach { result ->
                            val followed =
                                result.followType == V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_MY_FOLLOWING_LIST ||
                                        result.followType == V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_BOTH_FOLLOWERS_LIST
                            updateFollowingSet(result.userID, followed)
                        }
                    }

                    override fun onError(code: Int, desc: String?) {
                        logger.error("checkFollowType(listener) failed: code=$code, desc=$desc")
                    }
                }
            )
        }
    }
}
