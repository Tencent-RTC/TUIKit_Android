package io.trtc.tuikit.chat.customMessages

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.Keep
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderContext
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.messageListTouchTarget
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageListMessageSummaryRegistry
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageSummaryProvider
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.chat.R

@Keep
data class CustomLinkMessage(
    @SerializedName("businessID")
    val businessID: String? = null,
    @SerializedName("text")
    val text: String? = null,
    @SerializedName("link")
    val link: String? = null,
) {
    companion object {
        const val BUSINESS_ID = "text_link"

        fun from(customData: String?): CustomLinkMessage? {
            if (customData.isNullOrBlank()) {
                return null
            }
            return runCatching {
                Gson().fromJson(customData, CustomLinkMessage::class.java)
            }.getOrNull()
        }

        fun registerMessageSummary() {
            MessageListMessageSummaryRegistry.setCustomMessageSummary(
                businessID = BUSINESS_ID,
                summaryProvider = MessageSummaryProvider { summaryContext ->
                    val payload = summaryContext.message.messagePayload as? CustomMessagePayload
                    from(payload?.customData)?.text
                }
            )
        }
    }
}

class CustomLinkMessageRenderer : MessageRenderer {

    override val renderConfig: MessageRenderConfig
        get() = MessageRenderConfig(showMessageMeta = true, useDefaultBubble = true)

    @Composable
    override fun Render(message: MessageInfo, context: MessageRenderContext) {
        val androidContext = LocalContext.current
        val colors = context.colors
        val payload = message.messagePayload as? CustomMessagePayload
        val linkMessage = CustomLinkMessage.from(payload?.customData)
        val text = linkMessage?.text.orEmpty()
        val link = linkMessage?.link.orEmpty().trim()
        val hasLink = link.isNotEmpty()
        val canOpenLink = hasLink && !context.isMultiSelectMode
        val contentColor = if (message.isSentBySelf) colors.textColorAntiPrimary else colors.textColorPrimary
        var contentBounds by remember { mutableStateOf(Rect.Zero) }

        Column(
            modifier = Modifier
                .messageListTouchTarget()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    contentBounds = Rect(
                        left = pos.x,
                        top = pos.y,
                        right = pos.x + coords.size.width,
                        bottom = pos.y + coords.size.height
                    )
                }
                .pointerInput(link, canOpenLink) {
                    detectTapGestures(
                        onTap = {
                            if (canOpenLink) {
                                openLink(androidContext, link)
                            } else if (context.isMultiSelectMode) {
                                context.actions.toggleSelection(message)
                            }
                        },
                        onLongPress = {
                            context.actions.showLongPressMenu(message, contentBounds)
                        }
                    )
                }
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                lineHeight = 20.8.sp,
                color = contentColor
            )
            if (hasLink) {
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = stringResource(R.string.compose_demo_custom_message_view_details),
                    fontSize = 14.sp,
                    lineHeight = 16.8.sp,
                    color = colors.textColorLink
                )
            }
        }
    }

    private fun openLink(context: Context, link: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
