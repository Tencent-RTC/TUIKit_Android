package io.trtc.tuikit.chat.uikit.components.conversationlist.ui

import android.content.res.ColorStateList
import android.widget.ProgressBar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.conversationlist.utils.ConversationListTimeFormatter
import io.trtc.tuikit.chat.uikit.components.conversationlist.utils.needShowNotReceiveIcon
import io.trtc.tuikit.chat.uikit.components.emojipicker.emojiSizeForTextSize
import io.trtc.tuikit.chat.uikit.components.emojipicker.rememberEmojiText
import io.trtc.tuikit.chat.uikit.components.messagelist.model.CallStreamMediaType
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageUtils
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.GroupAtType
import io.trtc.tuikit.atomicxcore.api.conversation.ReceiveMessageOption
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus

@Composable
fun RowScope.ConversationContent() {
    val conversationInfo = LocalConversation.current
    val colors = LocalTheme.current.colors

    val abstract = MessageUtils.getMessageAbstract(conversationInfo.lastMessage, conversationInfo.conversationID)
    val subtitleSource = conversationInfo.draft?.takeIf { it.isNotEmpty() } ?: abstract
    val (emojiSubtitle, emojiInlineContent) = rememberEmojiText(subtitleSource, emojiSize = emojiSizeForTextSize(14.sp))
    val callMediaType = if (conversationInfo.draft.isNullOrEmpty()) {
        MessageUtils.getCallStreamMediaType(conversationInfo.lastMessage, conversationInfo.conversationID)
    } else {
        null
    }

    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = conversationInfo.title ?: conversationInfo.conversationID,
            color = colors.textColorPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = conversationListTextStyle(18.sp)
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (conversationInfo.lastMessage?.status) {
                MessageStatus.SEND_FAIL, MessageStatus.VIOLATION -> {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(color = colors.textColorError, shape = CircleShape)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "!",
                            color = colors.textColorButton,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            style = conversationListTextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                includeFontPadding = false,
                            )
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                MessageStatus.SENDING -> {
                    SendingProgressIndicator(color = colors.textColorAntiSecondary)
                    Spacer(Modifier.width(4.dp))
                }
                else -> Unit
            }
            Text(
                text = buildSubTitleAnnotatedString(
                    conversationInfo = conversationInfo,
                    emojiSubtitle = emojiSubtitle,
                    showCallIcon = callMediaType != null,
                ),
                modifier = Modifier.weight(1f, fill = false),
                color = colors.textColorSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                inlineContent = emojiInlineContent + callIconInlineContent(callMediaType),
                style = conversationListTextStyle(14.sp)
            )
        }
    }
    Spacer(modifier = Modifier.width(16.dp))

    Box(
        modifier = Modifier
            .fillMaxHeight()
    ) {
        Text(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 13.dp),
            text = ConversationListTimeFormatter.formatConversationListTime(conversationInfo.lastMessage?.timestamp),
            color = colors.textColorSecondary,
            maxLines = 1,
            style = conversationListTextStyle(12.sp)
        )
        if (conversationInfo.needShowNotReceiveIcon) {
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            Icon(
                painter = painterResource(R.drawable.conversation_list_not_receive_icon),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 13.dp)
                    .size(16.dp)
                    .then(if (isRtl) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
                tint = colors.textColorDisable
            )
        }
    }
}

private const val CALL_ICON_INLINE_ID = "conversation_list_call_icon"

@Composable
private fun callIconInlineContent(mediaType: CallStreamMediaType?): Map<String, InlineTextContent> {
    if (mediaType == null) return emptyMap()
    val tint = LocalTheme.current.colors.textColorSecondary
    val iconRes = when (mediaType) {
        CallStreamMediaType.VOICE -> R.drawable.conversation_list_call_audio_icon
        else -> R.drawable.conversation_list_call_video_icon
    }
    return mapOf(
        CALL_ICON_INLINE_ID to InlineTextContent(
            placeholder = Placeholder(
                width = 16.sp,
                height = 12.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = tint
                )
                Spacer(Modifier.width(4.dp))
            }
        }
    )
}

@Composable
private fun buildSubTitleAnnotatedString(
    conversationInfo: ConversationInfo,
    emojiSubtitle: AnnotatedString,
    showCallIcon: Boolean = false,
): AnnotatedString {
    val colors = LocalTheme.current.colors
    val atTagText = buildAtTagText(conversationInfo)
    val draftPrefix = stringResource(R.string.conversation_list_draft_prefix)

    return buildAnnotatedString {
        if (!conversationInfo.draft.isNullOrEmpty()) {
            if (atTagText.isNotEmpty()) {
                withStyle(style = SpanStyle(color = colors.textColorError)) {
                    append(atTagText)
                }
            }
            withStyle(style = SpanStyle(color = colors.textColorError)) {
                append(draftPrefix)
            }
            withStyle(style = SpanStyle(color = colors.textColorSecondary)) {
                append(" ")
                append(emojiSubtitle)
            }
            return@buildAnnotatedString
        }

        if (atTagText.isNotEmpty()) {
            withStyle(style = SpanStyle(color = colors.textColorError)) {
                append(atTagText)
            }
            withStyle(style = SpanStyle(color = colors.textColorSecondary)) {
                append(" ")
                if (showCallIcon) {
                    appendInlineContent(CALL_ICON_INLINE_ID, " ")
                }
                append(emojiSubtitle)
            }
            return@buildAnnotatedString
        }

        if (conversationInfo.receiveOption != ReceiveMessageOption.RECEIVE
            && conversationInfo.unreadCount > 0
        ) {
            withStyle(style = SpanStyle(color = colors.textColorSecondary)) {
                append("[")
                append(conversationInfo.unreadCount.toString())
                append(stringResource(R.string.conversation_list_message_count_unit))
                append("] ")
                if (showCallIcon) {
                    appendInlineContent(CALL_ICON_INLINE_ID, " ")
                }
                append(emojiSubtitle)
            }
            return@buildAnnotatedString
        }

        withStyle(style = SpanStyle(color = colors.textColorSecondary)) {
            if (showCallIcon) {
                appendInlineContent(CALL_ICON_INLINE_ID, " ")
            }
            append(emojiSubtitle)
        }
    }
}

@Composable
private fun SendingProgressIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    val tintArgb = color.toArgb()
    AndroidView(
        modifier = modifier.size(12.dp),
        factory = { context ->
            ProgressBar(context).apply {
                isIndeterminate = true
                indeterminateTintList = ColorStateList.valueOf(tintArgb)
            }
        },
        update = { progressBar ->
            progressBar.indeterminateTintList = ColorStateList.valueOf(tintArgb)
        }
    )
}

private fun conversationListTextStyle(
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    includeFontPadding: Boolean = true,
): TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = fontWeight,
    fontSize = fontSize,
    letterSpacing = 0.sp,
    lineHeight = TextUnit.Unspecified,
    platformStyle = PlatformTextStyle(includeFontPadding = includeFontPadding),
)

@Composable
private fun buildAtTagText(conversationInfo: ConversationInfo): String {
    if (conversationInfo.unreadCount <= 0) return ""
    if (!conversationInfo.conversationID.startsWith("group_")) return ""

    val groupAtInfoList = conversationInfo.groupAtInfoList ?: return ""
    if (groupAtInfoList.isEmpty()) return ""

    val atAllPrefix = stringResource(R.string.conversation_list_at_all_prefix)
    val atMePrefix = stringResource(R.string.conversation_list_at_me_prefix)

    var hasAtAll = false
    var hasAtMe = false

    for (atInfo in groupAtInfoList) {
        when (atInfo.atType) {
            GroupAtType.AT_ME -> hasAtMe = true
            GroupAtType.AT_ALL -> hasAtAll = true
            GroupAtType.AT_ALL_AT_ME -> {
                hasAtAll = true
                hasAtMe = true
            }
        }
    }

    return buildString {
        if (hasAtAll) append(atAllPrefix)
        if (hasAtMe) append(atMePrefix)
    }
}
