package io.trtc.tuikit.chat.uikit.components.conversationlist.adapter
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.ChatDateTimeUtils
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.conversationlist.utils.isUnread
import io.trtc.tuikit.chat.uikit.components.conversationlist.utils.needShowBadge
import io.trtc.tuikit.chat.uikit.components.conversationlist.utils.needShowNotReceiveIcon
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiSpanHelper
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.MessageListMessageSummaryFormatter
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.GroupAtType
import io.trtc.tuikit.atomicxcore.api.conversation.ReceiveMessageOption
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus

internal fun resolveConversationAvatarBadge(conversation: ConversationInfo): Avatar.AvatarBadge {
    val isReceiveMessage = conversation.receiveOption == ReceiveMessageOption.RECEIVE
    return when {
        conversation.isUnread && conversation.needShowBadge -> {
            val count = conversation.unreadCount
            if (count > 0) {
                val badgeText = if (count > 99) "99+" else count.toString()
                Avatar.AvatarBadge.Text(badgeText)
            } else {
                Avatar.AvatarBadge.Dot
            }
        }
        !isReceiveMessage && conversation.isUnread -> Avatar.AvatarBadge.Dot
        else -> Avatar.AvatarBadge.None
    }
}

class ConversationListAdapter(
    private val context: Context,
    private val onItemClick: (ConversationInfo) -> Unit = {},
    private val onItemLongClick: (ConversationInfo, View) -> Unit = { _, _ -> }
) : ListAdapter<ConversationInfo, ConversationListAdapter.ConversationViewHolder>(DIFF) {

    private val themeStore = ThemeStore.shared(context)
    private val colors: ColorTokens get() = themeStore.themeState.value.currentTheme.tokens.color
    private val summaryFormatter = MessageListMessageSummaryFormatter()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val itemView = ConversationItemLayout(parent.context)
        itemView.layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            dp2px(parent.context, 64f)
        )
        return ConversationViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = getItem(position)
        holder.bind(conversation)
    }

    override fun onBindViewHolder(
        holder: ConversationViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_THEME)) {
            holder.bindTheme(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    fun notifyThemeChanged() {
        if (itemCount > 0) {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_THEME)
        }
    }

    inner class ConversationViewHolder(
        private val itemLayout: ConversationItemLayout
    ) : RecyclerView.ViewHolder(itemLayout) {

        fun bind(conversation: ConversationInfo) {
            val colors = this@ConversationListAdapter.colors

            itemLayout.bindConversation(conversation, colors, context, summaryFormatter)

            itemLayout.setOnClickListener {
                onItemClick(conversation)
            }

            itemLayout.setOnLongClickListener {
                itemLayout.setHighlighted(true, colors)
                onItemLongClick(conversation, itemLayout)
                true
            }
        }

        fun bindTheme(conversation: ConversationInfo) {
            itemLayout.bindTheme(conversation, colors, context, summaryFormatter)
        }
    }

    companion object {
        private const val PAYLOAD_THEME = "conversation_list_theme"

        val DIFF = object : DiffUtil.ItemCallback<ConversationInfo>() {
            override fun areItemsTheSame(
                oldItem: ConversationInfo,
                newItem: ConversationInfo
            ): Boolean = oldItem.conversationID == newItem.conversationID

            override fun areContentsTheSame(
                oldItem: ConversationInfo,
                newItem: ConversationInfo
            ): Boolean = oldItem == newItem
        }
    }
}

class ConversationItemLayout(context: Context) : FrameLayout(context) {

    private val avatar: Avatar
    private val titleView: TextView
    private val subtitleView: TextView
    private val timeView: TextView
    private val muteIcon: ImageView
    private val sendFailIcon: TextView
    private val sendingIndicator: ProgressBar

    private val highlightOverlay = ColorDrawable(Color.TRANSPARENT)
    private val sendFailIconBackground = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
    }

    init {
        isClickable = true
        isFocusable = true
        clipChildren = false
        clipToPadding = false
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE

        val contentRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            clipChildren = false
            clipToPadding = false
            setPadding(dp2px(context, 16f), 0, dp2px(context, 16f), 0)
            layoutParams = FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }
        addView(contentRow)

        avatar = Avatar(context).apply {
            setSize(Avatar.AvatarSize.M)
        }
        contentRow.addView(avatar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val spacer1 = View(context)
        contentRow.addView(spacer1, LinearLayout.LayoutParams(dp2px(context, 8f), 0))

        val middleColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        contentRow.addView(middleColumn)

        titleView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                typeface = Typeface.create(Typeface.DEFAULT, 600, false)
            } else {
                typeface = Typeface.DEFAULT_BOLD
            }
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textDirection = View.TEXT_DIRECTION_LOCALE
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        middleColumn.addView(titleView, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ))

        val spacerTitle = View(context)
        middleColumn.addView(spacerTitle, LinearLayout.LayoutParams(0, dp2px(context, 4f)))

        val subtitleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        middleColumn.addView(subtitleRow, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ))

        sendFailIcon = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            text = "!"
            visibility = GONE
            background = sendFailIconBackground
        }
        subtitleRow.addView(sendFailIcon, LinearLayout.LayoutParams(
            dp2px(context, 14f), dp2px(context, 14f)
        ).apply { marginEnd = dp2px(context, 4f) })

        sendingIndicator = ProgressBar(context).apply {
            visibility = GONE
        }
        subtitleRow.addView(sendingIndicator, LinearLayout.LayoutParams(
            dp2px(context, 12f), dp2px(context, 12f)
        ).apply { marginEnd = dp2px(context, 4f) })

        subtitleView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textDirection = View.TEXT_DIRECTION_LOCALE
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        subtitleRow.addView(subtitleView, LinearLayout.LayoutParams(
            0, LayoutParams.WRAP_CONTENT, 1f
        ))

        val spacerMiddle = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp2px(context, 16f), 0)
        }
        contentRow.addView(spacerMiddle)

        val rightColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        contentRow.addView(rightColumn, LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
        ))

        timeView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            textDirection = View.TEXT_DIRECTION_LOCALE
        }
        rightColumn.addView(timeView, LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
        ))

        val spacerTimeToMute = View(context)
        rightColumn.addView(spacerTimeToMute, LinearLayout.LayoutParams(0, dp2px(context, 4f)))

        muteIcon = ImageView(context).apply {
            visibility = GONE
            setImageDrawable(context.getDrawable(R.drawable.conversation_list_not_receive_icon)?.apply {
                isAutoMirrored = true
            })
        }
        rightColumn.addView(muteIcon, LinearLayout.LayoutParams(
            dp2px(context, 16f), dp2px(context, 16f)
        ))

        foreground = highlightOverlay
    }

    fun bindConversation(
        conversation: ConversationInfo,
        colors: ColorTokens,
        context: Context,
        summaryFormatter: MessageListMessageSummaryFormatter = MessageListMessageSummaryFormatter()
    ) {
        avatar.setContent(
            Avatar.AvatarContent.Image(
                url = conversation.avatarURL,
                fallbackName = conversation.title ?: conversation.conversationID
            )
        )

        bindAvatarBadge(conversation)

        titleView.text = conversation.title ?: conversation.conversationID
        timeView.text = ChatDateTimeUtils.formatConversationListTime(conversation.lastMessage?.timestamp)

        bindTheme(conversation, colors, context, summaryFormatter)
    }

    fun bindTheme(
        conversation: ConversationInfo,
        colors: ColorTokens,
        context: Context,
        summaryFormatter: MessageListMessageSummaryFormatter = MessageListMessageSummaryFormatter()
    ) {
        setBackgroundColor(
            if (conversation.isPinned) colors.bgColorInput else colors.bgColorOperate
        )
        titleView.setTextColor(colors.textColorPrimary)
        subtitleView.setTextColor(colors.textColorSecondary)
        val rawSubtitle = buildSubtitle(conversation, colors, context, summaryFormatter)
        val fallbackSubtitle = buildSubtitle(
            conversation, colors, context, summaryFormatter, EmojiSpanHelper::replaceEmojiKeysWithNames
        )
        val bindToken = "${conversation.conversationID}|${conversation.lastMessage?.msgID.orEmpty()}|$rawSubtitle|${subtitleView.textSize}"
        subtitleView.setTag(R.id.emoji_span_bind_token_tag, bindToken)
        subtitleView.text = fallbackSubtitle
        EmojiSpanHelper.applyEmojiSpans(context, rawSubtitle, subtitleView.textSize, subtitleView) { spanned ->
            if (subtitleView.getTag(R.id.emoji_span_bind_token_tag) == bindToken) {
                subtitleView.text = spanned
            }
        }
        timeView.setTextColor(colors.textColorSecondary)
        bindMuteIcon(conversation, colors)
        bindSendStatus(conversation, colors)
    }

    fun setHighlighted(highlighted: Boolean, colors: ColorTokens) {
        if (highlighted) {
            highlightOverlay.color = Color.argb(20, 0, 0, 0)
        } else {
            highlightOverlay.color = Color.TRANSPARENT
        }
    }

    private fun bindAvatarBadge(conversation: ConversationInfo) {
        avatar.setBadge(resolveConversationAvatarBadge(conversation))
    }

    private fun bindMuteIcon(conversation: ConversationInfo, colors: ColorTokens) {
        if (conversation.needShowNotReceiveIcon) {
            muteIcon.visibility = VISIBLE
            muteIcon.setColorFilter(colors.textColorSecondary)
        } else {
            muteIcon.visibility = GONE
        }
    }

    private fun bindSendStatus(conversation: ConversationInfo, colors: ColorTokens) {
        val lastMsg = conversation.lastMessage
        when (lastMsg?.status) {
            MessageStatus.SEND_FAIL, MessageStatus.VIOLATION -> {
                sendFailIcon.visibility = VISIBLE
                sendFailIcon.setTextColor(colors.textColorButton)
                sendFailIconBackground.setColor(colors.textColorError)
                sendingIndicator.visibility = GONE
            }
            MessageStatus.SENDING -> {
                sendFailIcon.visibility = GONE
                sendingIndicator.indeterminateTintList = ColorStateList.valueOf(colors.textColorAntiSecondary)
                sendingIndicator.visibility = VISIBLE
            }
            else -> {
                sendFailIcon.visibility = GONE
                sendingIndicator.visibility = GONE
            }
        }
    }

    private fun buildSubtitle(
        conversation: ConversationInfo,
        colors: ColorTokens,
        context: Context,
        summaryFormatter: MessageListMessageSummaryFormatter,
        transform: (String) -> String = { it }
    ): CharSequence {
        val atTagText = buildAtTagText(conversation, context)
        val draft = conversation.draft
        if (!draft.isNullOrEmpty()) {
            val draftPrefix = context.getString(R.string.conversation_list_draft_prefix)
            return SpannableStringBuilder().apply {
                appendErrorText(colors, atTagText)
                appendErrorText(colors, draftPrefix)
                append(" ")
                append(transform(draft))
            }
        }

        val defaultSubtitle = transform(getMessageAbstract(conversation, context, summaryFormatter))
        if (atTagText.isNotEmpty()) {
            return SpannableStringBuilder().apply {
                appendErrorText(colors, atTagText)
                append(" ")
                append(defaultSubtitle)
            }
        }

        if (conversation.receiveOption != ReceiveMessageOption.RECEIVE && conversation.unreadCount > 0) {
            return buildString {
                append("[")
                append(conversation.unreadCount)
                append(context.getString(R.string.conversation_list_message_count_unit))
                append("] ")
                append(defaultSubtitle)
            }
        }

        return defaultSubtitle
    }

    private fun SpannableStringBuilder.appendErrorText(colors: ColorTokens, text: String) {
        if (text.isEmpty()) return
        val start = length
        append(text)
        setSpan(
            ForegroundColorSpan(colors.textColorError),
            start,
            length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun getMessageAbstract(
        conversation: ConversationInfo,
        context: Context,
        summaryFormatter: MessageListMessageSummaryFormatter
    ): String {
        val messageInfo = conversation.lastMessage ?: return ""
        return summaryFormatter.format(context, messageInfo, conversation.conversationID)
    }

    private fun buildAtTagText(conversation: ConversationInfo, context: Context): String {
        if (conversation.unreadCount <= 0) return ""
        if (!ConversationIDUtil.isGroup(conversation.conversationID)) return ""

        val groupAtInfoList = conversation.groupAtInfoList ?: return ""
        if (groupAtInfoList.isEmpty()) return ""

        val atAllPrefix = context.getString(R.string.conversation_list_at_all_prefix)
        val atMePrefix = context.getString(R.string.conversation_list_at_me_prefix)

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
                else -> Unit
            }
        }

        return buildString {
            if (hasAtAll) append(atAllPrefix)
            if (hasAtMe) append(atMePrefix)
        }
    }
}

fun dp2px(context: Context, dpValue: Float): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dpValue, context.resources.displayMetrics
    ).toInt()
}