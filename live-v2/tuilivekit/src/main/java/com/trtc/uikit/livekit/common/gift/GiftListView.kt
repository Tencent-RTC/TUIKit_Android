package com.trtc.uikit.livekit.common.gift

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.basic.LiveKitLogger
import com.trtc.uikit.livekit.common.gift.panel.category.GiftCategoryPagerAdapter
import com.trtc.uikit.livekit.common.gift.player.GiftLanguage
import com.trtc.uikit.livekit.common.gift.panel.gift.viewholder.ComboButtonView
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.gift.GiftCategory
import io.trtc.tuikit.atomicxcore.api.gift.GiftStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class GiftListView : LinearLayout, LifecycleOwner {
    private val logger: LiveKitLogger = LiveKitLogger.Companion.getComponentLogger("GiftListView")
    private var liveInfo: LiveInfo = LiveInfo()
    private var giftStore: GiftStore? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val collectJobs = mutableListOf<Job>()
    private var isObserverAdded = false

    private var giftCategories: List<GiftCategory> = emptyList()
    private var currentSelectedCategoryIndex: Int = 0

    private var viewPager: ViewPager2? = null
    private var tabItemContainer: LinearLayout? = null
    private val tabViews = mutableListOf<TextView>()
    private val tabIndicators = mutableListOf<View>()
    private val topContainer: FrameLayout by lazy { findViewById(R.id.gift_top_container) }
    private val bottomContainer: FrameLayout by lazy { findViewById(R.id.gift_bottom_container) }

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        orientation = VERTICAL
        inflate(context, R.layout.livekit_gift_layout_list_panel_containers, this)
    }
    
    private var onAdvancedComboRequested: ((Gift) -> Unit)? = null

    private fun selectCategory(index: Int) {
        if (index < 0 || index >= giftCategories.size) return
        currentSelectedCategoryIndex = index
        viewPager?.setCurrentItem(index, true)
    }

    private fun updateTabSelection(index: Int) {
        tabViews.forEachIndexed { i, tabView ->
            if (i == index) {
                tabView.setTextColor(ContextCompat.getColor(context, R.color.common_color_white_e5))
                tabView.isSelected = true
            } else {
                tabView.setTextColor(ContextCompat.getColor(context, R.color.common_text_color_secondary))
                tabView.isSelected = false
            }
        }
        tabIndicators.forEachIndexed { i, indicator ->
            indicator.visibility = if (i == index) VISIBLE else INVISIBLE
        }
        currentSelectedCategoryIndex = index
    }

    private fun createTabView(title: String, index: Int): LinearLayout {
        val container = LayoutInflater.from(context)
            .inflate(R.layout.livekit_gift_layout_tab_item_container, null) as LinearLayout

        val tabView = container.findViewById<TextView>(R.id.tv_tab_title).apply {
            text = title
        }
        val indicator = container.findViewById<View>(R.id.v_tab_indicator)

        container.setOnClickListener {
            selectCategory(index)
        }

        tabViews.add(tabView)
        tabIndicators.add(indicator)

        return container
    }

    fun init(liveInfo: LiveInfo) {
        if (liveInfo.liveID.isEmpty()) {
            return
        }
        this.liveInfo = liveInfo
        initStore()
        setupLifecycleIfNeeded()
    }

    private fun initStore() {
        giftStore = GiftStore.Companion.create(liveInfo.liveID)
        giftStore?.setLanguage(GiftLanguage.currentCode())
        giftStore?.refreshUsableGifts(null)
    }

    private fun addObserver() {
        launch {
            giftStore?.giftState?.usableGifts?.collect {
                onGiftListChange(it)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setupLifecycleIfNeeded()
    }

    override fun onDetachedFromWindow() {
        collectJobs.forEach { it.cancel() }
        collectJobs.clear()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        isObserverAdded = false
        super.onDetachedFromWindow()
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private fun setGiftCategoryList(categoryList: List<GiftCategory>) {
        if (categoryList.isEmpty()) {
            logger.error("setGiftCategoryList categoryList is empty")
            return
        }

        if (viewPager != null && isSameCategoryStructure(giftCategories, categoryList)) {
            giftCategories = categoryList
            return
        }

        giftCategories = categoryList

        if (currentSelectedCategoryIndex >= categoryList.size) {
            currentSelectedCategoryIndex = 0
        }

        detachChildrenSafely(topContainer)
        detachChildrenSafely(bottomContainer)
        tabViews.clear()
        tabIndicators.clear()

        buildTabBar(categoryList)
        buildViewPager(categoryList)

        updateTabSelection(currentSelectedCategoryIndex)
        viewPager?.setCurrentItem(currentSelectedCategoryIndex, false)
    }

    private fun buildTabBar(categoryList: List<GiftCategory>) {
        val paddingPx = (16f * resources.displayMetrics.density).toInt()
        val scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(paddingPx, 0, paddingPx, 0)
        }
        val itemContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        categoryList.forEachIndexed { index, category ->
            itemContainer.addView(createTabView(category.name, index))
        }
        scrollView.addView(itemContainer)
        tabItemContainer = itemContainer

        topContainer.addView(
            scrollView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun buildViewPager(categoryList: List<GiftCategory>) {
        val pager = ViewPager2(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            isUserInputEnabled = false
        }
        val pagerAdapter = GiftCategoryPagerAdapter(
            context = context,
            categories = categoryList,
            columns = COLUMNS,
            onGiftSelectedListener = null,
            onGiftClick = { gift, count -> sendGift(gift, count) },
            onAdvancedComboRequested = { gift -> onAdvancedComboRequested?.invoke(gift) }
        )
        pager.adapter = pagerAdapter
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                pagerAdapter.clearAllPageSelections()
                updateTabSelection(position)
            }
        })

        bottomContainer.addView(pager)
        viewPager = pager
    }

    private fun isSameCategoryStructure(
        oldList: List<GiftCategory>,
        newList: List<GiftCategory>
    ): Boolean {
        if (oldList.size != newList.size) return false
        for (i in oldList.indices) {
            if (oldList[i].name != newList[i].name) return false
            if (oldList[i].giftList.size != newList[i].giftList.size) return false
        }
        return true
    }

    private fun detachChildrenSafely(parent: ViewGroup) {
        for (i in parent.childCount - 1 downTo 0) {
            stopAnimationsRecursively(parent.getChildAt(i))
        }
        parent.removeAllViews()
    }

    private fun stopAnimationsRecursively(view: View?) {
        view ?: return
        if (view is RecyclerView) {
            view.stopScroll()
            view.itemAnimator?.endAnimations()
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                stopAnimationsRecursively(view.getChildAt(i))
            }
        }
    }

    private fun setupLifecycleIfNeeded() {
        if (liveInfo.liveID.isEmpty()) {
            return
        }
        if (!isObserverAdded) {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            addObserver()
            isObserverAdded = true
        }
    }

    fun sendGift(gift: Gift, giftCount: Int) {
        giftStore?.sendGift(gift.giftID, giftCount, liveInfo.liveOwner.userID, object : CompletionHandler {
            override fun onFailure(code: Int, desc: String) {
            }

            override fun onSuccess() {
            }
        })
    }

    private fun onGiftListChange(list: List<GiftCategory>) {
        if (!TextUtils.isEmpty(liveInfo.liveID)) {
            setGiftCategoryList(list)
        }
    }
    
    private fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        return lifecycleScope.launch(block = block).also { job ->
            collectJobs.add(job)
        }
    }

    companion object {
        private const val COLUMNS = 4

        fun show(context: Context, liveInfo: LiveInfo) {
            val sheet = object : BottomSheetView(context) {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    LayoutInflater.from(context)
                        .inflate(R.layout.livekit_gift_layout_send_dialog_panel, contentView, true)
                    contentView.findViewById<GiftListView>(R.id.gift_list_view)?.also { panel ->
                        panel.init(liveInfo)
                        panel.onAdvancedComboRequested = { gift ->
                            val store = panel.giftStore
                            dismiss()
                            if (store != null) {
                                showFloatingCombo(context, gift, store, liveInfo)
                            }
                        }
                    }
                    setCanceledOnTouchOutside(true)
                }
            }
            sheet.show()
        }

        private fun showFloatingCombo(
            context: Context,
            gift: Gift,
            store: GiftStore,
            liveInfo: LiveInfo
        ) {
            val activity = context.findActivity() ?: return
            val root = activity.window?.decorView as? ViewGroup ?: return

            val density = context.resources.displayMetrics.density
            val comboWidthPx = (72f * density).toInt()
            val comboHeightPx = (90f * density).toInt()
            val marginEndPx = (16f * density).toInt()
            val marginBottomPx = (78f * density).toInt()

            val host = FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false
                layoutParams = FrameLayout.LayoutParams(
                    comboWidthPx, comboHeightPx
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    rightMargin = marginEndPx
                    bottomMargin = marginBottomPx
                }
            }

            val combo = ComboButtonView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                )
            }
            host.addView(combo)
            combo.onSend = {
                store.sendGift(gift.giftID, 1, liveInfo.liveOwner.userID, object : CompletionHandler {
                    override fun onFailure(code: Int, desc: String) {}
                    override fun onSuccess() {}
                })
                combo.bumpCombo()
            }
            combo.onExpire = {
                (host.parent as? ViewGroup)?.removeView(host)
            }

            root.addView(host)
            combo.startCombo()
        }
        
        private fun Context.findActivity(): Activity? {
            var ctx: Context? = this
            while (ctx is ContextWrapper) {
                if (ctx is Activity) return ctx
                ctx = ctx.baseContext
            }
            return null
        }
    }
}
