package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputFill
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputMorePanelAppearance
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputMenuAction
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MoreActionsPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val COLUMNS = 4
        private const val ROWS_PER_PAGE = 2
        private const val PAGE_SIZE = COLUMNS * ROWS_PER_PAGE

        private const val PAGE_INDICATOR_DOT_SIZE_DP = 5
        private const val PAGE_INDICATOR_DOT_SPACING_DP = 4
        private const val PAGE_INDICATOR_VERTICAL_PADDING_DP = 6
        private const val PAGE_INDICATOR_INACTIVE_ALPHA = 64

        private const val ITEM_SIZE_DP = 56
        private const val ITEM_ICON_SIZE_DP = 28
        private const val ITEM_CORNER_RADIUS_DP = 12
        private const val ITEM_TITLE_SIZE_SP = 12f
    }

    private val viewPager: ViewPager2
    private val indicatorContainer: LinearLayout
    private var pagerAdapter: MoreActionsPagerAdapter? = null
    private var viewScope: CoroutineScope? = null
    private var appearance: MessageInputMorePanelAppearance? = null
    private var onRedDotDismissed: ((actionID: String) -> Unit)? = null

    private val density: Float
        get() = resources.displayMetrics.density

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE

        val dp16 = (16 * density).toInt()
        setPadding(dp16, dp16, dp16, dp16)

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        addView(
            rootLayout,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        viewPager = ViewPager2(context).apply {
            id = View.generateViewId()
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            offscreenPageLimit = 1
        }
        rootLayout.addView(
            viewPager,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        indicatorContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val paddingV = pageIndicatorVerticalPadding()
            setPadding(0, paddingV, 0, paddingV)
            visibility = View.GONE
        }
        rootLayout.addView(
            indicatorContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    fun setActions(actions: List<MessageInputMenuAction>, dismissedRedDotIds: Set<String> = emptySet()) {
        val pages = if (actions.isEmpty()) {
            listOf(emptyList())
        } else {
            actions.chunked(PAGE_SIZE)
        }
        val newAdapter = MoreActionsPagerAdapter(
            pages = pages,
            appearanceProvider = { appearance },
            dismissedRedDotIds = dismissedRedDotIds,
            onRedDotDismissed = { actionID -> onRedDotDismissed?.invoke(actionID) }
        )
        pagerAdapter = newAdapter
        viewPager.adapter = newAdapter
        rebuildIndicator(pages.size)
    }

    fun setOnRedDotDismissedListener(listener: ((actionID: String) -> Unit)?) {
        onRedDotDismissed = listener
    }

    fun setAppearance(appearance: MessageInputMorePanelAppearance?) {
        if (this.appearance == appearance) {
            return
        }
        this.appearance = appearance
        applyThemeColors(ThemeStore.shared(context).themeState.value.currentTheme.tokens.color)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                applyThemeColors(it.currentTheme.tokens.color)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        viewScope?.cancel()
        viewScope = null
    }

    private fun applyThemeColors(colors: ColorTokens) {
        background = MessageInputAppearanceApplier.fillDrawable(
            fill = appearance?.fill ?: MessageInputFill.Color(colors.bgColorOperate),
            cornerRadiusDp = null,
            density = density
        )
        pagerAdapter?.notifyPageItemsChanged()
        applyIndicatorColors(colors)
    }

    private fun pageIndicatorDotSize(): Int = (PAGE_INDICATOR_DOT_SIZE_DP * density).toInt()

    private fun pageIndicatorDotSpacing(): Int = (PAGE_INDICATOR_DOT_SPACING_DP * density).toInt()

    private fun pageIndicatorVerticalPadding(): Int =
        (PAGE_INDICATOR_VERTICAL_PADDING_DP * density).toInt()

    private fun rebuildIndicator(pageCount: Int) {
        viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        indicatorContainer.removeAllViews()
        if (pageCount <= 1) {
            indicatorContainer.visibility = View.GONE
            return
        }
        indicatorContainer.visibility = View.VISIBLE

        val dotSize = pageIndicatorDotSize()
        val spacing = pageIndicatorDotSpacing()
        for (i in 0 until pageCount) {
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                }
            }
            val lp = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                if (i > 0) marginStart = spacing
            }
            indicatorContainer.addView(dot, lp)
        }

        viewPager.registerOnPageChangeCallback(pageChangeCallback)

        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        applyIndicatorColors(colors)
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
            applyIndicatorColors(colors, position)
        }
    }

    private fun applyIndicatorColors(
        colors: ColorTokens,
        activePosition: Int = viewPager.currentItem
    ) {
        if (indicatorContainer.childCount == 0) return
        val activeColor = colors.textColorPrimary
        val inactiveColor = ColorUtils.setAlphaComponent(
            colors.textColorPrimary, PAGE_INDICATOR_INACTIVE_ALPHA
        )
        for (i in 0 until indicatorContainer.childCount) {
            val dot = indicatorContainer.getChildAt(i)
            val drawable = dot.background as? GradientDrawable ?: continue
            drawable.setColor(if (i == activePosition) activeColor else inactiveColor)
        }
    }

    private class MoreActionsPagerAdapter(
        private val pages: List<List<MessageInputMenuAction>>,
        private val appearanceProvider: () -> MessageInputMorePanelAppearance?,
        private val dismissedRedDotIds: Set<String>,
        private val onRedDotDismissed: (actionID: String) -> Unit
    ) : RecyclerView.Adapter<MoreActionsPagerAdapter.PageViewHolder>() {

        class PageViewHolder(
            val recyclerView: RecyclerView,
            val adapter: MoreActionsAdapter
        ) : RecyclerView.ViewHolder(recyclerView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val context = parent.context
            val pageAdapter = MoreActionsAdapter(
                actions = emptyList(),
                dismissedRedDotIds = dismissedRedDotIds,
                appearanceProvider = appearanceProvider,
                onRedDotDismissed = onRedDotDismissed
            )
            val recyclerView = RecyclerView(context).apply {
                layoutDirection = View.LAYOUT_DIRECTION_LOCALE
                layoutManager = GridLayoutManager(context, COLUMNS)
                overScrollMode = OVER_SCROLL_NEVER
                adapter = pageAdapter
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            return PageViewHolder(recyclerView, pageAdapter)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.adapter.submitActions(
                newActions = pages.getOrNull(position).orEmpty(),
                firstActionIndex = position * PAGE_SIZE
            )
        }

        override fun getItemCount(): Int = pages.size

        fun notifyPageItemsChanged() {
            notifyDataSetChanged()
        }
    }

    private class MoreActionsAdapter(
        private var actions: List<MessageInputMenuAction>,
        private val dismissedRedDotIds: Set<String>,
        private val appearanceProvider: () -> MessageInputMorePanelAppearance?,
        private val onRedDotDismissed: (actionID: String) -> Unit
    ) : RecyclerView.Adapter<MoreActionsAdapter.ViewHolder>() {

        private var firstActionIndex = 0

        class ViewHolder(
            val container: LinearLayout,
            val iconContainer: FrameLayout,
            val iconView: ImageView,
            val titleView: TextView,
            val redDot: View
        ) : RecyclerView.ViewHolder(container)

        fun submitActions(newActions: List<MessageInputMenuAction>, firstActionIndex: Int = 0) {
            actions = newActions
            this.firstActionIndex = firstActionIndex
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            val density = context.resources.displayMetrics.density

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LOCALE
                val lp = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                val dp8 = (8 * density).toInt()
                lp.topMargin = dp8
                lp.bottomMargin = dp8
                layoutParams = lp
            }

            val iconContainer = FrameLayout(context).apply {
                layoutDirection = View.LAYOUT_DIRECTION_LOCALE
                val lp = LinearLayout.LayoutParams(0, 0)
                lp.gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = lp
            }

            val iconView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                val lp = FrameLayout.LayoutParams(0, 0)
                lp.gravity = Gravity.CENTER
                layoutParams = lp
            }
            iconContainer.addView(iconView)

            val dp8 = (8 * density).toInt()
            val redDot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                }
                val lp = FrameLayout.LayoutParams(dp8, dp8)
                lp.gravity = Gravity.TOP or Gravity.END
                layoutParams = lp
                visibility = View.GONE
            }
            iconContainer.addView(redDot)
            container.addView(iconContainer)

            val dp4 = (4 * density).toInt()
            val titleView = TextView(context).apply {
                gravity = Gravity.CENTER
                textDirection = View.TEXT_DIRECTION_LOCALE
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dp4
                layoutParams = lp
            }
            container.addView(titleView)

            return ViewHolder(container, iconContainer, iconView, titleView, redDot)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val action = actions[position]
            val context = holder.itemView.context
            val density = context.resources.displayMetrics.density
            val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
            val appearance = appearanceProvider()

            holder.iconView.setImageResource(action.iconResID)
            holder.iconView.setColorFilter(appearance?.itemIconTint ?: colors.textColorPrimary)
            holder.titleView.text = action.title
            holder.titleView.setTextColor(appearance?.itemTitleColor ?: colors.textColorSecondary)
            holder.titleView.textSize = appearance?.itemTitleSizeSp ?: ITEM_TITLE_SIZE_SP

            applyItemMetrics(holder, appearance, density)
            holder.iconContainer.background = MessageInputAppearanceApplier.fillDrawable(
                fill = resolveItemFill(appearance, position, colors),
                cornerRadiusDp = appearance?.itemCornerRadiusDp ?: ITEM_CORNER_RADIUS_DP,
                density = density
            )

            val redDotBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colors.textColorError)
            }
            holder.redDot.background = redDotBg
            holder.redDot.visibility =
                if (action.showRedDot && action.ID !in dismissedRedDotIds) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            holder.container.setOnClickListener {
                if (action.showRedDot) {
                    holder.redDot.visibility = View.GONE
                    onRedDotDismissed(action.ID)
                }
                action.onClick()
            }
        }

        override fun getItemCount(): Int = actions.size

        private fun applyItemMetrics(
            holder: ViewHolder,
            appearance: MessageInputMorePanelAppearance?,
            density: Float
        ) {
            val itemSizePx = ((appearance?.itemSizeDp ?: ITEM_SIZE_DP) * density).toInt()
            holder.iconContainer.layoutParams = holder.iconContainer.layoutParams.apply {
                width = itemSizePx
                height = itemSizePx
            }
            val iconSizePx = ((appearance?.itemIconSizeDp ?: ITEM_ICON_SIZE_DP) * density).toInt()
            holder.iconView.layoutParams = holder.iconView.layoutParams.apply {
                width = iconSizePx
                height = iconSizePx
            }
        }

        /**
         * Item fills are indexed by the action's position across the whole panel and cycle when the
         * host supplied fewer fills than there are actions, so a short color sequence repeats
         * instead of leaving later entries unstyled.
         */
        private fun resolveItemFill(
            appearance: MessageInputMorePanelAppearance?,
            position: Int,
            colors: ColorTokens
        ): MessageInputFill {
            val fills = appearance?.itemFills.orEmpty()
            if (fills.isEmpty()) {
                return MessageInputFill.Color(colors.bgColorTopBar)
            }
            return fills[(firstActionIndex + position) % fills.size]
        }
    }
}
