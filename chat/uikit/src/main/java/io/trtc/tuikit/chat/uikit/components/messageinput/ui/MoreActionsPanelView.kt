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
    }

    private val viewPager: ViewPager2
    private val indicatorContainer: LinearLayout
    private var pagerAdapter: MoreActionsPagerAdapter? = null
    private var viewScope: CoroutineScope? = null

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

    fun setActions(actions: List<MessageInputMenuAction>) {
        val pages = if (actions.isEmpty()) {
            listOf(emptyList())
        } else {
            actions.chunked(PAGE_SIZE)
        }
        val newAdapter = MoreActionsPagerAdapter(pages)
        pagerAdapter = newAdapter
        viewPager.adapter = newAdapter
        rebuildIndicator(pages.size)
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
        setBackgroundColor(colors.bgColorOperate)
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
        private val pages: List<List<MessageInputMenuAction>>
    ) : RecyclerView.Adapter<MoreActionsPagerAdapter.PageViewHolder>() {

        class PageViewHolder(
            val recyclerView: RecyclerView,
            val adapter: MoreActionsAdapter
        ) : RecyclerView.ViewHolder(recyclerView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val context = parent.context
            val pageAdapter = MoreActionsAdapter(emptyList())
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
            holder.adapter.submitActions(pages.getOrNull(position).orEmpty())
        }

        override fun getItemCount(): Int = pages.size

        fun notifyPageItemsChanged() {
            notifyDataSetChanged()
        }
    }

    private class MoreActionsAdapter(
        private var actions: List<MessageInputMenuAction>
    ) : RecyclerView.Adapter<MoreActionsAdapter.ViewHolder>() {

        class ViewHolder(
            val container: LinearLayout,
            val iconContainer: FrameLayout,
            val iconView: ImageView,
            val titleView: TextView
        ) : RecyclerView.ViewHolder(container)

        fun submitActions(newActions: List<MessageInputMenuAction>) {
            actions = newActions
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

            val dp56 = (56 * density).toInt()
            val iconContainer = FrameLayout(context).apply {
                val lp = LinearLayout.LayoutParams(dp56, dp56)
                lp.gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = lp
            }

            val dp28 = (28 * density).toInt()
            val iconView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                val lp = FrameLayout.LayoutParams(dp28, dp28)
                lp.gravity = Gravity.CENTER
                layoutParams = lp
            }
            iconContainer.addView(iconView)
            container.addView(iconContainer)

            val dp4 = (4 * density).toInt()
            val titleView = TextView(context).apply {
                gravity = Gravity.CENTER
                textDirection = View.TEXT_DIRECTION_LOCALE
                textSize = 12f
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dp4
                layoutParams = lp
            }
            container.addView(titleView)

            return ViewHolder(container, iconContainer, iconView, titleView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val action = actions[position]
            val context = holder.itemView.context
            val density = context.resources.displayMetrics.density
            val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

            holder.iconView.setImageResource(action.iconResID)
            holder.iconView.setColorFilter(colors.textColorPrimary)
            holder.titleView.text = action.title
            holder.titleView.setTextColor(colors.textColorSecondary)

            val iconBg = GradientDrawable().apply {
                setColor(colors.bgColorTopBar)
                cornerRadius = 12 * density
            }
            holder.iconContainer.background = iconBg

            holder.container.setOnClickListener {
                action.onClick()
            }
        }

        override fun getItemCount(): Int = actions.size
    }
}
