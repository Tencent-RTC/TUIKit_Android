package io.trtc.tuikit.chat.uikit.components.userpicker.ui
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.userpicker.adapter.FlatItem
import io.trtc.tuikit.chat.uikit.components.userpicker.adapter.UserPickerAdapter
import io.trtc.tuikit.chat.uikit.components.userpicker.model.UserPickerData
import io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.AtomicIndexBar
import io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.pinyinhelper.Pinyin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UserPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val INDEX_BAR_END_PADDING_DP = 8f
        private const val INDEX_BAR_VERTICAL_PADDING_DP = 16f
        private const val HEADER_HORIZONTAL_PADDING_DP = 16f
        private const val HEADER_VERTICAL_PADDING_DP = 6f
        private const val HEADER_TEXT_SIZE_SP = 14f
        private const val CENTER_BUBBLE_SIZE_DP = 80f
        private const val CENTER_BUBBLE_TEXT_SIZE_SP = 28f
        private const val CENTER_BUBBLE_DISMISS_DELAY_MS = 150L
    }

    private val recyclerView: RecyclerView
    private val indexBar: AtomicIndexBar
    private val centerBubble: TextView

    private var viewScope: CoroutineScope? = null
    private var centerBubbleDismissJob: Job? = null

    private var groups: List<PickerGroup> = emptyList()
    private var flatItems: List<FlatItem> = emptyList()
    private var groupRanges: Map<String, Int> = emptyMap()
    private var dataSourceRaw: List<UserPickerData<Any?>> = emptyList()

    private val selectionState = UserPickerSelectionState()
    private var lockedKeys = emptySet<String>()
    private var maxCount: Int? = null
    private var hasReachedEnd = false
    private var showCheckbox: Boolean = true

    private var onSelectedChangedListener: ((List<UserPickerData<Any?>>) -> Unit)? = null
    private var onMaxCountExceedListener: ((List<UserPickerData<Any?>>) -> Unit)? = null
    private var onReachEndListener: (() -> Unit)? = null

    private var listAdapter: UserPickerAdapter

    init {
        listAdapter = UserPickerAdapter(
            context = context,
            showCheckbox = showCheckbox,
            selectedKeys = selectionState.keys,
            lockedKeys = lockedKeys,
            onItemClick = ::handleItemClick
        )

        layoutDirection = View.LAYOUT_DIRECTION_LOCALE

        recyclerView = RecyclerView(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutManager = LinearLayoutManager(context)
            adapter = listAdapter
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }
        addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        indexBar = AtomicIndexBar(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            visibility = GONE
        }
        val dm = resources.displayMetrics
        val indexBarLp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            marginEnd = dp2px(INDEX_BAR_END_PADDING_DP, dm).toInt()
            topMargin = dp2px(INDEX_BAR_VERTICAL_PADDING_DP, dm).toInt()
            bottomMargin = dp2px(INDEX_BAR_VERTICAL_PADDING_DP, dm).toInt()
        }
        addView(indexBar, indexBarLp)

        val bubbleSizePx = dp2px(CENTER_BUBBLE_SIZE_DP, dm).toInt()
        centerBubble = TextView(context).apply {
            visibility = GONE
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_LOCALE
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, CENTER_BUBBLE_TEXT_SIZE_SP)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val bubbleLp = LayoutParams(bubbleSizePx, bubbleSizePx).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = bubbleLp
        }
        addView(centerBubble)

        setupIndexBar()
        setupScrollListener()
        recyclerView.addItemDecoration(StickyHeaderDecoration())

        setBackgroundColor(getColors().bgColorOperate)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> setDataSource(dataSource: List<UserPickerData<T>>) {
        val previousKeys = dataSourceRaw.map { it.key }
        dataSourceRaw = UserPickerDataSourcePolicy.deduplicateByKey(
            dataSource as List<UserPickerData<Any?>>
        )
        if (UserPickerDataSourcePolicy.shouldResetReachEnd(previousKeys, dataSourceRaw.map { it.key })) {
            hasReachedEnd = false
        }
        groups = groupAndSort(dataSourceRaw)
        rebuildFlatItems()

        selectionState.onDataSourceChanged()
        rebuildAdapter()

        val letters = groups.map { it.letter }
        indexBar.setLetters(letters)
        indexBar.visibility = if (letters.isNotEmpty()) VISIBLE else GONE
        scheduleReachEndCheck()
    }

    fun setDefaultSelectedItems(keys: List<String>) {
        val changedKeys = selectionState.replaceWith(keys)
        if (changedKeys.isEmpty()) return
        val payload = listAdapter.getSelectionPayload()
        changedKeys.forEach { key ->
            UserPickerDataSourcePolicy.findUserItemPositions(flatItems, key).forEach { position ->
                listAdapter.notifyItemChanged(position, payload)
            }
        }
    }

    fun setLockedItems(keys: List<String>) {
        lockedKeys = keys.toSet()
        rebuildAdapter()
    }

    fun setMaxCount(maxCount: Int?) {
        this.maxCount = maxCount
        rebuildAdapter()
    }

    fun setShowCheckbox(show: Boolean) {
        if (this.showCheckbox == show) return
        this.showCheckbox = show
        rebuildAdapter()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getSelectedItems(): List<UserPickerData<T>> {
        val allItems = groups.flatMap { it.items }
        return selectionState.keys.mapNotNull { key ->
            allItems.firstOrNull { it.key == key }
        } as List<UserPickerData<T>>
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> setOnSelectedChangedListener(listener: (List<UserPickerData<T>>) -> Unit) {
        onSelectedChangedListener = listener as ((List<UserPickerData<Any?>>) -> Unit)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> setOnMaxCountExceedListener(listener: (List<UserPickerData<T>>) -> Unit) {
        onMaxCountExceedListener = listener as ((List<UserPickerData<Any?>>) -> Unit)
    }

    fun setOnReachEndListener(listener: () -> Unit) {
        onReachEndListener = listener
        scheduleReachEndCheck()
    }

    private fun handleItemClick(data: UserPickerData<Any?>) {
        when (val result = selectionState.toggle(data.key, maxCount, lockedKeys.contains(data.key))) {
            is UserPickerSelectionState.Result.Changed -> {
                notifySelectionChanged(result.changedKeys)
                pushSelectionChange()
            }
            UserPickerSelectionState.Result.MaxCountExceeded -> {
                val selectedItems = getSelectedItemsInternal()
                onMaxCountExceedListener?.invoke(selectedItems)
            }
            UserPickerSelectionState.Result.Ignored -> Unit
        }
    }

    private fun notifySelectionChanged(keys: Collection<String>) {
        val payload = listAdapter.getSelectionPayload()
        keys.distinct().forEach { key ->
            UserPickerDataSourcePolicy.findUserItemPositions(flatItems, key).forEach { position ->
                listAdapter.notifyItemChanged(position, payload)
            }
        }
    }

    private fun pushSelectionChange() {
        val selectedItems = getSelectedItemsInternal()
        onSelectedChangedListener?.invoke(selectedItems)
    }

    private fun getSelectedItemsInternal(): List<UserPickerData<Any?>> {
        val allItems = groups.flatMap { it.items }
        return selectionState.keys.mapNotNull { key ->
            allItems.firstOrNull { it.key == key }
        }
    }

    private fun setupIndexBar() {
        indexBar.onLetterSelected = { letter ->
            groupRanges[letter]?.let { position ->
                (recyclerView.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(position, 0)
            }
            showCenterBubble(letter)
        }

        indexBar.onDragStart = {
            centerBubbleDismissJob?.cancel()
            recyclerView.stopScroll()
        }

        indexBar.onDragEnd = {
            centerBubbleDismissJob?.cancel()
            centerBubbleDismissJob = viewScope?.launch {
                delay(CENTER_BUBBLE_DISMISS_DELAY_MS)
                centerBubble.visibility = GONE
            }
        }
    }

    private fun showCenterBubble(letter: String) {
        centerBubbleDismissJob?.cancel()
        val colors = getColors()
        val dm = resources.displayMetrics
        val bubbleSizePx = dp2px(CENTER_BUBBLE_SIZE_DP, dm).toInt()
        val cornerRadius = bubbleSizePx / 2f

        centerBubble.text = letter
        centerBubble.setTextColor(colors.textColorPrimary)

        val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setCornerRadius(cornerRadius)
            val bgColor = colors.bgColorBubbleReciprocal
            val alpha = (0.9f * 255).toInt()
            val colorWithAlpha = (bgColor and 0x00FFFFFF) or (alpha shl 24)
            setColor(colorWithAlpha)
        }
        centerBubble.background = bgDrawable
        centerBubble.visibility = VISIBLE
    }

    private fun setupScrollListener() {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                val firstVisible = lm.findFirstVisibleItemPosition()
                if (firstVisible == RecyclerView.NO_POSITION) return

                val item = flatItems.getOrNull(firstVisible) ?: return
                val letter = when (item) {
                    is FlatItem.SectionHeader -> item.letter
                    is FlatItem.UserItem -> item.letter
                }
                indexBar.setCurrentLetter(letter)

                checkReachEnd(lm)
            }
        })
    }

    private fun checkReachEnd(lm: LinearLayoutManager) {
        val total = lm.itemCount
        val lastVisible = lm.findLastVisibleItemPosition()
        if (UserPickerReachEndPolicy.shouldNotify(
                hasReachedEnd = hasReachedEnd,
                hasListener = onReachEndListener != null,
                totalItemCount = total,
                lastVisibleItemPosition = lastVisible
            )
        ) {
            hasReachedEnd = true
            onReachEndListener?.invoke()
        }
    }

    private fun scheduleReachEndCheck() {
        recyclerView.post {
            val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return@post
            checkReachEnd(lm)
        }
    }

    private data class PickerGroup(val letter: String, val items: List<UserPickerData<Any?>>)

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

    private fun groupAndSort(items: List<UserPickerData<Any?>>): List<PickerGroup> {
        val grouped = items.groupBy { item -> getFirstLetter(item.label) }
        val sortedGroups = grouped.map { (letter, groupItems) ->
            val sorted = groupItems.sortedBy { it.label.lowercase() }
            PickerGroup(letter, sorted)
        }
        return sortedGroups.sortedWith { g1, g2 ->
            when {
                g1.letter == "#" && g2.letter != "#" -> 1
                g1.letter != "#" && g2.letter == "#" -> -1
                else -> g1.letter.compareTo(g2.letter)
            }
        }
    }

    private fun rebuildFlatItems() {
        val items = mutableListOf<FlatItem>()
        val ranges = mutableMapOf<String, Int>()

        groups.forEach { group ->
            ranges[group.letter] = items.size
            items.add(FlatItem.SectionHeader(group.letter))
            group.items.forEach { item ->
                items.add(FlatItem.UserItem(group.letter, item))
            }
        }

        flatItems = items
        groupRanges = ranges
    }

    private fun rebuildAdapter() {
        listAdapter.showCheckbox = showCheckbox
        listAdapter.lockedKeys = lockedKeys
        listAdapter.items = flatItems
        listAdapter.notifyDataSetChanged()
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
            val isRtl = parent.layoutDirection == View.LAYOUT_DIRECTION_RTL
            textPaint.textAlign = if (isRtl) Paint.Align.RIGHT else Paint.Align.LEFT
            textPaint.textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                HEADER_TEXT_SIZE_SP,
                dm
            )
            textPaint.color = colors.textColorPrimary
            headerPaint.color = colors.bgColorDialog

            val currentItem = flatItems.getOrNull(firstVisiblePos) ?: return
            val currentLetter = when (currentItem) {
                is FlatItem.SectionHeader -> currentItem.letter
                is FlatItem.UserItem -> currentItem.letter
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                val colors = it.currentTheme.tokens.color
                setBackgroundColor(colors.bgColorOperate)
                updateCenterBubbleTheme(colors)
                recyclerView.adapter?.notifyDataSetChanged()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        centerBubbleDismissJob?.cancel()
        centerBubbleDismissJob = null
        viewScope?.cancel()
        viewScope = null
    }

    private fun updateCenterBubbleTheme(colors: ColorTokens) {
        if (centerBubble.visibility == VISIBLE) {
            centerBubble.setTextColor(colors.textColorPrimary)
        }
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }
}
