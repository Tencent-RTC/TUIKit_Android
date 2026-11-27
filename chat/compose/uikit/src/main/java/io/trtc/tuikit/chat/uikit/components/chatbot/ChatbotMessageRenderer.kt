package io.trtc.tuikit.chat.uikit.components.chatbot

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.emojipicker.emojiSizeForTextSize
import io.trtc.tuikit.chat.uikit.components.emojipicker.rememberEmojiText
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlin.math.PI
import kotlin.math.sin

internal class ChatbotMessageRenderer : MessageRenderer {
    @Composable
    override fun Render(message: MessageInfo) {
        val colors = LocalTheme.current.colors
        val data = ChatbotMessageProtocol.parse(message)
        val text = data?.displayText.orEmpty()
        val isLoading = data != null &&
            (data.isPlaceholder ||
                (data.source == ChatbotMessageSource.FLOW && !data.isFinished))

        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (text.isNotEmpty()) {
                val (annotatedString, inlineContent) = rememberEmojiText(text, emojiSize = emojiSizeForTextSize(16.sp))
                Text(
                    text = annotatedString,
                    inlineContent = inlineContent,
                    fontSize = 16.sp,
                    lineHeight = 20.8.sp,
                    color = colors.textColorPrimary
                )
                if (isLoading) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
            if (isLoading) {
                ChatbotLoadingDots(
                    color = colors.textColorSecondary,
                    modifier = Modifier.size(width = 24.dp, height = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatbotLoadingDots(
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "chatbotLoading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing)
        ),
        label = "chatbotLoadingPhase"
    )
    Canvas(modifier = modifier) {
        val radius = 2.dp.toPx()
        val spacing = 7.dp.toPx()
        val totalWidth = spacing * 2f
        val startX = (size.width - totalWidth) / 2f
        val centerY = size.height / 2f
        repeat(3) { index ->
            val wave = (sin((phase - index / 3f) * 2f * PI).toFloat() + 1f) / 2f
            val alpha = (DOT_MIN_ALPHA + wave * (255 - DOT_MIN_ALPHA)) / 255f
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = Offset(startX + spacing * index, centerY)
            )
        }
    }
}

private const val DOT_MIN_ALPHA = 64
