package io.trtc.tuikit.chat.uikit.components.search.ui
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiSpanHelper
import io.trtc.tuikit.chat.uikit.components.search.utils.HighlightSegment
import io.trtc.tuikit.chat.uikit.components.search.utils.HighlightUtils
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.search.utils.displayName
import io.trtc.tuikit.chat.uikit.components.search.utils.getMessageAbstract
import io.trtc.tuikit.chat.uikit.components.search.utils.userAvatarURL
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchAllViewModel
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchCategory
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.atomicxcore.api.search.FriendSearchInfo
import io.trtc.tuikit.atomicxcore.api.search.GroupSearchInfo
import io.trtc.tuikit.atomicxcore.api.search.MessageSearchResultItem
import io.trtc.tuikit.atomicxcore.api.search.SearchType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GlobalSearchPage(
    context: Context,
    private val viewModel: SearchAllViewModel
) : LinearLayout(context) {

    private val searchBar = SearchBarView(context)
    private val recyclerView = RecyclerView(context)
    private val loadingView: ProgressBar
    private var viewScope: CoroutineScope? = null
    private var searchQuery = ""
    private val themeStore = ThemeStore.shared(context)

    var onResultClick: ((Any) -> Unit)? = null
    var onQueryChange: ((String) -> Unit)? = null
    var onShowMore: ((SearchType) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        addView(searchBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        loadingView = ProgressBar(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(16)
            }
            visibility = View.GONE
        }
        addView(loadingView)

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
            }
        })
        addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        searchBar.onQueryChange = { query ->
            searchQuery = query
            onQueryChange?.invoke(query)
            if (query.isBlank()) {
                viewModel.updateSearchQuery(query)
                updateResults(emptyList())
            } else {
                viewModel.updateSearchQuery(query)
            }
        }
        searchBar.onCancel = { onCancel?.invoke() }

        applyTheme()
    }

    fun start() {
        if (viewScope != null) {
            searchBar.requestFocusAndShowKeyboard()
            return
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        viewScope = scope

        scope.launch {
            viewModel.searchCategories.collectLatest { categories ->
                updateResults(categories)
            }
        }
        scope.launch {
            viewModel.isSearching.collectLatest { searching ->
                loadingView.visibility = if (searching) View.VISIBLE else View.GONE
                if (searching) recyclerView.visibility = View.GONE
                else recyclerView.visibility = View.VISIBLE
            }
        }
        scope.launch {
            themeStore.themeState.collectLatest {
                applyTheme()
                recyclerView.adapter?.notifyDataSetChanged()
            }
        }

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

    private fun updateResults(categories: List<SearchCategory>) {
        val adapter = SearchCategoryAdapter(
            context = context,
            categories = categories,
            keywords = searchQuery,
            onResultClick = { onResultClick?.invoke(it) },
            onShowMore = { onShowMore?.invoke(it) }
        )
        recyclerView.adapter = adapter
    }

    private fun applyTheme() {
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        setBackgroundColor(colors.bgColorDefault)
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

private class SearchCategoryAdapter(
    private val context: Context,
    private val categories: List<SearchCategory>,
    private val keywords: String,
    private val onResultClick: (Any) -> Unit,
    private val onShowMore: (SearchType) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_CONTACT = 1
        private const val TYPE_GROUP = 2
        private const val TYPE_MESSAGE = 3
        private const val TYPE_MORE = 4
    }

    private data class Item(
        val type: Int,
        val category: SearchCategory? = null,
        val data: Any? = null
    )

    private val items = mutableListOf<Item>()

    init {
        for (category in categories) {
            items.add(Item(type = TYPE_HEADER, category = category))
            for (result in category.results) {
                val type = when (category.type) {
                    SearchType.FRIEND -> TYPE_CONTACT
                    SearchType.GROUP -> TYPE_GROUP
                    SearchType.MESSAGE -> TYPE_MESSAGE
                    else -> TYPE_CONTACT
                }
                items.add(Item(type = type, data = result))
            }
            if (category.hasMore) {
                items.add(Item(type = TYPE_MORE, category = category))
            }
        }
    }

    override fun getItemViewType(position: Int) = items[position].type

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(CategoryHeaderView(context))
            TYPE_MORE -> MoreViewHolder(CategoryMoreView(context))
            else -> ResultViewHolder(SearchResultItemView(context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

        when (holder) {
            is HeaderViewHolder -> {
                val category = item.category ?: return
                holder.view.bind(category, context)
            }
            is MoreViewHolder -> {
                val category = item.category ?: return
                holder.view.bind(category.type, context) {
                    onShowMore(category.type)
                }
            }
            is ResultViewHolder -> {
                val data = item.data ?: return
                when (data) {
                    is FriendSearchInfo -> holder.view.bindContact(data, keywords, colors) {
                        onResultClick(data)
                    }
                    is GroupSearchInfo -> holder.view.bindGroup(data, keywords, colors, context) {
                        onResultClick(data)
                    }
                    is MessageSearchResultItem -> holder.view.bindMessage(data, keywords, colors, context) {
                        onResultClick(data)
                    }
                }
            }
        }
    }

    private class HeaderViewHolder(val view: CategoryHeaderView) : RecyclerView.ViewHolder(view)

    private class ResultViewHolder(val view: SearchResultItemView) : RecyclerView.ViewHolder(view)

    private class MoreViewHolder(val view: CategoryMoreView) : RecyclerView.ViewHolder(view)
}

private class CategoryHeaderView(context: Context) : LinearLayout(context) {

    private val titleText: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dpToPx(1)
        }
        val dp20 = dpToPx(20)
        setPadding(dp20, dpToPx(10), dp20, dpToPx(2))

        titleText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        addView(titleText, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun bind(category: SearchCategory, context: Context) {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        setBackgroundColor(colors.bgColorOperate)
        titleText.setTextColor(colors.textColorSecondary)
        titleText.text = when (category.type) {
            SearchType.FRIEND -> context.getString(R.string.search_category_contact)
            SearchType.GROUP -> context.getString(R.string.search_category_group)
            SearchType.MESSAGE -> context.getString(R.string.search_category_chat_record)
            else -> ""
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

private class CategoryMoreView(context: Context) : LinearLayout(context) {

    private val moreText: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
        val dp20 = dpToPx(20)
        setPadding(dp20, dpToPx(10), dp20, dpToPx(10))

        moreText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        addView(moreText, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun bind(searchType: SearchType, context: Context, onClick: () -> Unit) {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        setBackgroundColor(colors.bgColorOperate)
        moreText.setTextColor(colors.textColorLink)
        moreText.text = when (searchType) {
            SearchType.FRIEND -> context.getString(R.string.search_view_more_contacts)
            SearchType.GROUP -> context.getString(R.string.search_view_more_groups)
            SearchType.MESSAGE -> context.getString(R.string.search_view_more_messages)
            else -> context.getString(R.string.search_more)
        }
        setOnClickListener { onClick() }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}

class SearchResultItemView(context: Context) : LinearLayout(context) {

    private val avatar: Avatar
    private val titleText: TextView
    private val subtitleText: TextView
    private val textContainer: LinearLayout

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val dp20 = dpToPx(20)
        val dp6 = dpToPx(6)
        setPadding(dp20, dp6, dp20, dp6 + dpToPx(3))
        layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            dpToPx(61)
        )

        avatar = Avatar(context)
        addView(avatar, LayoutParams(dpToPx(36), dpToPx(36)))

        textContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dpToPx(8)
            layoutParams = lp
        }
        addView(textContainer)

        titleText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            isSingleLine = true
        }
        textContainer.addView(titleText)

        subtitleText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            isSingleLine = true
        }
        textContainer.addView(subtitleText)
    }

    private fun applyRowBackground(colors: io.trtc.tuikit.atomicx.theme.tokens.ColorTokens) {
        val divider = ColorDrawable(colors.strokeColorPrimary)
        background = LayerDrawable(arrayOf(ColorDrawable(colors.bgColorOperate), divider)).apply {
            setLayerGravity(1, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL)
            setLayerSize(1, -1, 1)
            setLayerInset(1, dpToPx(20), 0, dpToPx(20), dpToPx(3))
        }
    }

    fun bindContact(
        contact: FriendSearchInfo,
        keywords: String,
        colors: io.trtc.tuikit.atomicx.theme.tokens.ColorTokens,
        onClick: () -> Unit
    ) {
        applyRowBackground(colors)
        avatar.setContent(Avatar.AvatarContent.Image(contact.userAvatarURL, contact.displayName))
        titleText.text = HighlightUtils.highlight(
            contact.displayName,
            keywords,
            colors.textColorLink,
            colors.textColorPrimary
        )
        subtitleText.text = HighlightUtils.highlightMultiple(
            listOf(
                HighlightSegment("ID: "),
                HighlightSegment(contact.userID, keywords)
            ),
            colors.textColorLink,
            colors.textColorTertiary
        )
        setOnClickListener { onClick() }
    }

    fun bindGroup(
        group: GroupSearchInfo,
        keywords: String,
        colors: io.trtc.tuikit.atomicx.theme.tokens.ColorTokens,
        context: Context,
        onClick: () -> Unit
    ) {
        applyRowBackground(colors)
        avatar.setContent(Avatar.AvatarContent.Image(group.groupAvatarURL, group.displayName))
        titleText.text = HighlightUtils.highlight(
            group.displayName,
            keywords,
            colors.textColorLink,
            colors.textColorPrimary
        )
        subtitleText.text = HighlightUtils.highlightMultiple(
            listOf(
                HighlightSegment("${context.getString(R.string.search_group_id)}: "),
                HighlightSegment(group.groupID, keywords)
            ),
            colors.textColorLink,
            colors.textColorTertiary
        )
        setOnClickListener { onClick() }
    }

    fun bindMessage(
        message: MessageSearchResultItem,
        keywords: String,
        colors: io.trtc.tuikit.atomicx.theme.tokens.ColorTokens,
        context: Context,
        onClick: () -> Unit
    ) {
        applyRowBackground(colors)
        avatar.setContent(Avatar.AvatarContent.Image(message.conversationAvatarURL, message.displayName))
        titleText.text = HighlightUtils.highlight(
            message.displayName,
            keywords,
            colors.textColorLink,
            colors.textColorPrimary
        )
        if (message.messageCount > 1) {
            subtitleText.setTag(R.id.emoji_span_bind_token_tag, null)
            subtitleText.text = context.getString(
                R.string.search_related_chat_record_count,
                message.messageCount
            )
            subtitleText.setTextColor(colors.textColorTertiary)
        } else {
            val firstMessage = message.messageList.firstOrNull()
            val rawAbstract = firstMessage?.getMessageAbstract(context) ?: ""
            val bindToken = "${message.conversationID}|$keywords|$rawAbstract|${subtitleText.textSize}"
            subtitleText.setTag(R.id.emoji_span_bind_token_tag, bindToken)
            subtitleText.text = HighlightUtils.highlight(
                EmojiSpanHelper.replaceEmojiKeysWithNames(rawAbstract),
                keywords,
                colors.textColorLink,
                colors.textColorTertiary
            )
            EmojiSpanHelper.applyEmojiSpans(
                context,
                HighlightUtils.highlight(rawAbstract, keywords, colors.textColorLink, colors.textColorTertiary),
                subtitleText.textSize,
                subtitleText
            ) { spanned ->
                if (subtitleText.getTag(R.id.emoji_span_bind_token_tag) == bindToken) {
                    subtitleText.text = spanned
                }
            }
        }
        setOnClickListener { onClick() }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
