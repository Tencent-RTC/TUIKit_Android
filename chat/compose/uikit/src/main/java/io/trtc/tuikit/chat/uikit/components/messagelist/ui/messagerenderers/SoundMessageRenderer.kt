package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.config.MessageAlignment
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalAudioPlayingState
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalInteractionHandler
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.isMessagePlaying
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messageContentGestures
import io.trtc.tuikit.atomicxcore.api.message.AudioMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlin.math.roundToInt

class SoundMessageRenderer : MessageRenderer {
    @Composable
    override fun Render(message: MessageInfo) {
        val colors = LocalTheme.current.colors
        val messageInteraction = LocalInteractionHandler.current
        val audioPlayingState by LocalAudioPlayingState.current
        val config = LocalMessageListConfig.current
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        val isCurrentMessage = audioPlayingState.playingMessageId == message.msgID
        val isPlaying = audioPlayingState.isMessagePlaying(message.msgID ?: "")
        val duration = (message.messagePayload as? AudioMessagePayload)?.audioDuration ?: 0
        val contentColor = if (message.isSentBySelf) {
            colors.textColorAntiPrimary
        } else {
            colors.textColorPrimary
        }
        val alignEnd = shouldAlignContentEnd(
            alignment = config.alignment,
            isSelf = message.isSentBySelf,
            isRtl = isRtl
        )
        val minWidth = (screenWidthDp * MIN_WIDTH_SCREEN_RATIO)
            .roundToInt()
            .coerceIn(ABSOLUTE_MIN_WIDTH_DP, ABSOLUTE_MAX_WIDTH_DP)
        val maxWidth = (screenWidthDp * MAX_WIDTH_SCREEN_RATIO)
            .roundToInt()
            .coerceIn(minWidth, ABSOLUTE_MAX_WIDTH_DP)
        val clampedDuration = duration.coerceIn(1, MAX_DURATION_SECONDS)
        val bubbleWidth = minWidth + ((maxWidth - minWidth).toFloat() *
            (clampedDuration.toFloat() / MAX_DURATION_SECONDS)).roundToInt()

        LaunchedEffect(message.msgID) {
            messageInteraction.onRendered()
        }

        val containerAlpha = if (isCurrentMessage || !audioPlayingState.isPlaying) 1f else 0.85f
        Row(
            modifier = Modifier
                .width(bubbleWidth.dp)
                .alpha(containerAlpha)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .messageContentGestures(message, messageInteraction),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
        ) {
            val icon = @Composable {
                VoiceWaveIcon(
                    color = contentColor,
                    isPlaying = isPlaying,
                    rotateForSelf = message.isSentBySelf,
                    mirrorRtl = isRtl
                )
            }
            val durationText = @Composable {
                Text(
                    modifier = Modifier.alpha(if (isCurrentMessage) 1f else 0.85f),
                    text = formatVoiceDuration(duration),
                    fontSize = 16.sp,
                    color = contentColor,
                    maxLines = 1
                )
            }
            if (isRtl) {
                durationText()
                Box(modifier = Modifier.padding(start = 4.dp)) { icon() }
            } else {
                icon()
                Box(modifier = Modifier.padding(start = 4.dp)) { durationText() }
            }
        }
    }

    private fun shouldAlignContentEnd(
        alignment: MessageAlignment,
        isSelf: Boolean,
        isRtl: Boolean,
    ): Boolean {
        return when (alignment) {
            MessageAlignment.LEFT -> false
            MessageAlignment.RIGHT -> true
            MessageAlignment.TWO_SIDED -> if (isRtl) !isSelf else isSelf
            else -> if (isRtl) !isSelf else isSelf
        }
    }

    private fun formatVoiceDuration(totalSeconds: Int): String {
        return "${totalSeconds.coerceAtLeast(0)}\""
    }

    private companion object {
        const val MAX_DURATION_SECONDS = 60
        const val ABSOLUTE_MIN_WIDTH_DP = 64
        const val ABSOLUTE_MAX_WIDTH_DP = 260
        const val MIN_WIDTH_SCREEN_RATIO = 0.16f
        const val MAX_WIDTH_SCREEN_RATIO = 0.55f
    }
}

@Composable
private fun VoiceWaveIcon(
    color: Color,
    isPlaying: Boolean,
    rotateForSelf: Boolean,
    mirrorRtl: Boolean,
) {
    val layerRes = listOf(
        R.drawable.message_list_voice_icon_dot,
        R.drawable.message_list_voice_icon_arc_inner,
        R.drawable.message_list_voice_icon_arc_outer,
    )
    val transition = rememberInfiniteTransition(label = "voiceWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = layerRes.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = FRAME_INTERVAL_MS * layerRes.size, easing = LinearEasing)
        ),
        label = "voiceWavePhase"
    )
    val activeIndex = if (isPlaying) phase.toInt() % layerRes.size else -1

    Box(
        modifier = Modifier
            .size(16.dp)
            .scale(scaleX = if (mirrorRtl) -1f else 1f, scaleY = 1f)
            .rotate(if (rotateForSelf) 180f else 0f)
    ) {
        layerRes.forEachIndexed { index, resId ->
            val layerAlpha = if (!isPlaying || index == activeIndex) 1f else DIMMED_ALPHA
            Icon(
                modifier = Modifier
                    .size(16.dp)
                    .alpha(layerAlpha),
                painter = painterResource(resId),
                contentDescription = null,
                tint = color
            )
        }
    }
}

private const val FRAME_INTERVAL_MS = 300
private const val DIMMED_ALPHA = 76f / 255f
