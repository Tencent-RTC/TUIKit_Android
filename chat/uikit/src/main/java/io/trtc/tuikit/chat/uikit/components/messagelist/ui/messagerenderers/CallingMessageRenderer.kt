package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.AtomicCallEventPublisher
import io.trtc.tuikit.chat.uikit.components.common.RtlUtils
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.CallMessageModel
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.CallMessageParser
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.CallStreamMediaType
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.getCallMessageDisplayString
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

class CallingMessageRenderer : MessageRenderer {

    companion object {
        private const val TAG_TEXT = "calling_text"
        private const val TAG_ICON = "calling_icon"
        private const val ICON_TEXT_SPACING_DP = 8
    }

    override fun createView(context: Context, parent: ViewGroup): View {
        val density = context.resources.displayMetrics.density
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
            val horizontalPadding = (12 * density).toInt()
            val verticalPadding = (8 * density).toInt()
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val textView = TextView(context).apply {
            tag = TAG_TEXT
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineSpacing(0f, 1.3f)
            maxLines = 2
        }
        container.addView(
            textView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val icon = ImageView(context).apply {
            tag = TAG_ICON
            visibility = View.GONE
            adjustViewBounds = true
        }
        container.addView(
            icon,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (ICON_TEXT_SPACING_DP * density).toInt()
            }
        )

        return container
    }

    override fun bindView(
        view: View,
        message: MessageInfo,
        viewModel: MessageListViewModel,
        config: MessageListConfigProtocol,
        colors: ColorTokens
    ) {
        val container = view as LinearLayout
        val textView = container.findViewWithTag<TextView>(TAG_TEXT)
        val icon = container.findViewWithTag<ImageView>(TAG_ICON)

        val callModel = CallMessageParser.parse(message)
        if (callModel == null) {
            textView.text = ""
            icon.visibility = View.GONE
            container.setOnClickListener(null)
            return
        }

        val displayText = getCallMessageDisplayString(
            context = textView.context,
            message = message,
            callModel = callModel
        )
        textView.text = displayText

        val contentColor = if (message.isSentBySelf) {
            colors.textColorAntiPrimary
        } else {
            colors.textColorPrimary
        }
        textView.setTextColor(contentColor)

        val iconRes = when (callModel.streamMediaType) {
            CallStreamMediaType.VOICE -> R.drawable.message_list_call_audio_icon
            CallStreamMediaType.VIDEO -> R.drawable.message_list_call_video_icon
            else -> 0
        }

        val isRtl = RtlUtils.isLocaleRtl(view.context)
        if (iconRes != 0) {
            icon.visibility = View.VISIBLE
            icon.setImageResource(iconRes)
            icon.drawable?.setTint(contentColor)
            icon.scaleX = if (
                CallMessageDisplayPolicy.shouldMirrorCallIcon(
                    isSelf = message.isSentBySelf,
                    streamMediaType = callModel.streamMediaType,
                    isRtl = isRtl,
                )
            ) {
                -1f
            } else {
                1f
            }
        } else {
            icon.visibility = View.GONE
            icon.scaleX = 1f
        }

        applyContentOrder(container, textView, icon, message.isSentBySelf, isRtl)

        val canReCall = callModel.streamMediaType == CallStreamMediaType.VOICE ||
            callModel.streamMediaType == CallStreamMediaType.VIDEO
        if (canReCall) {
            container.isClickable = true
            container.setOnClickListener {
                reInitiateCall(message, callModel)
            }
        } else {
            container.setOnClickListener(null)
        }
    }

    private fun applyContentOrder(
        container: LinearLayout,
        textView: TextView,
        icon: ImageView,
        isSelf: Boolean,
        isRtl: Boolean,
    ) {
        container.removeAllViews()
        CallMessageDisplayPolicy.contentOrder(isSelf, isRtl).forEach { part ->
            when (part) {
                CallMessageContentPart.ICON ->
                    container.addView(icon, iconLayoutParams(container, isSelf, isRtl))
                CallMessageContentPart.TEXT -> container.addView(textView, textLayoutParams())
                else -> Unit
            }
        }
    }

    private fun iconLayoutParams(
        container: LinearLayout,
        isSelf: Boolean,
        isRtl: Boolean,
    ): LinearLayout.LayoutParams {
        val spacing = (ICON_TEXT_SPACING_DP * container.resources.displayMetrics.density).toInt()
        val iconAfterText = isSelf xor isRtl
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            if (iconAfterText) {
                marginStart = spacing
            } else {
                marginEnd = spacing
            }
        }
    }

    private fun textLayoutParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
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

internal enum class CallMessageContentPart {
    ICON,
    TEXT
}

internal object CallMessageDisplayPolicy {
    fun contentOrder(isSelf: Boolean, isRtl: Boolean = false): List<CallMessageContentPart> {
        return if (isSelf xor isRtl) {
            listOf(CallMessageContentPart.TEXT, CallMessageContentPart.ICON)
        } else {
            listOf(CallMessageContentPart.ICON, CallMessageContentPart.TEXT)
        }
    }

    fun shouldShowOutsideUnreadDot(
        isSelf: Boolean,
        isShowUnreadPoint: Boolean
    ): Boolean {
        return isShowUnreadPoint && !isSelf
    }

    fun shouldMirrorCallIcon(
        isSelf: Boolean,
        streamMediaType: CallStreamMediaType,
        isRtl: Boolean = false,
    ): Boolean {
        val isCallMedia = streamMediaType == CallStreamMediaType.VIDEO ||
            streamMediaType == CallStreamMediaType.VOICE
        return isCallMedia && ((!isSelf) xor isRtl)
    }
}
