package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.bumptech.glide.Glide
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.FaceMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

class FaceMessageRenderer : MessageRenderer {

    override val renderConfig: MessageRenderConfig
        get() = MessageRenderConfig(showMessageMeta = true, useDefaultBubble = false)

    override fun createView(context: Context, parent: ViewGroup): View {
        val density = context.resources.displayMetrics.density
        return ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                (80 * density).toInt(),
                (80 * density).toInt()
            )
        }
    }

    override fun bindView(
        view: View,
        message: MessageInfo,
        viewModel: MessageListViewModel,
        config: MessageListConfigProtocol,
        colors: ColorTokens
    ) {
        val imageView = view as ImageView
        val payload = message.messagePayload as? FaceMessagePayload
        val emoji = FaceMessageResourceResolver.resolve(
            faceName = payload?.faceData,
            faceIndex = payload?.faceIndex ?: -1,
            emojis = EmojiManager.emojiGroupList.flatMap { it.emojis }
        )

        if (emoji == null) {
            Glide.with(imageView).clear(imageView)
            imageView.setImageResource(R.drawable.message_list_image_error_image)
            return
        }

        val cachedDrawable = EmojiManager.getCachedEmojiDrawable(emoji.key)
        if (cachedDrawable != null) {
            Glide.with(imageView).clear(imageView)
            imageView.setImageDrawable(cachedDrawable)
        } else {
            Glide.with(imageView)
                .load(emoji.emojiUrl)
                .error(R.drawable.message_list_image_error_image)
                .into(imageView)
        }
    }
}
