package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.tencent.imsdk.v2.V2TIMGroupListener
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.common.AtomicCallEventPublisher
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

private const val KEY_GROUP_ATTRIBUTE = "inner_attr_kit_info"
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

@Composable
internal fun MessageListJoinCallBanner(
    conversationID: String,
    modifier: Modifier = Modifier
) {
    val groupId = remember(conversationID) { ConversationIDUtil.groupIdOrNull(conversationID) } ?: return
    var state by remember(groupId) { mutableStateOf<MessageListJoinCallBannerState?>(null) }

    DisposableEffect(groupId) {
        val mainHandler = Handler(Looper.getMainLooper())
        fun applyAttributes(attributes: Map<String?, String?>?) {
            val currentUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID
            val parsed = MessageListJoinCallBannerStateParser.parse(
                raw = attributes?.get(KEY_GROUP_ATTRIBUTE),
                currentUserId = currentUserId
            )
            mainHandler.post {
                state = parsed
                if (parsed != null) {
                    fetchJoinCallUserProfiles(parsed) { enriched ->
                        mainHandler.post {
                            if (state?.callId == enriched.callId) {
                                state = enriched
                            }
                        }
                    }
                }
            }
        }

        val listener = object : V2TIMGroupListener() {
            override fun onGroupAttributeChanged(
                groupID: String?,
                groupAttributeMap: MutableMap<String?, String>?
            ) {
                if (groupID.isNullOrEmpty() || groupID != groupId) {
                    return
                }
                applyAttributes(groupAttributeMap)
            }
        }
        V2TIMManager.getInstance().addGroupListener(listener)
        V2TIMManager.getGroupManager().getGroupAttributes(
            groupId,
            listOf(KEY_GROUP_ATTRIBUTE),
            object : V2TIMValueCallback<Map<String?, String?>?> {
                override fun onSuccess(map: Map<String?, String?>?) {
                    applyAttributes(map)
                }

                override fun onError(code: Int, desc: String) {
                    mainHandler.post { state = null }
                }
            }
        )
        onDispose {
            V2TIMManager.getInstance().removeGroupListener(listener)
            mainHandler.removeCallbacksAndMessages(null)
        }
    }

    val current = state ?: return
    JoinCallBannerCard(
        state = current,
        modifier = modifier
    )
}

@Composable
private fun JoinCallBannerCard(
    state: MessageListJoinCallBannerState,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(state.callId) {
        expanded = false
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "joinCallChevron"
    )
    val iconRes = if (state.mediaType.contains("audio", ignoreCase = true) ||
        state.mediaType.contains("voice", ignoreCase = true)
    ) {
        R.drawable.message_list_call_audio_icon
    } else {
        R.drawable.message_list_call_video_icon
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .background(colors.bgColorOperate, RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = colors.buttonColorPrimaryDefault,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = stringResource(R.string.message_list_join_group_call_users, state.users.size),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.textColorPrimary
                )
                JoinCallChevron(
                    color = colors.textColorSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 18.dp, bottom = 30.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
                    ) {
                        state.users.forEach { user ->
                            Avatar(
                                modifier = Modifier.size(52.dp),
                                url = user.avatarUrl,
                                name = user.displayName,
                                size = AvatarSize.M
                            )
                        }
                    }
                    if (state.shouldShowJoinButton) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.strokeColorSecondary)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable {
                                    AtomicCallEventPublisher.publishStartJoin(state.callId)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.message_list_join_group_call),
                                fontSize = 16.sp,
                                color = colors.textColorPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinCallChevron(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        val left = size.width * 0.25f
        val centerX = size.width * 0.5f
        val right = size.width * 0.75f
        val highY = size.height * 0.38f
        val lowY = size.height * 0.62f
        drawLine(color, Offset(left, highY), Offset(centerX, lowY), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(centerX, lowY), Offset(right, highY), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
}

private fun fetchJoinCallUserProfiles(
    state: MessageListJoinCallBannerState,
    onResult: (MessageListJoinCallBannerState) -> Unit
) {
    val userIds = state.users.map { it.id }.filter { it.isNotEmpty() }
    if (userIds.isEmpty()) {
        return
    }
    V2TIMManager.getInstance().getUsersInfo(
        userIds,
        object : V2TIMValueCallback<List<V2TIMUserFullInfo>?> {
            override fun onSuccess(profiles: List<V2TIMUserFullInfo>?) {
                val profileByUserId = profiles.orEmpty().associateBy { it.userID }
                if (profileByUserId.isEmpty()) {
                    return
                }
                onResult(
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
