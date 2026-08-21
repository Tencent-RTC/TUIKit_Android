package io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.pinyinhelper.Pinyin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AZOrderedListItem<T>(
    val key: String,
    val label: String,
    val avatarUrl: Any? = null,
    val extraData: T
)

data class AZOrderedListConfig(
    val showIndexBar: Boolean = true
)

internal data class AZGroup<T>(val letter: String, val items: List<AZOrderedListItem<T>>)

class AtomicAZOrderedList @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val INDEX_BAR_END_PADDING_DP = 8f
        private const val INDEX_BAR_VERTICAL_PADDING_DP = 16f
        private const val ITEM_HEIGHT_DP = 60f
        private const val ITEM_HORIZONTAL_PADDING_DP = 16f
        private const val AVATAR_TEXT_SPACING_DP = 12f
        private const val HEADER_HORIZONTAL_PADDING_DP = 16f
        private const val HEADER_VERTICAL_PADDING_DP = 6f
        private const val ITEM_TEXT_SIZE_SP = 18f
        private const val HEADER_TEXT_SIZE_SP = 14f
        private const val DIVIDER_HEIGHT_DP = 0.5f
        private const val DIVIDER_LEFT_MARGIN_DP = 68f
    }

    private val recyclerView: RecyclerView
    private val indexBar: AtomicIndexBar
    private var viewScope: CoroutineScope? = null

    private var groups: List<AZGroup<Any?>> = emptyList()
    private var flatItems: List<FlatItem> = emptyList()
    private var groupRanges: Map<String, Int> = emptyMap()
    private var config: AZOrderedListConfig = AZOrderedListConfig()
    private var onItemClickListener: ((AZOrderedListItem<Any?>) -> Unit)? = null
    private var onItemLongClickListener: ((AZOrderedListItem<Any?>, View) -> Unit)? = null
    private var headerView: View? = null
    private var footerView: View? = null

    var onUserInteraction: (() -> Unit)? = null

    private val listAdapter = AZListAdapter()

    init {
        layoutDirection = LAYOUT_DIRECTION_LOCALE
        recyclerView = RecyclerView(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutManager = LinearLayoutManager(context)
            adapter = listAdapter
            (itemAnimator as? SimpleItemAnimator)
                ?.supportsChangeAnimations = false
        }
        addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        indexBar = AtomicIndexBar(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            visibility = GONE
        }
        val indexBarLp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            val dm = resources.displayMetrics
            marginEnd = dp2px(INDEX_BAR_END_PADDING_DP, dm).toInt()
            topMargin = dp2px(INDEX_BAR_VERTICAL_PADDING_DP, dm).toInt()
            bottomMargin = dp2px(INDEX_BAR_VERTICAL_PADDING_DP, dm).toInt()
        }
        addView(indexBar, indexBarLp)

        indexBar.onLetterSelected = { letter ->
            groupRanges[letter]?.let { position ->
                (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
            }
        }

        indexBar.onDragStart = {
            recyclerView.stopScroll()
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                val firstVisible = lm.findFirstVisibleItemPosition()
                if (firstVisible == RecyclerView.NO_POSITION) return
                val item = flatItems.getOrNull(firstVisible) ?: return
                val letter = when (item) {
                    is FlatItem.SectionHeader -> item.letter
                    is FlatItem.DataItem -> item.letter
                    else -> null
                }
                if (letter != null) {
                    indexBar.setCurrentLetter(letter)
                }
            }
        })

        recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
                onUserInteraction?.invoke()
                return false
            }
        })

        recyclerView.addItemDecoration(StickyHeaderDecoration())
        recyclerView.addItemDecoration(ItemDividerDecoration())

        setBackgroundColor(getColors().bgColorOperate)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> setDataSource(dataSource: List<AZOrderedListItem<T>>) {
        val castData = dataSource as List<AZOrderedListItem<Any?>>
        groups = groupAndSort(castData)
        rebuildFlatItems()
        listAdapter.notifyDataSetChanged()

        val letters = groups.map { it.letter }
        indexBar.setLetters(letters)
        indexBar.visibility = if (config.showIndexBar && letters.isNotEmpty()) VISIBLE else GONE
    }

    fun setConfig(config: AZOrderedListConfig) {
        this.config = config
        val letters = groups.map { it.letter }
        indexBar.visibility = if (config.showIndexBar && letters.isNotEmpty()) VISIBLE else GONE
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> setOnItemClickListener(listener: (AZOrderedListItem<T>) -> Unit) {
        onItemClickListener = listener as ((AZOrderedListItem<Any?>) -> Unit)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> setOnItemLongClickListener(listener: (AZOrderedListItem<T>, View) -> Unit) {
        onItemLongClickListener = listener as ((AZOrderedListItem<Any?>, View) -> Unit)
    }

    fun setHeaderView(view: View?) {
        headerView = view
        rebuildFlatItems()
        listAdapter.notifyDataSetChanged()
    }

    fun setFooterView(view: View?) {
        footerView = view
        rebuildFlatItems()
        listAdapter.notifyDataSetChanged()
    }

    private sealed class FlatItem {
        data class CustomHeader(val view: View) : FlatItem()
        data class SectionHeader(val letter: String) : FlatItem()
        data class DataItem(val letter: String, val item: AZOrderedListItem<Any?>) : FlatItem()
        data class CustomFooter(val view: View) : FlatItem()
    }

    private fun rebuildFlatItems() {
        val items = mutableListOf<FlatItem>()
        val ranges = mutableMapOf<String, Int>()

        headerView?.let { items.add(FlatItem.CustomHeader(it)) }

        groups.forEach { group ->
            ranges[group.letter] = items.size
            items.add(FlatItem.SectionHeader(group.letter))
            group.items.forEach { item ->
                items.add(FlatItem.DataItem(group.letter, item))
            }
        }

        footerView?.let { items.add(FlatItem.CustomFooter(it)) }

        flatItems = items
        groupRanges = ranges
    }

    private inner class AZListAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_CUSTOM_HEADER = 0
        private val TYPE_SECTION_HEADER = 1
        private val TYPE_DATA_ITEM = 2
        private val TYPE_CUSTOM_FOOTER = 3

        override fun getItemViewType(position: Int): Int {
            return when (flatItems[position]) {
                is FlatItem.CustomHeader -> TYPE_CUSTOM_HEADER
                is FlatItem.SectionHeader -> TYPE_SECTION_HEADER
                is FlatItem.DataItem -> TYPE_DATA_ITEM
                is FlatItem.CustomFooter -> TYPE_CUSTOM_FOOTER
            }
        }

        override fun getItemCount(): Int = flatItems.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                TYPE_CUSTOM_HEADER, TYPE_CUSTOM_FOOTER -> {
                    val container = FrameLayout(parent.context).apply {
                        layoutParams = RecyclerView.LayoutParams(
                            RecyclerView.LayoutParams.MATCH_PARENT,
                            RecyclerView.LayoutParams.WRAP_CONTENT
                        )
                    }
                    CustomViewHolder(container)
                }

                TYPE_SECTION_HEADER -> {
                    SectionHeaderViewHolder(createSectionHeaderView(parent.context))
                }

                TYPE_DATA_ITEM -> {
                    DataItemViewHolder(createDataItemView(parent.context))
                }

                else -> throw IllegalArgumentException("Unknown viewType: $viewType")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = flatItems[position]
            val colors = getColors()

            when {
                holder is CustomViewHolder && item is FlatItem.CustomHeader -> {
                    val container = holder.itemView as FrameLayout
                    container.removeAllViews()
                    (item.view.parent as? ViewGroup)?.removeView(item.view)
                    container.addView(item.view)
                }

                holder is CustomViewHolder && item is FlatItem.CustomFooter -> {
                    val container = holder.itemView as FrameLayout
                    container.removeAllViews()
                    (item.view.parent as? ViewGroup)?.removeView(item.view)
                    container.addView(item.view)
                }

                holder is SectionHeaderViewHolder && item is FlatItem.SectionHeader -> {
                    holder.textView.text = item.letter
                    holder.textView.setTextColor(colors.textColorTertiary)
                    holder.itemView.setBackgroundColor(colors.bgColorInput)
                }

                holder is DataItemViewHolder && item is FlatItem.DataItem -> {
                    holder.textView.text = item.item.label
                    holder.textView.setTextColor(colors.textColorPrimary)
                    holder.itemView.setBackgroundColor(colors.bgColorOperate)
                    holder.avatar.setContent(
                        Avatar.AvatarContent.Image(item.item.avatarUrl, item.item.label)
                    )
                    holder.itemView.setOnClickListener {
                        onItemClickListener?.invoke(item.item)
                    }
                    holder.itemView.setOnLongClickListener {
                        onItemLongClickListener?.let { listener ->
                            listener.invoke(item.item, holder.itemView)
                            true
                        } ?: false
                    }
                }
            }
        }
    }

    private class CustomViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private class SectionHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewWithTag("sectionText")
    }

    private class DataItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: Avatar = view.findViewWithTag("avatar")
        val textView: TextView = view.findViewWithTag("label")
    }

    private fun createSectionHeaderView(context: Context): View {
        val dm = context.resources.displayMetrics
        val hPad = dp2px(HEADER_HORIZONTAL_PADDING_DP, dm).toInt()
        val vPad = dp2px(HEADER_VERTICAL_PADDING_DP, dm).toInt()
        val colors = getColors()

        return FrameLayout(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(colors.bgColorInput)
            setPadding(hPad, vPad, hPad, vPad)

            val textView = TextView(context).apply {
                tag = "sectionText"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, HEADER_TEXT_SIZE_SP)
                setTextColor(colors.textColorTertiary)
                textAlignment = TEXT_ALIGNMENT_VIEW_START
                textDirection = TEXT_DIRECTION_LOCALE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                layoutParams = LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
            }
            addView(textView)
        }
    }

    private fun createDataItemView(context: Context): View {
        val dm = context.resources.displayMetrics
        val itemHeightPx = dp2px(ITEM_HEIGHT_DP, dm).toInt()
        val hPad = dp2px(ITEM_HORIZONTAL_PADDING_DP, dm).toInt()
        val spacingPx = dp2px(AVATAR_TEXT_SPACING_DP, dm).toInt()
        val colors = getColors()

        val container = FrameLayout(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                itemHeightPx
            )
            setBackgroundColor(colors.bgColorOperate)
            setPadding(hPad, 0, hPad, 0)
        }

        val rowLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }

        val avatar = Avatar(context).apply {
            tag = "avatar"
            setSize(Avatar.AvatarSize.M)
        }
        rowLayout.addView(avatar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(spacingPx, 1)
        }
        rowLayout.addView(spacer)

        val textView = TextView(context).apply {
            tag = "label"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, ITEM_TEXT_SIZE_SP)
            setTextColor(colors.textColorPrimary)
            textAlignment = TEXT_ALIGNMENT_VIEW_START
            textDirection = TEXT_DIRECTION_LOCALE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        rowLayout.addView(textView)

        container.addView(rowLayout)
        return container
    }

    private inner class StickyHeaderDecoration : RecyclerView.ItemDecoration() {

        private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val lm = parent.layoutManager as? LinearLayoutManager ?: return
            val firstVisiblePos = lm.findFirstVisibleItemPosition()
            if (firstVisiblePos == RecyclerView.NO_POSITION) return

            val dm = parent.context.resources.displayMetrics
            val colors = getColors()
            val hPad = dp2px(HEADER_HORIZONTAL_PADDING_DP, dm)
            val vPad = dp2px(HEADER_VERTICAL_PADDING_DP, dm)
            val isRtl = parent.layoutDirection == LAYOUT_DIRECTION_RTL
            textPaint.textAlign = if (isRtl) Paint.Align.RIGHT else Paint.Align.LEFT
            textPaint.textSize = dp2px(HEADER_TEXT_SIZE_SP, dm)
            textPaint.color = colors.textColorTertiary
            headerPaint.color = colors.bgColorInput

            val currentItem = flatItems.getOrNull(firstVisiblePos) ?: return
            val currentLetter = when (currentItem) {
                is FlatItem.SectionHeader -> currentItem.letter
                is FlatItem.DataItem -> currentItem.letter
                else -> return
            }

            val textHeight = textPaint.descent() - textPaint.ascent()
            val stickyHeaderHeight = textHeight + vPad * 2

            var stickyTop = 0f
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i) ?: continue
                val adapterPos = parent.getChildAdapterPosition(child)
                if (adapterPos == RecyclerView.NO_POSITION) continue
                val item = flatItems.getOrNull(adapterPos) ?: continue
                if (item is FlatItem.SectionHeader && item.letter != currentLetter) {
                    val childTop = child.top.toFloat()
                    if (childTop < stickyHeaderHeight && childTop > 0) {
                        stickyTop = childTop - stickyHeaderHeight
                        break
                    }
                }
            }

            c.drawRect(0f, stickyTop, parent.width.toFloat(), stickyTop + stickyHeaderHeight, headerPaint)
            val textY = stickyTop + vPad - textPaint.ascent()
            val textX = if (isRtl) parent.width.toFloat() - hPad else hPad
            c.drawText(currentLetter, textX, textY, textPaint)
        }
    }

    private inner class ItemDividerDecoration : RecyclerView.ItemDecoration() {

        private val dividerPaint = Paint().apply {
            style = Paint.Style.FILL
        }

        override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val dm = parent.context.resources.displayMetrics
            val colors = getColors()
            dividerPaint.color = colors.strokeColorSecondary
            val dividerHeight = dp2px(DIVIDER_HEIGHT_DP, dm).coerceAtLeast(1f)
            val startMargin = dp2px(DIVIDER_LEFT_MARGIN_DP, dm)
            val isRtl = parent.layoutDirection == LAYOUT_DIRECTION_RTL

            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i) ?: continue
                val adapterPos = parent.getChildAdapterPosition(child)
                if (adapterPos == RecyclerView.NO_POSITION) continue

                val currentItem = flatItems.getOrNull(adapterPos) ?: continue
                val nextItem = flatItems.getOrNull(adapterPos + 1)

                if (currentItem is FlatItem.DataItem && nextItem is FlatItem.DataItem) {
                    val top = child.bottom.toFloat()
                    val left = if (isRtl) 0f else startMargin
                    val right = if (isRtl) parent.width.toFloat() - startMargin else parent.width.toFloat()
                    c.drawRect(left, top, right, top + dividerHeight, dividerPaint)
                }
            }
        }
    }

    private fun getFirstLetter(name: String): String {
        if (name.isEmpty()) return "#"
        val firstChar = name[0]
        if (firstChar.isLetter() && firstChar.code < 128) {
            return firstChar.uppercaseChar().toString()
        }
        if (firstChar.isDigit()) return "#"
        if (Pinyin.isChinese(firstChar)) {
            val pinyin = Pinyin.toPinyin(firstChar)
            return if (pinyin.isNotEmpty()) pinyin[0].uppercaseChar().toString() else "#"
        }
        return "#"
    }

    private fun <T> groupAndSort(items: List<AZOrderedListItem<T>>): List<AZGroup<T>> {
        val grouped = items.groupBy { item -> getFirstLetter(item.label) }
        val sortedGroups = grouped.map { (letter, groupItems) ->
            val sorted = groupItems.sortedBy { it.label.lowercase() }
            AZGroup(letter, sorted)
        }
        return sortedGroups.sortedWith { g1, g2 ->
            when {
                g1.letter == "#" && g2.letter != "#" -> 1
                g1.letter != "#" && g2.letter == "#" -> -1
                else -> g1.letter.compareTo(g2.letter)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                val colors = it.currentTheme.tokens.color
                setBackgroundColor(colors.bgColorOperate)
                listAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }

}
