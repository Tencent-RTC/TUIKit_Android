package io.trtc.tuikit.chat.uikit.components.messagelist.ui
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.cardview.widget.CardView
import com.google.gson.Gson
import com.tencent.imsdk.v2.V2TIMGroupListener
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.AtomicCallEventPublisher
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

internal class MessageListJoinCallBannerController(
    private val context: Context,
    private val container: FrameLayout
) {
    private var currentGroupId: String? = null
    private var currentCallId: String? = null
    private var bannerView: MessageListJoinCallBannerView? = null

    private val groupListener = object : V2TIMGroupListener() {
        override fun onGroupAttributeChanged(groupID: String?, groupAttributeMap: MutableMap<String?, String>?) {
            if (groupID.isNullOrEmpty() || groupID != currentGroupId) {
                return
            }
            render(groupAttributeMap)
        }
    }

    fun bind(conversationID: String) {
        val groupId = ConversationIDUtil.groupIdOrNull(conversationID)
        if (groupId == currentGroupId) {
            return
        }
        release()
        if (groupId == null) {
            hide()
            return
        }
        currentGroupId = groupId
        V2TIMManager.getInstance().addGroupListener(groupListener)
        V2TIMManager.getGroupManager().getGroupAttributes(
            groupId,
            listOf(KEY_GROUP_ATTRIBUTE),
            object : V2TIMValueCallback<Map<String?, String?>?> {
                override fun onSuccess(map: Map<String?, String?>?) {
                    if (groupId != currentGroupId) {
                        return
                    }
                    render(map)
                }

                override fun onError(code: Int, desc: String) {
                    if (groupId != currentGroupId) {
                        return
                    }
                    hide()
                }
            }
        )
    }

    fun release() {
        V2TIMManager.getInstance().removeGroupListener(groupListener)
        currentGroupId = null
        hide()
    }

    private fun render(attributes: Map<String?, String?>?) {
        val currentUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID
        val state = MessageListJoinCallBannerStateParser.parse(
            raw = attributes?.get(KEY_GROUP_ATTRIBUTE),
            currentUserId = currentUserId
        )
        if (state == null || state.callId.isEmpty()) {
            hide()
            return
        }
        currentCallId = state.callId
        val view = bannerView ?: MessageListJoinCallBannerView(context).also {
            bannerView = it
            container.addView(
                it,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        view.bind(state)
        container.visibility = View.VISIBLE
        fetchUserProfiles(state, view)
    }

    private fun hide() {
        currentCallId = null
        container.visibility = View.GONE
        bannerView?.bind(null)
    }

    private fun fetchUserProfiles(
        state: MessageListJoinCallBannerState,
        view: MessageListJoinCallBannerView
    ) {
        val userIds = state.users.map { it.id }.filter { it.isNotEmpty() }
        if (userIds.isEmpty()) {
            return
        }
        V2TIMManager.getInstance().getUsersInfo(
            userIds,
            object : V2TIMValueCallback<List<V2TIMUserFullInfo>?> {
                override fun onSuccess(profiles: List<V2TIMUserFullInfo>?) {
                    if (currentCallId != state.callId || container.visibility != View.VISIBLE || bannerView !== view) {
                        return
                    }
                    val profileByUserId = profiles.orEmpty().associateBy { it.userID }
                    if (profileByUserId.isEmpty()) {
                        return
                    }
                    view.bind(
                        state.copy(
                            users = state.users.map { user ->
                                profileByUserId[user.id]?.let { profile ->
                                    user.copy(
                                        displayName = profile.nickName?.takeIf { it.isNotBlank() } ?: user.displayName,
                                        avatarUrl = profile.faceUrl?.takeIf { it.isNotBlank() } ?: user.avatarUrl
                                    )
                                } ?: user
                            }
                        )
                    )
                }

                override fun onError(code: Int, desc: String) = Unit
            }
        )
    }
}

private class MessageListJoinCallBannerView(context: Context) : LinearLayout(context) {
    private val themeColors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    private val card: CardView
    private val cardContent: LinearLayout
    private val headerRow: LinearLayout
    private val hintView: TextView
    private val callIcon: ImageView
    private val chevronView: ChevronView
    private val expandedContent: LinearLayout
    private val avatarRecyclerView: MaxWidthRecyclerView
    private val avatarAdapter = CallParticipantAvatarAdapter()
    private val divider: View
    private val joinButton: TextView
    private var boundCallId: String? = null
    private var isExpanded: Boolean = false
    private var expandAnimator: ValueAnimator? = null

    init {
        orientation = VERTICAL
        setPadding(10.dp, 6.dp, 10.dp, 8.dp)

        card = CardView(context).apply {
            radius = 12.dp.toFloat()
            cardElevation = 4.dp.toFloat()
            maxCardElevation = 4.dp.toFloat()
            setCardBackgroundColor(themeColors.bgColorOperate)
            useCompatPadding = true
            preventCornerOverlap = true
        }
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        cardContent = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        card.addView(cardContent, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp, 12.dp, 14.dp, 12.dp)
            isClickable = true
            setOnClickListener {
                setExpanded(!isExpanded)
            }
        }
        cardContent.addView(headerRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        callIcon = ImageView(context).apply {
            imageTintList = ColorStateList.valueOf(themeColors.buttonColorPrimaryDefault)
        }
        headerRow.addView(callIcon, LayoutParams(22.dp, 22.dp))

        hintView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(themeColors.textColorPrimary)
            maxLines = 1
        }
        headerRow.addView(
            hintView,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12.dp
                marginEnd = 12.dp
            }
        )

        chevronView = ChevronView(context).apply {
            setChevronColor(themeColors.textColorSecondary)
        }
        headerRow.addView(chevronView, LayoutParams(20.dp, 20.dp))

        expandedContent = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = GONE
            alpha = 0f
        }
        cardContent.addView(expandedContent, LayoutParams(LayoutParams.MATCH_PARENT, 0))

        avatarRecyclerView = MaxWidthRecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = avatarAdapter
            itemAnimator = DefaultItemAnimator().apply {
                supportsChangeAnimations = false
            }
            addItemDecoration(HorizontalSpacingDecoration(AVATAR_SPACING_DP.dp))
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            maxViewWidth = avatarListViewportWidth()
            setPadding(0, 18.dp, 0, 30.dp)
            clipToPadding = false
        }
        expandedContent.addView(
            avatarRecyclerView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )

        divider = View(context).apply {
            setBackgroundColor(themeColors.strokeColorSecondary)
        }
        expandedContent.addView(divider, LayoutParams(LayoutParams.MATCH_PARENT, 1))

        joinButton = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(themeColors.textColorPrimary)
        }
        expandedContent.addView(joinButton, LayoutParams(LayoutParams.MATCH_PARENT, 48.dp))
    }

    fun bind(state: MessageListJoinCallBannerState?) {
        if (state == null) {
            boundCallId = null
            setExpanded(false, animate = false)
            avatarAdapter.submitUsers(emptyList())
            joinButton.setOnClickListener(null)
            return
        }
        val isNewCall = state.callId != boundCallId
        if (isNewCall) {
            boundCallId = state.callId
            setExpanded(false, animate = false)
        }

        callIcon.setImageResource(callIconRes(state.mediaType))
        avatarAdapter.submitUsers(state.users) {
            if (isNewCall) {
                avatarRecyclerView.scrollToPosition(0)
            }
        }
        hintView.text = context.getString(R.string.message_list_join_group_call_users, state.users.size)
        joinButton.text = context.getString(R.string.message_list_join_group_call)
        joinButton.visibility = if (state.shouldShowJoinButton) VISIBLE else GONE
        divider.visibility = joinButton.visibility
        joinButton.setOnClickListener {
            AtomicCallEventPublisher.publishStartJoin(state.callId)
        }
    }

    private fun callIconRes(mediaType: String): Int {
        return if (mediaType.contains("audio", ignoreCase = true) ||
            mediaType.contains("voice", ignoreCase = true)
        ) {
            R.drawable.message_list_call_audio_icon
        } else {
            R.drawable.message_list_call_video_icon
        }
    }

    private fun setExpanded(expanded: Boolean, animate: Boolean = true) {
        if (isExpanded == expanded && animate) {
            return
        }
        isExpanded = expanded
        chevronView.setExpanded(expanded, animate)
        expandAnimator?.cancel()

        val targetHeight = if (expanded) measureExpandedContentHeight() else 0
        val startHeight = expandedContent.layoutParams.height.takeIf { it >= 0 } ?: expandedContent.height
        if (!animate || !isLaidOut) {
            expandedContent.visibility = if (expanded) VISIBLE else GONE
            expandedContent.alpha = if (expanded) 1f else 0f
            expandedContent.layoutParams = expandedContent.layoutParams.apply {
                height = if (expanded) LayoutParams.WRAP_CONTENT else 0
            }
            requestLayout()
            return
        }

        if (expanded) {
            expandedContent.visibility = VISIBLE
            expandedContent.alpha = 0f
        }
        expandAnimator = ValueAnimator.ofInt(startHeight, targetHeight).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val animatedHeight = animator.animatedValue as Int
                val fraction = animator.animatedFraction
                expandedContent.layoutParams = expandedContent.layoutParams.apply {
                    height = animatedHeight
                }
                expandedContent.alpha = if (expanded) fraction else 1f - fraction
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    expandAnimator = null
                    expandedContent.visibility = if (expanded) VISIBLE else GONE
                    expandedContent.alpha = if (expanded) 1f else 0f
                    expandedContent.layoutParams = expandedContent.layoutParams.apply {
                        height = if (expanded) LayoutParams.WRAP_CONTENT else 0
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    expandAnimator = null
                }
            })
            start()
        }
    }

    private fun measureExpandedContentHeight(): Int {
        val availableWidth = (cardContent.width.takeIf { it > 0 } ?: width).coerceAtLeast(0)
        if (availableWidth == 0) {
            return 0
        }
        expandedContent.measure(
            MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        return expandedContent.measuredHeight
    }

    private fun avatarListViewportWidth(): Int {
        return AVATAR_SIZE_DP.dp * MAX_VISIBLE_AVATAR_COUNT +
            AVATAR_SPACING_DP.dp * (MAX_VISIBLE_AVATAR_COUNT - 1)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}

internal data class MessageListJoinCallBannerState(
    val callId: String,
    val mediaType: String,
    val users: List<MessageListJoinCallBannerUser>,
    val isCurrentUserJoined: Boolean
) {
    val shouldShowJoinButton: Boolean
        get() = !isCurrentUserJoined
}

internal data class MessageListJoinCallBannerUser(
    val id: String,
    val displayName: String,
    val avatarUrl: String?
)

internal object MessageListJoinCallBannerStateParser {
    private const val KEY_BUSINESS_TYPE = "business_type"
    private const val KEY_CALL_ID = "call_id"
    private const val KEY_CALL_MEDIA_TYPE = "call_media_type"
    private const val KEY_USER_LIST = "user_list"
    private const val KEY_USER_ID = "userid"
    private const val KEY_USER_ID_ALT = "userID"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_NAME = "name"
    private const val KEY_AVATAR_URL = "avatar_url"
    private const val KEY_FACE_URL = "faceUrl"
    private const val VALUE_BUSINESS_TYPE = "callkit"

    fun parse(raw: String?, currentUserId: String?): MessageListJoinCallBannerState? {
        if (raw.isNullOrEmpty()) {
            return null
        }
        val map = runCatching {
            @Suppress("UNCHECKED_CAST")
            Gson().fromJson(raw, Map::class.java) as Map<String, Any?>
        }.getOrNull() ?: return null

        val businessType = map[KEY_BUSINESS_TYPE] as? String
        val callId = map[KEY_CALL_ID] as? String
        val mediaType = map[KEY_CALL_MEDIA_TYPE] as? String
        val users = parseUsers(map[KEY_USER_LIST])
        if (businessType != VALUE_BUSINESS_TYPE || callId.isNullOrEmpty() ||
            mediaType.isNullOrEmpty() || users.size <= 1
        ) {
            return null
        }

        val isCurrentUserJoined = !currentUserId.isNullOrEmpty() && users.any { it.id == currentUserId }
        return MessageListJoinCallBannerState(
            callId = callId,
            mediaType = mediaType,
            users = users,
            isCurrentUserJoined = isCurrentUserJoined
        )
    }

    private fun parseUsers(value: Any?): List<MessageListJoinCallBannerUser> {
        val list = value as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val userMap = item as? Map<*, *> ?: return@mapNotNull null
            val userId = userMap[KEY_USER_ID] as? String
                ?: userMap[KEY_USER_ID_ALT] as? String
                ?: return@mapNotNull null
            if (userId.isEmpty()) {
                return@mapNotNull null
            }
            val displayName = userMap[KEY_NICKNAME] as? String
                ?: userMap[KEY_NAME] as? String
                ?: userId
            MessageListJoinCallBannerUser(
                id = userId,
                displayName = displayName,
                avatarUrl = userMap[KEY_AVATAR_URL] as? String ?: userMap[KEY_FACE_URL] as? String
            )
        }
    }
}

private class MaxWidthRecyclerView(context: Context) : RecyclerView(context) {
    var maxViewWidth: Int = Int.MAX_VALUE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val cappedWidth = widthSize.coerceAtMost(maxViewWidth)
        val cappedWidthSpec = MeasureSpec.makeMeasureSpec(cappedWidth, widthMode)
        super.onMeasure(cappedWidthSpec, heightMeasureSpec)
    }
}

private class CallParticipantAvatarAdapter : RecyclerView.Adapter<CallParticipantAvatarViewHolder>() {
    private val users = mutableListOf<MessageListJoinCallBannerUser>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallParticipantAvatarViewHolder {
        val avatarSize = (AVATAR_SIZE_DP * parent.resources.displayMetrics.density).toInt()
        val avatar = Avatar(parent.context).apply {
            setSize(Avatar.AvatarSize.M)
            layoutParams = RecyclerView.LayoutParams(avatarSize, avatarSize)
        }
        return CallParticipantAvatarViewHolder(avatar)
    }

    override fun onBindViewHolder(holder: CallParticipantAvatarViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    fun submitUsers(newUsers: List<MessageListJoinCallBannerUser>, onCommitted: (() -> Unit)? = null) {
        val oldUsers = users.toList()
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldUsers.size
            override fun getNewListSize(): Int = newUsers.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldUsers[oldItemPosition].id == newUsers[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldUsers[oldItemPosition] == newUsers[newItemPosition]
            }
        })
        users.clear()
        users.addAll(newUsers)
        diffResult.dispatchUpdatesTo(this)
        onCommitted?.invoke()
    }
}

private class CallParticipantAvatarViewHolder(
    private val avatar: Avatar
) : RecyclerView.ViewHolder(avatar) {
    fun bind(user: MessageListJoinCallBannerUser) {
        avatar.setContent(
            Avatar.AvatarContent.Image(
                url = user.avatarUrl.orEmpty(),
                fallbackName = user.displayName
            )
        )
    }
}

private class HorizontalSpacingDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        outRect.left = if (position > 0) spacing else 0
    }
}

private class ChevronView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f * resources.displayMetrics.density
    }

    fun setChevronColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun setExpanded(expanded: Boolean, animate: Boolean) {
        val targetRotation = if (expanded) 180f else 0f
        if (animate && isLaidOut) {
            animate()
                .rotation(targetRotation)
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            rotation = targetRotation
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = width * 0.25f
        val centerX = width * 0.5f
        val right = width * 0.75f
        val highY = height * 0.38f
        val lowY = height * 0.62f
        canvas.drawLine(left, highY, centerX, lowY, paint)
        canvas.drawLine(centerX, lowY, right, highY, paint)
    }
}

private const val KEY_GROUP_ATTRIBUTE = "inner_attr_kit_info"
private const val AVATAR_SIZE_DP = 52
private const val AVATAR_SPACING_DP = 14
private const val MAX_VISIBLE_AVATAR_COUNT = 4
