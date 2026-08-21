package io.trtc.tuikit.chat.uikit.components.contactlist.ui
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.AppBarLayout
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.common.findViewModelStoreOwner
import io.trtc.tuikit.chat.uikit.components.contactlist.config.ChatContactListConfig
import io.trtc.tuikit.chat.uikit.components.contactlist.config.ContactListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.contactlist.model.ContactCustomItem
import io.trtc.tuikit.chat.uikit.components.contactlist.model.ContactCustomItemContext
import io.trtc.tuikit.chat.uikit.components.contactlist.model.buildContactListItems
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.matchesSearchQuery
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.ContactListViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.ContactListViewModelFactory
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.AZOrderedListItem
import io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.AtomicAZOrderedList
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ContactListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val DEFAULT_ITEM_HEIGHT_DP = 60f
        private const val DEFAULT_ITEM_HORIZONTAL_PADDING_DP = 16f
        private const val DEFAULT_ITEM_ICON_SIZE_DP = 40f
        private const val DEFAULT_ITEM_ICON_TEXT_SPACING_DP = 12f
        private const val DEFAULT_ITEM_TITLE_SIZE_SP = 18f
        private const val BADGE_SIZE_DP = 18f
        private const val BADGE_TEXT_SIZE_SP = 11f
        private const val DIVIDER_HEIGHT_DP = 0.5f
        private const val DIVIDER_LEFT_MARGIN_DP = 68f
        private const val EMPTY_ILLUSTRATION_WIDTH_DP = 88f
        private const val EMPTY_TEXT_SIZE_SP = 14f
        private const val EMPTY_TEXT_TOP_MARGIN_DP = 14f
    }

    private val azOrderedList: AtomicAZOrderedList
    private val appBarLayout: AppBarLayout
    private val emptyView: LinearLayout
    private val emptyTextView: TextView
    private lateinit var viewModel: ContactListViewModel
    private lateinit var searchBarView: ContactListSearchBarView
    private var viewScope: CoroutineScope? = null
    private var searchQuery: String = ""
    private var allContacts: List<ContactInfo> = emptyList()
    private var initialLoadFinished = false
    private val viewModelKey = "${ContactListViewModel::class.java.name}:${System.identityHashCode(this)}"

    private var onContactClick: ((ContactInfo) -> Unit)? = null
    private var onGroupClick: ((ContactInfo) -> Unit)? = null
    var onCreateChat: ((String) -> Unit)? = null

    private var headerView: LinearLayout? = null
    private val defaultItemViews = mutableMapOf<String, DefaultItemViewHolder>()
    private var headerItems: List<ContactCustomItem> = emptyList()

    init {
        layoutDirection = LAYOUT_DIRECTION_LOCALE

        val coordinatorLayout = CoordinatorLayout(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val appBar = AppBarLayout(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            fitsSystemWindows = false
            stateListAnimator = null
            setBackgroundColor(getColors().bgColorOperate)
        }
        appBarLayout = appBar
        coordinatorLayout.addView(
            appBar,
            CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.WRAP_CONTENT
            )
        )

        searchBarView = ContactListSearchBarView(context).apply {
            onQueryChange = { query ->
                searchQuery = query
                renderFriendList()
            }
        }
        val searchBarParams = AppBarLayout.LayoutParams(
            AppBarLayout.LayoutParams.MATCH_PARENT,
            AppBarLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or
                AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
        }
        appBarLayout.addView(searchBarView, searchBarParams)

        azOrderedList = AtomicAZOrderedList(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            onUserInteraction = {
                searchBarView.hideKeyboard()
            }
            setOnTouchListener { _, _ ->
                searchBarView.hideKeyboard()
                false
            }
        }
        val listParams = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.MATCH_PARENT
        ).apply {
            behavior = AppBarLayout.ScrollingViewBehavior()
        }
        coordinatorLayout.addView(azOrderedList, listParams)

        val dm = resources.displayMetrics
        emptyTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, EMPTY_TEXT_SIZE_SP)
            setText(R.string.uikit_empty_content)
            setTextColor(getColors().textColorTertiary)
        }
        emptyView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            visibility = GONE

            val illustrationView = ImageView(context).apply {
                setImageResource(R.drawable.uikit_empty_illustration)
                adjustViewBounds = true
                layoutParams = LinearLayout.LayoutParams(
                    dp2px(EMPTY_ILLUSTRATION_WIDTH_DP, dm).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            addView(illustrationView)

            val textParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp2px(EMPTY_TEXT_TOP_MARGIN_DP, dm).toInt()
            }
            addView(emptyTextView, textParams)
        }
        coordinatorLayout.addView(
            emptyView,
            CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                CoordinatorLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                behavior = AppBarLayout.ScrollingViewBehavior()
            }
        )

        addView(coordinatorLayout)
        setBackgroundColor(getColors().bgColorOperate)
    }

    @JvmOverloads
    fun setup(
        config: ContactListConfigProtocol = ChatContactListConfig(),
        onContactClick: ((ContactInfo) -> Unit)? = null,
        onGroupClick: ((ContactInfo) -> Unit)? = null
    ) {
        this.onContactClick = onContactClick
        this.onGroupClick = onGroupClick

        appBarLayout.visibility = if (config.showSearchBar) View.VISIBLE else View.GONE
        if (!config.showSearchBar) {
            searchQuery = ""
            searchBarView.hideKeyboard()
        }

        val contactStore = ContactStore.shared
        val groupStore = GroupStore.shared
        cleanupBinding()

        val owner = context.findViewModelStoreOwner()
            ?: error("ContactListView requires a ViewModelStoreOwner host context.")
        viewModel = ViewModelProvider(
            owner,
            ContactListViewModelFactory(contactStore, groupStore)
        ).get(viewModelKey, ContactListViewModel::class.java)

        val defaultItems = viewModel.getDefaultItems(
            config = config,
            onNavigateToFriendApplications = {
                FriendApplicationDialog(context, contactStore).show()
            },
            onNavigateToGroupApplications = {
                GroupApplicationDialog(context, groupStore).show()
            },
            onNavigateToMyGroup = {
                MyGroupDialog(context, groupStore) { groupInfo ->
                    onGroupClick?.invoke(groupInfo)
                }.show()
            },
            onNavigateToBlacklist = {
                BlacklistDialog(context, contactStore) { contactInfo ->
                    onContactClick?.invoke(contactInfo)
                }.show()
            }
        )
        this.headerItems = buildContactListItems(
            itemContext = ContactCustomItemContext(context),
            defaults = defaultItems,
            customizer = config.itemCustomizer,
        )

        defaultItemViews.clear()
        headerView = buildHeaderView(this.headerItems)
        azOrderedList.setHeaderView(headerView)
        headerView?.setOnTouchListener { _, _ ->
            searchBarView.hideKeyboard()
            false
        }

        azOrderedList.setOnItemClickListener<ContactInfo> { item ->
            onContactClick?.invoke(item.extraData)
        }

        if (isAttachedToWindow) {
            bindViewModel()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!::viewModel.isInitialized) return
        bindViewModel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
    }

    private fun bindViewModel() {
        if (viewScope != null || !::viewModel.isInitialized) return

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        viewScope = scope

        scope.launch {
            viewModel.friendList.collectLatest { list ->
                allContacts = list
                renderFriendList()
            }
        }

        scope.launch {
            viewModel.initialLoadFinished.collectLatest { finished ->
                initialLoadFinished = finished
                renderFriendList()
            }
        }

        headerItems.forEach { item ->
            scope.launch {
                item.badgeCount.collectLatest { count ->
                    updateBadge(item.ID, count)
                }
            }
        }

        scope.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                val colors = it.currentTheme.tokens.color
                setBackgroundColor(colors.bgColorOperate)
                appBarLayout.setBackgroundColor(colors.bgColorOperate)
                emptyTextView.setTextColor(colors.textColorTertiary)
                searchBarView.applyTheme()
                refreshHeaderColors(colors)
            }
        }
    }

    private fun cleanupBinding() {
        viewScope?.cancel()
        viewScope = null
        headerView?.let { azOrderedList.setHeaderView(null) }
        headerView = null
        headerItems = emptyList()
        defaultItemViews.clear()
    }

    fun showAddContactDialog() {
        if (!::viewModel.isInitialized) return
        ContactFlowLauncher.showAddFriendPage(
            context = context,
            contactStore = viewModel.contactStore,
            groupStore = viewModel.groupStore
        )
    }

    fun showJoinGroupDialog() {
        if (!::viewModel.isInitialized) return
        ContactFlowLauncher.showAddGroupPage(
            context = context,
            contactStore = viewModel.contactStore,
            groupStore = viewModel.groupStore
        )
    }

    fun showCreateSingleChatDialog() {
        if (!::viewModel.isInitialized) return
        ContactFlowLauncher.showStartSingleChatPage(
            context = context,
            contactStore = viewModel.contactStore,
            groupStore = viewModel.groupStore,
            onCreateChat = { conversationId ->
                onCreateChat?.invoke(conversationId)
            }
        )
    }

    fun showCreateGroupDialog() {
        if (!::viewModel.isInitialized) return
        ContactFlowLauncher.showCreateGroupChatPage(
            context = context,
            contactStore = viewModel.contactStore,
            groupStore = viewModel.groupStore,
            onCreateChat = { conversationId ->
                onCreateChat?.invoke(conversationId)
            }
        )
    }

    private fun renderFriendList() {
        val isSearching = searchQuery.isNotBlank()
        headerView?.visibility = if (isSearching) View.GONE else View.VISIBLE
        emptyView.visibility = if (!isSearching && initialLoadFinished && allContacts.isEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }

        val filteredContacts = allContacts.filter { contact ->
            contact.matchesSearchQuery(searchQuery)
        }
        azOrderedList.setDataSource(
            filteredContacts.map { contact ->
                AZOrderedListItem(
                    key = contact.userID,
                    label = contact.displayName,
                    avatarUrl = contact.avatarURL,
                    extraData = contact
                )
            }
        )
    }

    private fun buildHeaderView(items: List<ContactCustomItem>): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        items.forEachIndexed { index, item ->
            val holder = createDefaultItemView(item)
            defaultItemViews[item.ID] = holder
            container.addView(holder.itemContainer)

            if (index < items.size - 1) {
                container.addView(createDivider())
            }
        }

        return container
    }

    private fun createDefaultItemView(item: ContactCustomItem): DefaultItemViewHolder {
        val dm = resources.displayMetrics
        val colors = getColors()
        val itemHeightPx = dp2px(DEFAULT_ITEM_HEIGHT_DP, dm).toInt()
        val hPad = dp2px(DEFAULT_ITEM_HORIZONTAL_PADDING_DP, dm).toInt()
        val iconSizePx = dp2px(DEFAULT_ITEM_ICON_SIZE_DP, dm).toInt()
        val iconTextSpacingPx = dp2px(DEFAULT_ITEM_ICON_TEXT_SPACING_DP, dm).toInt()
        val badgeSizePx = dp2px(BADGE_SIZE_DP, dm).toInt()

        val itemContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(colors.bgColorOperate)
        }

        val rowView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                itemHeightPx
            )
            setPadding(hPad, 0, hPad, 0)
            setOnClickListener { item.onClick() }
        }

        val iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx)
            if (item.iconResID != 0) {
                setImageDrawable(ContextCompat.getDrawable(context, item.iconResID)?.apply {
                    isAutoMirrored = true
                })
            } else {
                visibility = GONE
            }
        }
        rowView.addView(iconView)

        val iconTextSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconTextSpacingPx, 1)
            if (item.iconResID == 0) {
                visibility = GONE
            }
        }
        rowView.addView(iconTextSpacer)

        val titleView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, DEFAULT_ITEM_TITLE_SIZE_SP)
            setTextColor(colors.textColorPrimary)
            text = item.title.ifEmpty {
                if (item.titleResID != 0) context.getString(item.titleResID) else ""
            }
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textDirection = View.TEXT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        rowView.addView(titleView)

        val badgeView = BadgeView(context).apply {
            layoutParams = LinearLayout.LayoutParams(badgeSizePx, badgeSizePx)
            visibility = GONE
        }
        rowView.addView(badgeView)

        itemContainer.addView(rowView)

        return DefaultItemViewHolder(
            itemContainer = itemContainer,
            rowView = rowView,
            iconView = iconView,
            titleView = titleView,
            badgeView = badgeView
        )
    }

    private fun createDivider(): View {
        val dm = resources.displayMetrics
        val colors = getColors()
        val dividerHeightPx = dp2px(DIVIDER_HEIGHT_DP, dm).toInt().coerceAtLeast(1)
        val dividerStartMarginPx = dp2px(DIVIDER_LEFT_MARGIN_DP, dm).toInt()

        return View(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            setBackgroundColor(colors.strokeColorSecondary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dividerHeightPx
            ).apply {
                marginStart = dividerStartMarginPx
            }
            tag = "divider"
        }
    }

    private fun updateBadge(itemID: String, count: Int) {
        val holder = defaultItemViews[itemID] ?: return
        if (count > 0) {
            holder.badgeView.visibility = VISIBLE
            holder.badgeView.count = count
            holder.badgeView.invalidate()
        } else {
            holder.badgeView.visibility = GONE
        }
    }

    private fun refreshHeaderColors(colors: ColorTokens) {
        defaultItemViews.values.forEach { holder ->
            holder.itemContainer.setBackgroundColor(colors.bgColorOperate)
            holder.titleView.setTextColor(colors.textColorPrimary)
            holder.badgeView.setBadgeColor(colors.textColorError)
        }

        headerView?.let { container ->
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child.tag == "divider") {
                    child.setBackgroundColor(colors.strokeColorSecondary)
                }
            }
        }
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }

    private data class DefaultItemViewHolder(
        val itemContainer: LinearLayout,
        val rowView: LinearLayout,
        val iconView: ImageView,
        val titleView: TextView,
        val badgeView: BadgeView
    )

    private class BadgeView(context: Context) : View(context) {

        var count: Int = 0

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            color = 0xFFFFFFFF.toInt()
        }

        init {
            val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
            bgPaint.color = colors.textColorError
            textPaint.textSize = context.resources.displayMetrics.density * BADGE_TEXT_SIZE_SP
        }

        fun setBadgeColor(color: Int) {
            bgPaint.color = color
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (count <= 0) return
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) / 2f
            canvas.drawCircle(cx, cy, radius, bgPaint)

            val text = if (count > 99) "99+" else count.toString()
            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(text, cx, textY, textPaint)
        }
    }

}
