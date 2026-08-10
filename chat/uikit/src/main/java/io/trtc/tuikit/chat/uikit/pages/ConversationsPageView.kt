package io.trtc.tuikit.chat.uikit.pages
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import io.trtc.tuikit.chat.uikit.components.conversationlist.config.ChatConversationActionConfig
import io.trtc.tuikit.chat.uikit.components.conversationlist.config.ConversationActionConfigProtocol
import io.trtc.tuikit.chat.uikit.components.conversationlist.ui.ConversationListView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.chat.uikit.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ConversationsPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val themeStore = ThemeStore.shared(context)
    private var viewScope: CoroutineScope? = null

    private val pageHeader: PageHeaderView
    private val coordinatorLayout: CoordinatorLayout
    private val appBarLayout: AppBarLayout
    private val searchBarWrapper: LinearLayout
    private val searchBarInner: LinearLayout
    private val searchIcon: ImageView
    private val searchText: TextView
    private val conversationListView: ConversationListView
    private val searchBarBgDrawable: GradientDrawable

    private var onSearchClick: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).toInt()

    init {
        orientation = VERTICAL
        layoutDirection = LAYOUT_DIRECTION_LOCALE

        pageHeader = PageHeaderView(context)
        addView(pageHeader, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        coordinatorLayout = CoordinatorLayout(context)
        addView(coordinatorLayout, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        appBarLayout = AppBarLayout(context).apply {
            fitsSystemWindows = false
            stateListAnimator = null
        }
        coordinatorLayout.addView(
            appBarLayout,
            CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.WRAP_CONTENT
            )
        )

        searchBarWrapper = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(16), dp(4), dp(16), dp(12))
        }
        val searchBarParams = AppBarLayout.LayoutParams(
            AppBarLayout.LayoutParams.MATCH_PARENT,
            AppBarLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
        }
        appBarLayout.addView(searchBarWrapper, searchBarParams)

        searchBarBgDrawable = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
        }

        searchBarInner = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            background = searchBarBgDrawable
            setOnClickListener { onSearchClick?.invoke() }
        }
        searchBarWrapper.addView(
            searchBarInner,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(36))
        )

        searchIcon = ImageView(context).apply {
            setImageResource(R.drawable.uikit_ic_search)
        }
        searchBarInner.addView(searchIcon, LayoutParams(dp(16), dp(16)))

        searchText = TextView(context).apply {
            text = context.getString(R.string.uikit_search_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.marginStart = dp(6)
            layoutParams = lp
        }
        searchBarInner.addView(searchText)

        conversationListView = ConversationListView(context)
        val listParams = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.MATCH_PARENT
        ).apply {
            behavior = AppBarLayout.ScrollingViewBehavior()
        }
        coordinatorLayout.addView(conversationListView, listParams)

        conversationListView.setup()
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyColors(state.currentTheme.tokens.color)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
    }

    private fun applyColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorOperate)
        appBarLayout.setBackgroundColor(colors.bgColorOperate)
        searchBarWrapper.setBackgroundColor(colors.bgColorOperate)
        searchBarBgDrawable.setColor(colors.bgColorInput)
        searchIcon.setColorFilter(colors.textColorTertiary)
        searchText.setTextColor(colors.textColorTertiary)
    }

    private fun setSearchBarVisible(visible: Boolean) {
        appBarLayout.visibility = if (visible) VISIBLE else GONE
    }

    @JvmOverloads
    fun setup(
        config: ConversationActionConfigProtocol = ChatConversationActionConfig(),
        showSearchBar: Boolean = true,
        headerTitle: String? = null,
        headerRightAction: View? = null,
        onConversationClick: ((ConversationInfo) -> Unit)? = null,
        onSearchClick: (() -> Unit)? = null
    ) {
        this.onSearchClick = onSearchClick
        setSearchBarVisible(showSearchBar)
        headerTitle?.let { pageHeader.setTitle(it) }
        headerRightAction?.let { pageHeader.setEditContent(it) }
        conversationListView.setup(
            config = config,
            onConversationClick = onConversationClick ?: {}
        )
    }
}
