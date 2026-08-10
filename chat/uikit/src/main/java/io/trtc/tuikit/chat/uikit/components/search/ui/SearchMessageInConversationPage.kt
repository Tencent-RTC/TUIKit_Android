package io.trtc.tuikit.chat.uikit.components.search.ui
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiSpanHelper
import io.trtc.tuikit.chat.uikit.components.search.utils.HighlightUtils
import io.trtc.tuikit.chat.uikit.components.search.utils.displayName
import io.trtc.tuikit.chat.uikit.components.search.utils.getMessageAbstract
import io.trtc.tuikit.chat.uikit.components.search.utils.messageSender
import io.trtc.tuikit.chat.uikit.components.search.utils.messageSenderAvatarUrl
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageInConversationViewModel
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.search.MessageSearchResultItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchMessageInConversationPage(
    context: Context,
    private val viewModel: SearchMessageInConversationViewModel
) : LinearLayout(context) {

    private val searchBar = SubSearchBarView(context)
    private val conversationCard: LinearLayout
    private val conversationAvatar: Avatar
    private val conversationName: TextView
    private val conversationArrow: ImageView
    private val divider: View
    private val recyclerView = RecyclerView(context)
    private val loadingView: ProgressBar
    private val emptyText: TextView
    private var viewScope: CoroutineScope? = null
    private var searchQuery = ""
    private var conversationID = ""
    private val themeStore = ThemeStore.shared(context)

    var onMessageClick: ((MessageInfo) -> Unit)? = null
    var onConversationSelect: ((MessageSearchResultItem) -> Unit)? = null
    var onQueryChange: ((String) -> Unit)? = null
    var onBack: (() -> Unit)? = null

    private var currentConversation: MessageSearchResultItem? = null

    init {
        orientation = VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        addView(searchBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        conversationCard = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val dp16 = dpToPx(16)
            val dp12 = dpToPx(12)
            setPadding(dp16, dp12, dp16, dp12)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(64))
        }

        conversationAvatar = Avatar(context).apply {
            layoutParams = LayoutParams(dpToPx(40), dpToPx(40))
        }
        conversationCard.addView(conversationAvatar)

        conversationName = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dpToPx(12)
            layoutParams = lp
            isSingleLine = true
        }
        conversationCard.addView(conversationName)

        conversationArrow = ImageView(context).apply {
            val iconSize = dpToPx(16)
            layoutParams = LayoutParams(iconSize, iconSize)
            setImageResource(R.drawable.uikit_ic_arrow_right)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        conversationCard.addView(conversationArrow)

        conversationCard.setOnClickListener {
            currentConversation?.let { onConversationSelect?.invoke(it) }
        }
        addView(conversationCard)

        divider = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 1).apply {
                marginStart = dpToPx(16)
                marginEnd = dpToPx(16)
            }
        }
        addView(divider)

        loadingView = ProgressBar(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(16)
            }
            visibility = View.GONE
        }
        addView(loadingView)

        emptyText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            text = context.getString(R.string.search_cannot_found_chat_record)
            gravity = Gravity.CENTER
            val dp16 = dpToPx(16)
            setPadding(dp16, dp16, dp16, dp16)
            visibility = View.GONE
        }
        addView(emptyText)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.setOnTouchListener { _, _ ->
            searchBar.hideKeyboard()
            false
        }
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dx != 0 || dy != 0) {
                    searchBar.hideKeyboard()
                }
                val lm = rv.layoutManager as LinearLayoutManager
                val total = lm.itemCount
                val lastVisible = lm.findLastVisibleItemPosition()
                if (total > 0 && lastVisible >= total - 1) {
                    viewModel.searchMore()
                }
            }
        })
        addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        searchBar.onQueryChange = { query ->
            searchQuery = query
            onQueryChange?.invoke(query)
            if (query.isBlank()) {
                viewModel.updateSearchQuery(conversationID, query)
                updateResults(emptyList())
            } else {
                viewModel.updateSearchQuery(conversationID, query)
            }
        }
        searchBar.onBack = { onBack?.invoke() }
        searchBar.onCancel = { onBack?.invoke() }

        applyTheme()
    }

    fun start(conversation: MessageSearchResultItem, keyword: String) {
        currentConversation = conversation
        conversationID = conversation.conversationID
        searchQuery = keyword
        searchBar.setQuery(keyword)

        conversationAvatar.setContent(
            Avatar.AvatarContent.Image(conversation.conversationAvatarURL, conversation.displayName)
        )
        conversationName.text = conversation.displayName

        if (viewScope != null) {
            viewModel.updateSearchQuery(conversationID, keyword)
            searchBar.requestFocusAndShowKeyboard()
            return
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        viewScope = scope

        scope.launch {
            viewModel.messagesInConversation.collectLatest { messages ->
                if (!viewModel.isSearching.value) {
                    updateResults(messages)
                }
            }
        }
        scope.launch {
            viewModel.isSearching.collectLatest { searching ->
                loadingView.visibility = if (searching) View.VISIBLE else View.GONE
                if (searching) {
                    emptyText.visibility = View.GONE
                    recyclerView.visibility = View.GONE
                } else {
                    updateResults(viewModel.messagesInConversation.value)
                }
            }
        }
        scope.launch {
            themeStore.themeState.collectLatest {
                applyTheme()
                recyclerView.adapter?.notifyDataSetChanged()
            }
        }

        viewModel.updateSearchQuery(conversationID, keyword)
        searchBar.requestFocusAndShowKeyboard()
    }

    private fun stopCollectingUiState() {
        viewScope?.cancel()
        viewScope = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopCollectingUiState()
    }

    private fun updateResults(messages: List<MessageInfo>) {
        if (messages.isEmpty() && searchQuery.isNotBlank()) {
            emptyText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = MessageInConversationAdapter(context, messages, searchQuery) {
                onMessageClick?.invoke(it)
            }
        }
    }

    private fun applyTheme() {
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        setBackgroundColor(colors.bgColorOperate)
        conversationCard.setBackgroundColor(colors.bgColorOperate)
        conversationName.setTextColor(colors.textColorPrimary)
        conversationArrow.setColorFilter(colors.textColorSecondary)
        divider.setBackgroundColor(colors.strokeColorSecondary)
        emptyText.setTextColor(colors.textColorSecondary)
        searchBar.applyTheme()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}

private class MessageInConversationAdapter(
    private val context: Context,
    private val messages: List<MessageInfo>,
    private val keywords: String,
    private val onClick: (MessageInfo) -> Unit
) : RecyclerView.Adapter<MessageInConversationAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(MessageItemView(context))

    override fun getItemCount() = messages.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        holder.view.bind(messages[position], keywords, colors)
        holder.view.setOnClickListener { onClick(messages[position]) }
    }

    class VH(val view: MessageItemView) : RecyclerView.ViewHolder(view)
}

private class MessageItemView(context: Context) : LinearLayout(context) {

    private val avatar: Avatar
    private val senderName: TextView
    private val messageContent: TextView
    private val textContainer: LinearLayout

    init {
        orientation = HORIZONTAL
        val dp16 = dpToPx(16)
        val dp12 = dpToPx(12)
        setPadding(dp16, dp12, dp16, dp12)
        layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )

        avatar = Avatar(context).apply {
            layoutParams = LayoutParams(dpToPx(40), dpToPx(40))
        }
        addView(avatar)

        textContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dpToPx(12)
            layoutParams = lp
        }
        addView(textContainer)

        senderName = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isSingleLine = true
        }
        textContainer.addView(senderName)

        messageContent = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = dpToPx(4)
            layoutParams = lp
            maxLines = 2
        }
        textContainer.addView(messageContent)
    }

    fun bind(
        message: MessageInfo,
        keywords: String,
        colors: io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
    ) {
        avatar.setContent(
            Avatar.AvatarContent.Image(message.messageSenderAvatarUrl, message.messageSender)
        )
        senderName.text = message.messageSender
        senderName.setTextColor(colors.textColorPrimary)

        val rawAbstract = message.getMessageAbstract(context)
        val bindToken = "${message.msgID.orEmpty()}|$keywords|$rawAbstract|${messageContent.textSize}"
        messageContent.setTag(R.id.emoji_span_bind_token_tag, bindToken)
        messageContent.text = HighlightUtils.highlight(
            EmojiSpanHelper.replaceEmojiKeysWithNames(rawAbstract),
            keywords,
            colors.textColorLink,
            colors.textColorSecondary
        )
        EmojiSpanHelper.applyEmojiSpans(
            context,
            HighlightUtils.highlight(rawAbstract, keywords, colors.textColorLink, colors.textColorSecondary),
            messageContent.textSize,
            messageContent
        ) { spanned ->
            if (messageContent.getTag(R.id.emoji_span_bind_token_tag) == bindToken) {
                messageContent.text = spanned
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
