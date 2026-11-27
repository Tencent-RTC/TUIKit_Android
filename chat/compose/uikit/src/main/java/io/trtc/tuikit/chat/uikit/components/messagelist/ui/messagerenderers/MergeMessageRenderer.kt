package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.emojipicker.emojiSizeForTextSize
import io.trtc.tuikit.chat.uikit.components.emojipicker.rememberEmojiText
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.BubbleStyle
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalInteractionHandler
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalMessageContentMaxWidth
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messageContentGestures
import io.trtc.tuikit.atomicxcore.api.message.MergedMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

class MergeMessageRenderer : MessageRenderer {

    override val renderConfig: MessageRenderConfig = MessageRenderConfig(
        useDefaultBubble = false,
        bubbleStyle = BubbleStyle.CARD
    )

    @Composable
    override fun Render(message: MessageInfo) {
        val colors = LocalTheme.current.colors
        val messageInteraction = LocalInteractionHandler.current
        val mergedPayload = message.messagePayload as? MergedMessagePayload
        val title = mergedPayload?.title.orEmpty()
        val abstractList = mergedPayload?.abstractList.orEmpty()

        val contentMaxWidth = LocalMessageContentMaxWidth.current
        val cardWidth = if (contentMaxWidth != Dp.Unspecified) {
            minOf(238.dp, contentMaxWidth)
        } else {
            238.dp
        }
        Column(
            modifier = Modifier
                .width(cardWidth)
                .messageContentGestures(message, messageInteraction)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.textColorPrimary
            )

            abstractList.take(ABSTRACT_MAX_COUNT).forEachIndexed { index, rawAbstract ->
                MergeAbstractLine(
                    text = rawAbstract,
                    topPadding = if (index == 0) 5.5.dp else 4.dp
                )
            }

            Spacer(
                modifier = Modifier
                    .padding(top = 9.25.dp, bottom = 5.75.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.strokeColorPrimary)
            )

            Text(
                text = stringResource(id = R.string.message_list_forward_chat_record),
                fontSize = 9.sp,
                lineHeight = 14.sp,
                color = colors.textColorTertiary
            )
        }
    }

    private companion object {
        const val ABSTRACT_MAX_COUNT = 4
    }
}

@Composable
private fun MergeAbstractLine(
    text: String,
    topPadding: Dp
) {
    val colors = LocalTheme.current.colors
    val (annotatedString, inlineContent) = rememberEmojiText(text, emojiSize = emojiSizeForTextSize(12.sp), matchNames = true)
    Text(
        modifier = Modifier.padding(top = topPadding),
        text = annotatedString,
        inlineContent = inlineContent,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = colors.textColorTertiary
    )
}
