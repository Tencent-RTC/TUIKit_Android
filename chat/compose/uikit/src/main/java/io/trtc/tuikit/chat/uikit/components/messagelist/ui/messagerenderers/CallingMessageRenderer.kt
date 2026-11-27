package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.common.AtomicCallEventPublisher
import io.trtc.tuikit.chat.uikit.components.messagelist.model.CallMessageModel
import io.trtc.tuikit.chat.uikit.components.messagelist.model.CallStreamMediaType
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalInteractionHandler
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messageContentGestures
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

class CallingMessageRenderer : MessageRenderer {
    @Composable
    override fun Render(message: MessageInfo) {
        val colors = LocalTheme.current.colors
        val context = LocalContext.current
        val messageInteraction = LocalInteractionHandler.current
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val callModel = CallMessageParser.parse(message)
        if (callModel == null) {
            return
        }
        val contentColor = if (message.isSentBySelf) {
            colors.textColorAntiPrimary
        } else {
            colors.textColorPrimary
        }
        val iconRes = when (callModel.streamMediaType) {
            CallStreamMediaType.VOICE -> R.drawable.message_list_call_audio_icon
            CallStreamMediaType.VIDEO -> R.drawable.message_list_call_video_icon
            else -> 0
        }
        val iconAfterText = message.isSentBySelf xor isRtl
        val canReCall = callModel.streamMediaType == CallStreamMediaType.VOICE ||
            callModel.streamMediaType == CallStreamMediaType.VIDEO

        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .messageContentGestures(
                    message = message,
                    handler = messageInteraction,
                    onTap = if (canReCall) {
                        { reInitiateCall(message, callModel) }
                    } else {
                        null
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val text = @Composable {
                Text(
                    text = getCallMessageDisplayString(context, message, callModel),
                    fontSize = 16.sp,
                    lineHeight = 20.8.sp,
                    maxLines = 2,
                    color = contentColor
                )
            }
            val icon = @Composable {
                if (iconRes != 0) {
                    Icon(
                        modifier = Modifier
                            .height(16.dp)
                            .wrapContentWidth()
                            .scale(
                                scaleX = if (shouldMirrorCallIcon(message.isSentBySelf, callModel.streamMediaType, isRtl)) {
                                    -1f
                                } else {
                                    1f
                                },
                                scaleY = 1f
                            ),
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = contentColor
                    )
                }
            }
            if (iconAfterText) {
                text()
                icon()
            } else {
                icon()
                text()
            }
        }
    }

    private fun shouldMirrorCallIcon(
        isSelf: Boolean,
        streamMediaType: CallStreamMediaType,
        isRtl: Boolean,
    ): Boolean {
        val isCallMedia = streamMediaType == CallStreamMediaType.VIDEO ||
            streamMediaType == CallStreamMediaType.VOICE
        return isCallMedia && ((!isSelf) xor isRtl)
    }

    private fun reInitiateCall(
        message: MessageInfo,
        callModel: CallMessageModel
    ) {
        val targetUserId = if (callModel.isCaller) {
            callModel.inviteeList.firstOrNull { it.isNotBlank() } ?: message.to.trim()
        } else {
            callModel.caller.trim()
        }
        if (targetUserId.isBlank()) {
            return
        }
        val mediaType = when (callModel.streamMediaType) {
            CallStreamMediaType.VIDEO -> AtomicCallEventPublisher.MEDIA_TYPE_VIDEO
            else -> AtomicCallEventPublisher.MEDIA_TYPE_AUDIO
        }
        AtomicCallEventPublisher.publishStartCall(
            participantIds = listOf(targetUserId),
            mediaType = mediaType
        )
    }
}

internal object CallMessageDisplayPolicy {
    fun shouldShowOutsideUnreadDot(
        isSelf: Boolean,
        isShowUnreadPoint: Boolean
    ): Boolean {
        return isShowUnreadPoint && !isSelf
    }
}
