package io.trtc.tuikit.chat.uikit.components.messagelist.adapter
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageListTouchTargetTags
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.auxiliary.AuxiliaryTextBubbleView
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.reactions.MessageReactionBarView
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.readreceipts.MessageReadReceiptIndicatorView
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar

internal data class MessageItemViewHierarchy(
    val timeView: TextView,
    val checkBox: ImageView,
    val contentRow: LinearLayout,
    val inlineTimeView: TextView,
    val leftAvatarView: Avatar,
    val rightAvatarView: Avatar,
    val middleColumn: LinearLayout,
    val nicknameView: TextView,
    val quoteBubbleView: MessageQuoteBubbleView,
    val bubbleRow: MaxWidthLinearLayout,
    val bubbleContainer: MaxWidthFrameLayout,
    val bubbleContentContainer: LinearLayout,
    val sendingIndicator: ProgressBar,
    val sendFailIcon: TextView,
    val readReceiptIndicatorView: MessageReadReceiptIndicatorView,
    val callUnreadDotView: View,
    val statusContainer: FrameLayout,
    val auxiliaryTextBubbleView: AuxiliaryTextBubbleView,
    val reactionBarView: MessageReactionBarView,
    val violationView: TextView,
    val maxRowWidth: Int,
    val maxBubbleWidth: Int
)

internal fun MessageItemView.buildMessageItemViewHierarchy(
    config: MessageListConfigProtocol,
    density: Float
): MessageItemViewHierarchy {
    orientation = LinearLayout.VERTICAL
    val horizontalPad = (config.horizontalPadding * density).toInt()
    setPadding(horizontalPad, 0, horizontalPad, 0)

    val timeView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        visibility = View.GONE
    }
    addView(timeView, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = (10 * density).toInt()
        bottomMargin = (10 * density).toInt()
    })

    val mainRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
    }
    addView(mainRow, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ))

    val checkBox = ImageView(context).apply {
        visibility = View.GONE
        isClickable = true
        isFocusable = true
        MessageListTouchTargetTags.mark(this)
    }
    val checkBoxSize = (MULTI_SELECT_CHECKBOX_SIZE_DP * density).toInt()
    mainRow.addView(checkBox, LinearLayout.LayoutParams(checkBoxSize, checkBoxSize).apply {
        gravity = Gravity.CENTER_VERTICAL
        marginEnd = (MULTI_SELECT_CHECKBOX_MARGIN_END_DP * density).toInt()
    })

    val contentRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        gravity = Gravity.TOP
    }
    mainRow.addView(contentRow, LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
    ))

    val inlineTimeView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        visibility = View.GONE
    }
    mainRow.addView(inlineTimeView, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = Gravity.TOP
        marginStart = (INLINE_MESSAGE_TIME_MARGIN_START_DP * density).toInt()
    })

    val leftAvatarView = Avatar(context).apply {
        setSize(Avatar.AvatarSize.S)
        visibility = View.GONE
        MessageListTouchTargetTags.mark(this)
    }
    contentRow.addView(leftAvatarView, LinearLayout.LayoutParams(
        (MESSAGE_AVATAR_SIZE_DP * density).toInt(),
        (MESSAGE_AVATAR_SIZE_DP * density).toInt()
    ).apply {
        marginEnd = (config.avatarSpacing * density).toInt()
    })

    val middleColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        gravity = Gravity.LEFT
    }
    contentRow.addView(
        middleColumn,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    )

    val nicknameView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.START
        visibility = View.GONE
        MessageListTouchTargetTags.mark(this)
    }
    middleColumn.addView(nicknameView, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        bottomMargin = (4 * density).toInt()
    })

    val bubbleRow = MaxWidthLinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        gravity = Gravity.LEFT or Gravity.BOTTOM
    }
    middleColumn.addView(bubbleRow, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ))

    val quoteBubbleView = MessageQuoteBubbleView(context).apply {
        visibility = View.GONE
    }
    middleColumn.addView(
        quoteBubbleView,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (4 * density).toInt()
        }
    )

    val statusContainer = FrameLayout(context).apply {
        MessageListTouchTargetTags.mark(this)
    }
    val sendingIndicator = ProgressBar(context).apply {
        visibility = View.GONE
        isIndeterminate = true
        indeterminateDrawable = ContextCompat.getDrawable(
            context,
            R.drawable.message_list_sending_indicator
        )
    }
    val sendFailIcon = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        text = "!"
        visibility = View.GONE
    }
    val readReceiptIndicatorView = MessageReadReceiptIndicatorView(context).apply {
        visibility = View.GONE
    }
    val callUnreadDotView = View(context).apply {
        visibility = View.GONE
    }
    statusContainer.addView(sendingIndicator, FrameLayout.LayoutParams(
        (12 * density).toInt(),
        (12 * density).toInt(),
        Gravity.CENTER
    ))
    statusContainer.addView(sendFailIcon, FrameLayout.LayoutParams(
        (14 * density).toInt(),
        (14 * density).toInt(),
        Gravity.CENTER
    ))
    statusContainer.addView(readReceiptIndicatorView, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.CENTER
    ))
    val bubbleContainer = MaxWidthFrameLayout(context).apply {
        MessageListTouchTargetTags.mark(this)
    }
    val bubbleContentContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        gravity = Gravity.START
    }
    bubbleContainer.addView(
        bubbleContentContainer,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
    )
    val screenWidth = context.resources.displayMetrics.widthPixels
    val maxRowWidth = MessageItemDisplayPolicy.resolveMaxRowWidth(
        screenWidth = screenWidth,
        horizontalPaddingDp = config.horizontalPadding,
        avatarSpacingDp = config.avatarSpacing,
        density = density
    )
    val maxBubbleWidth = MessageItemDisplayPolicy.resolvePreferredBubbleMaxWidth(
        screenWidth = screenWidth,
        maxRowWidth = maxRowWidth
    )
    bubbleRow.maxWidth = maxRowWidth
    bubbleContainer.maxWidth = maxBubbleWidth
    bubbleRow.addView(statusContainer, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = Gravity.BOTTOM
    })
    bubbleRow.addView(bubbleContainer, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ))

    val auxiliaryTextBubbleView = AuxiliaryTextBubbleView(context).apply {
        visibility = View.GONE
        MessageListTouchTargetTags.mark(this)
    }
    middleColumn.addView(auxiliaryTextBubbleView, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ))

    val reactionBarView = MessageReactionBarView(context).apply {
        visibility = View.GONE
        MessageListTouchTargetTags.mark(this)
    }

    val violationView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        visibility = View.GONE
        MessageListTouchTargetTags.mark(this)
    }
    middleColumn.addView(violationView, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = (2 * density).toInt()
    })

    val rightAvatarView = Avatar(context).apply {
        setSize(Avatar.AvatarSize.S)
        visibility = View.GONE
        MessageListTouchTargetTags.mark(this)
    }
    contentRow.addView(rightAvatarView, LinearLayout.LayoutParams(
        (MESSAGE_AVATAR_SIZE_DP * density).toInt(),
        (MESSAGE_AVATAR_SIZE_DP * density).toInt()
    ).apply {
        marginStart = (config.avatarSpacing * density).toInt()
    })

    return MessageItemViewHierarchy(
        timeView = timeView,
        checkBox = checkBox,
        contentRow = contentRow,
        inlineTimeView = inlineTimeView,
        leftAvatarView = leftAvatarView,
        rightAvatarView = rightAvatarView,
        middleColumn = middleColumn,
        nicknameView = nicknameView,
        quoteBubbleView = quoteBubbleView,
        bubbleRow = bubbleRow,
        bubbleContainer = bubbleContainer,
        bubbleContentContainer = bubbleContentContainer,
        sendingIndicator = sendingIndicator,
        sendFailIcon = sendFailIcon,
        readReceiptIndicatorView = readReceiptIndicatorView,
        callUnreadDotView = callUnreadDotView,
        statusContainer = statusContainer,
        auxiliaryTextBubbleView = auxiliaryTextBubbleView,
        reactionBarView = reactionBarView,
        violationView = violationView,
        maxRowWidth = maxRowWidth,
        maxBubbleWidth = maxBubbleWidth
    )
}
