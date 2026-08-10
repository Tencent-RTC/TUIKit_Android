package io.trtc.tuikit.chat.uikit.components.emojipicker.ui
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.RecentEmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.EmojiGroup
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EmojiPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val themeStore = ThemeStore.shared(context)
    private var viewScope: CoroutineScope? = null

    private val tabRecyclerView: RecyclerView
    private val dividerView: View
    private val viewPager: ViewPager2
    private val bottomBar: LinearLayout
    private val deleteButton: ImageView
    private val sendButton: TextView

    private lateinit var tabAdapter: EmojiTabAdapter
    private lateinit var pagerAdapter: EmojiPagerAdapter
    private var emojiGroupList: List<EmojiGroup> = emptyList()
    private val gridAdaptersByGroupId = linkedMapOf<String, EmojiGridAdapter>()
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

    private var onEmojiClick: (EmojiGroup, Emoji) -> Unit = { _, _ -> }
    private var onDeleteClick: () -> Unit = {}
    private var onSendClick: () -> Unit = {}

    private var previewPopup: PopupWindow? = null

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }

        tabRecyclerView = RecyclerView(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            val dp10 = dpToPx(10f).toInt()
            setPadding(dp10, dp10, dp10, dp10)
            clipToPadding = false
        }
        rootLayout.addView(tabRecyclerView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        dividerView = View(context)
        rootLayout.addView(dividerView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(1f).toInt()
        ))

        viewPager = ViewPager2(context).apply {
            id = View.generateViewId()
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        rootLayout.addView(viewPager, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        addView(rootLayout)

        bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            gravity = Gravity.CENTER_VERTICAL
            val dp16 = dpToPx(16f).toInt()
            setPadding(dp16, dp16, dp16, dp16)
            visibility = View.GONE
        }

        deleteButton = ImageView(context).apply {
            val lp = LinearLayout.LayoutParams(dpToPx(40f).toInt(), dpToPx(30f).toInt())
            layoutParams = lp
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dpToPx(6f).toInt(), dpToPx(6f).toInt(), dpToPx(6f).toInt(), dpToPx(6f).toInt())
            setImageResource(R.drawable.emoji_picker_delete_icon)
            setOnClickListener { onDeleteClick() }
        }
        bottomBar.addView(deleteButton)

        val spacer = View(context)
        bottomBar.addView(spacer, LinearLayout.LayoutParams(dpToPx(10f).toInt(), 0))

        sendButton = TextView(context).apply {
            val lp = LinearLayout.LayoutParams(dpToPx(50f).toInt(), dpToPx(30f).toInt())
            layoutParams = lp
            gravity = Gravity.CENTER
            textSize = 14f
            setText(R.string.emoji_picker_send)
            setOnClickListener { onSendClick() }
        }
        bottomBar.addView(sendButton)

        val bottomBarLp = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
        }
        addView(bottomBar, bottomBarLp)

        setupAdapters()
    }

    fun setup(
        onEmojiClick: (EmojiGroup, Emoji) -> Unit = { _, _ -> },
        onDeleteClick: () -> Unit = {},
        onSendClick: () -> Unit = {}
    ) {
        this.onEmojiClick = onEmojiClick
        this.onDeleteClick = onDeleteClick
        this.onSendClick = onSendClick
    }

    private fun setupAdapters() {
        tabAdapter = EmojiTabAdapter(emptyList()) { index ->
            viewPager.setCurrentItem(index, true)
        }
        tabRecyclerView.adapter = tabAdapter

        pagerAdapter = EmojiPagerAdapter(0) { position -> createEmojiPage(position) }

        viewPager.adapter = pagerAdapter
        val callback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tabAdapter.selectedIndex = position
                updateBottomBar(position)
            }
        }
        pageChangeCallback = callback
        viewPager.registerOnPageChangeCallback(callback)

        applyEmojiGroups(EmojiManager.emojiGroupList)
    }

    private fun createEmojiPage(position: Int): RecyclerView {
        val group = emojiGroupList.getOrNull(position)
        val spanCount = if (group?.isLittleEmoji == true) 8 else 5
        val gridAdapter = if (group != null) {
            getOrCreateGridAdapter(group)
        } else {
            EmojiGridAdapter(spanCount = spanCount, onEmojiClick = {}, onEmojiLongClick = {})
        }

        return RecyclerView(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            val gridLayoutManager = GridLayoutManager(context, spanCount)
            gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return gridAdapter.getSpanSize(position)
                }
            }
            layoutManager = gridLayoutManager
            adapter = gridAdapter
            val dp8 = dpToPx(8f).toInt()
            setPadding(dp8, dp8, dp8, dp8)
            clipToPadding = false
        }.also {
            if (group != null) {
                buildGridItems(group, gridAdapter)
            }
        }
    }

    private fun getOrCreateGridAdapter(group: EmojiGroup): EmojiGridAdapter {
        val spanCount = if (group.isLittleEmoji) 8 else 5
        val existingAdapter = gridAdaptersByGroupId[group.id]
        if (existingAdapter != null && existingAdapter.spanCount == spanCount) {
            return existingAdapter
        }

        val groupId = group.id
        return EmojiGridAdapter(
            spanCount = spanCount,
            onEmojiClick = { emoji ->
                val currentGroup = emojiGroupList.firstOrNull { it.id == groupId } ?: group
                if (currentGroup.isLittleEmoji) {
                    RecentEmojiManager.updateRecentEmoji(currentGroup.id, emoji.key)
                }
                onEmojiClick(currentGroup, emoji)
            },
            onEmojiLongClick = { emoji ->
                showEmojiPreview(emoji)
            }
        ).also { adapter ->
            gridAdaptersByGroupId[group.id] = adapter
        }
    }

    private fun applyEmojiGroups(groups: List<EmojiGroup>) {
        val pickerGroups = EmojiManager.filterEmojiGroupsForPicker(groups)
        emojiGroupList = pickerGroups
        tabAdapter.submitGroups(pickerGroups)
        gridAdaptersByGroupId.keys.retainAll(pickerGroups.map { it.id }.toSet())
        pagerAdapter.submitPageCount(pickerGroups.size)
        pagerAdapter.notifyDataSetChanged()

        if (pickerGroups.isEmpty()) {
            bottomBar.visibility = View.GONE
            return
        }

        val selectedIndex = viewPager.currentItem.coerceIn(0, pickerGroups.lastIndex)
        if (selectedIndex != viewPager.currentItem) {
            viewPager.setCurrentItem(selectedIndex, false)
        }
        tabAdapter.selectedIndex = selectedIndex
        updateBottomBar(selectedIndex)
        refreshAllGrids()
    }

    private fun updateBottomBar(position: Int) {
        val group = emojiGroupList.getOrNull(position)
        bottomBar.visibility = if (group?.isLittleEmoji == true) View.VISIBLE else View.GONE
    }

    private fun buildGridItems(group: EmojiGroup, adapter: EmojiGridAdapter) {
        val items = mutableListOf<EmojiGridAdapter.GridItem>()

        if (group.isLittleEmoji) {
            val groupEmojisByKey = group.emojis.associateBy { it.key }
            val recentEmojis = RecentEmojiManager.getRecentEmojiList(group.id)
                .mapNotNull { groupEmojisByKey[it] }
                .take(RECENT_EMOJI_DISPLAY_COUNT)
            if (recentEmojis.isNotEmpty()) {
                items.add(EmojiGridAdapter.GridItem.Header(context.getString(R.string.emoji_picker_recent)))
                recentEmojis.forEach { emoji ->
                    items.add(EmojiGridAdapter.GridItem.EmojiItem(emoji))
                }
                items.add(EmojiGridAdapter.GridItem.Spacer(8))
            }
            items.add(EmojiGridAdapter.GridItem.Header(context.getString(R.string.emoji_picker_all)))
        }

        group.emojis.forEach { emoji ->
            items.add(EmojiGridAdapter.GridItem.EmojiItem(emoji))
        }

        if (group.isLittleEmoji) {
            items.add(EmojiGridAdapter.GridItem.Spacer(60))
        }

        adapter.submitItems(items)
    }

    private fun showEmojiPreview(emoji: Emoji) {
        dismissEmojiPreview()

        val previewView = LayoutInflater.from(context)
            .inflate(R.layout.emoji_picker_popup_preview, null)
        val previewImage = previewView.findViewById<ImageView>(R.id.emoji_picker_preview_image)
        val colors = themeStore.themeState.value.currentTheme.tokens.color

        Glide.with(context)
            .load(emoji.emojiUrl)
            .into(previewImage)

        val bgDrawable = GradientDrawable().apply {
            setColor(colors.bgColorInput)
            cornerRadius = dpToPx(12f)
        }
        previewView.background = bgDrawable

        val size = dpToPx(120f).toInt()
        previewPopup = PopupWindow(previewView, size, size, true).apply {
            isOutsideTouchable = true
            setOnDismissListener { previewPopup = null }
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        val location = IntArray(2)
        getLocationInWindow(location)
        val x = location[0] + (width - size) / 2
        val y = location[1]
        previewPopup?.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
    }

    private fun dismissEmojiPreview() {
        previewPopup?.dismiss()
        previewPopup = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        viewScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        viewScope = scope

        scope.launch {
            themeStore.themeState.collectLatest {
                applyThemeColors(it.currentTheme.tokens.color)
            }
        }

        scope.launch {
            RecentEmojiManager.recentEmojiVersion.collectLatest {
                refreshAllGrids()
            }
        }

        scope.launch {
            EmojiManager.emojiGroupState.collectLatest { groups ->
                applyEmojiGroups(groups)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
        dismissEmojiPreview()
    }

    private fun applyThemeColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorOperate)
        dividerView.setBackgroundColor(colors.bgColorBubbleReciprocal)

        val deleteBg = GradientDrawable().apply {
            setColor(colors.buttonColorSecondaryDefault)
            cornerRadius = dpToPx(15f)
        }
        deleteButton.background = deleteBg
        deleteButton.setColorFilter(colors.textColorPrimary)

        val sendBg = GradientDrawable().apply {
            setColor(colors.buttonColorPrimaryDefault)
            cornerRadius = dpToPx(15f)
        }
        sendButton.background = sendBg
        sendButton.setTextColor(colors.textColorButton)

        tabAdapter.notifyDataSetChanged()
        gridAdaptersByGroupId.values.forEach { it.notifyDataSetChanged() }
    }

    private fun refreshAllGrids() {
        emojiGroupList.forEach { group ->
            val adapter = gridAdaptersByGroupId[group.id] ?: return@forEach
            buildGridItems(group, adapter)
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    companion object {
        private const val RECENT_EMOJI_DISPLAY_COUNT = 8
    }
}
