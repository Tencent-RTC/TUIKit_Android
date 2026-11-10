package com.trtc.uikit.livekit.common.gift.send

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitResources
import com.trtc.uikit.livekit.common.log.LiveKitLog
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.gift.GiftCategory
import io.trtc.tuikit.atomicxcore.api.gift.GiftStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class GiftSendPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    var onSendGift: ((Gift, Int) -> Unit)? = null
    var onAdvancedComboRequested: ((Gift) -> Unit)? = null
    private var categories: List<GiftCategory> = emptyList()
    private var selectedCategory = 0
    private val tabAdapter = GiftCategoryTabAdapter(::selectCategory)
    private val pageAdapter = GiftCategoryPagerAdapter(
        onSend = { gift, count -> onSendGift?.invoke(gift, count) },
        onAdvancedComboRequested = { onAdvancedComboRequested?.invoke(it) },
    )
    private val viewPager = ViewPager2(context)

    init {
        orientation = VERTICAL
        inflate(context, R.layout.livekit_gift_layout_list_panel_containers, this)
        val horizontalPadding = (16 * resources.displayMetrics.density).toInt()
        val tabs = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = tabAdapter
            itemAnimator = null
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
        }
        findViewById<FrameLayout>(R.id.gift_top_container).addView(
            tabs, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        viewPager.adapter = pageAdapter
        viewPager.isUserInputEnabled = false
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pageAdapter.clearSelections()
                selectedCategory = position
                tabAdapter.select(position)
            }
        })
        findViewById<FrameLayout>(R.id.gift_bottom_container).addView(
            viewPager, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
    }

    fun bindCategories(categories: List<GiftCategory>) {
        if (this.categories == categories) return
        this.categories = categories
        selectedCategory = selectedCategory.coerceIn(0, (categories.size - 1).coerceAtLeast(0))
        pageAdapter.update(categories)
        tabAdapter.update(categories, selectedCategory)
        viewPager.setCurrentItem(selectedCategory, false)
    }

    private fun selectCategory(index: Int) {
        if (index in categories.indices) viewPager.setCurrentItem(index, true)
    }

    fun stopCombo() = pageAdapter.clearSelections()

    companion object {
        private var current: Presentation? = null

        @MainThread
        fun show(
            context: Context,
            liveID: String,
            receiverID: String = "",
            onPanelTopChanged: ((Int?) -> Unit)? = null,
        ) {
            dismiss()
            val presentation = Presentation(context, liveID, receiverID, onPanelTopChanged)
            current = presentation
            presentation.onFinished = { if (current === presentation) current = null }
            presentation.show()
        }

        @MainThread
        fun dismiss() {
            current?.dismiss()
            current = null
        }

        @MainThread
        fun stopCombo() {
            current?.stopCombo()
        }
    }

    private class Presentation(
        private val context: Context,
        private val liveID: String,
        private val receiverID: String,
        private val onPanelTopChanged: ((Int?) -> Unit)? = null,
    ) {
        var onFinished: (() -> Unit)? = null
        private var panel: GiftSendPanelView? = null
        private var comboHost: FrameLayout? = null
        private var comboButton: GiftSendComboButton? = null
        private var closed = false
        private var sdkStore: GiftStore? = null
        private var scope: CoroutineScope? = null
        private var activationID = 0L
        private var categories: List<GiftCategory> = emptyList()
        private var panelLayoutGeneration = 0
        private var panelLayoutView: View? = null
        private var panelLayoutObserver: ViewTreeObserver? = null
        private var panelLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
        private var reportedPanelTop: Int? = null
        private val decorLocation = IntArray(2)
        private val sheet: BottomSheetView = object : BottomSheetView(context) {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                LayoutInflater.from(context).inflate(R.layout.livekit_gift_layout_send_dialog_panel, contentView, true)
                panel = contentView.findViewById<GiftSendPanelView>(R.id.gift_list_view).also { view ->
                    view.onSendGift = { gift, count -> sendGift(gift.giftID, count) }
                    view.onAdvancedComboRequested = { gift ->
                        showFloatingCombo(gift)
                        dismissSheet()
                    }
                    view.bindCategories(categories)
                }
                setCanceledOnTouchOutside(true)
            }

            override fun onStart() {
                super.onStart()
                observePanelTop(contentView, window?.decorView)
            }

            override fun sheetDidDismiss() {
                stopObservingPanelTop()
                panel = null
                if (comboHost == null) finish()
            }
        }

        fun show() {
            if (closed) return
            activate()
            sheet.show()
        }

        fun dismiss() {
            finish()
        }

        private fun dismissSheet() {
            sheet.dismiss()
        }

        fun stopCombo() {
            panel?.stopCombo()
            removeFloatingCombo()
            if (!sheet.isShowing) finish()
        }

        private fun activate() {
            val currentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            scope = currentScope
            val currentActivation = activationID
            val store = GiftStore.create(liveID)
            sdkStore = store
            store.setLanguage(LiveKitResources.giftLanguageCode)
            currentScope.launch {
                store.giftState.usableGifts.collect { snapshot ->
                    if (activationID != currentActivation) return@collect
                    categories = snapshot.map { category ->
                        category.copy(
                            extensionInfo = category.extensionInfo.toMap(),
                            giftList = category.giftList.map { gift ->
                                gift.copy(extensionInfo = gift.extensionInfo.toMap())
                            },
                        )
                    }
                    panel?.bindCategories(categories)
                }
            }
            store.refreshUsableGifts(completion(currentScope, currentActivation) { })
        }

        private fun deactivate() {
            activationID++
            scope?.cancel()
            scope = null
            sdkStore = null
            categories = emptyList()
        }

        private fun sendGift(giftID: String, count: Int = 1) {
            val store = sdkStore ?: return
            val currentScope = scope ?: return
            store.sendGift(giftID, count.coerceAtLeast(1), receiverID, completion(currentScope, activationID) { })
        }

        private fun completion(
            currentScope: CoroutineScope,
            currentActivation: Long,
            onComplete: (Boolean) -> Unit,
        ): CompletionHandler = object : CompletionHandler {
            private var completed = false

            override fun onSuccess() = finish(true)

            override fun onFailure(code: Int, desc: String) = finish(false, code, desc)

            private fun finish(success: Boolean, code: Int = 0, desc: String = "") {
                currentScope.launch {
                    if (activationID != currentActivation || completed) return@launch
                    completed = true
                    if (!success) {
                        LiveKitLog.getComponentLogger("GiftSendPanelView")
                            .warn("Gift operation failed: code=$code, desc=$desc")
                    }
                    onComplete(success)
                }
            }
        }

        private fun finish() {
            if (closed) return
            closed = true
            stopObservingPanelTop()
            deactivate()
            removeFloatingCombo()
            sheet.dismiss()
            onFinished?.invoke()
        }

        private fun observePanelTop(content: View, decor: View?) {
            stopObservingPanelTop()
            if (closed || decor == null) return
            val generation = panelLayoutGeneration
            val listener = ViewTreeObserver.OnGlobalLayoutListener {
                if (closed || generation != panelLayoutGeneration || !sheet.isShowing) return@OnGlobalLayoutListener
                reportPanelTop(content, decor)
            }
            panelLayoutView = decor
            panelLayoutObserver = decor.viewTreeObserver
            panelLayoutListener = listener
            panelLayoutObserver?.addOnGlobalLayoutListener(listener)
            if (sheet.isShowing) reportPanelTop(content, decor)
        }

        private fun stopObservingPanelTop() {
            panelLayoutGeneration++
            val observer = panelLayoutObserver?.takeIf { it.isAlive } ?: panelLayoutView?.viewTreeObserver
            val listener = panelLayoutListener
            panelLayoutView = null
            panelLayoutObserver = null
            panelLayoutListener = null
            if (observer?.isAlive == true && listener != null) observer.removeOnGlobalLayoutListener(listener)
            publishPanelTop(null)
        }

        private fun reportPanelTop(content: View, decor: View) {
            if (!content.isAttachedToWindow || !decor.isAttachedToWindow || content.height <= 0) return
            var top = 0
            var current = content
            // Layout positions exclude the popover's entry/exit translation, keeping the reserved region stable.
            while (current !== decor) {
                val parent = current.parent as? View ?: return
                top += current.top - parent.scrollY
                current = parent
            }
            decor.getLocationOnScreen(decorLocation)
            publishPanelTop(decorLocation[1] + top)
        }

        private fun publishPanelTop(top: Int?) {
            if (reportedPanelTop == top) return
            reportedPanelTop = top
            onPanelTopChanged?.invoke(top)
        }

        private fun removeFloatingCombo() {
            comboButton?.stopCombo()
            comboButton = null
            comboHost?.let { (it.parent as? ViewGroup)?.removeView(it) }
            comboHost = null
        }

        private fun showFloatingCombo(gift: Gift) {
            val activity = context.findActivity() ?: return
            val root = activity.window?.decorView as? ViewGroup ?: return
            removeFloatingCombo()

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

            val combo = GiftSendComboButton(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                )
            }
            host.addView(combo)
            combo.onSend = {
                sendGift(gift.giftID)
                combo.bumpCombo()
            }
            combo.onExpire = ::finish
            comboHost = host
            comboButton = combo
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


private class GiftCategoryPagerAdapter(
    private val onSend: (Gift, Int) -> Unit,
    private val onAdvancedComboRequested: (Gift) -> Unit,
) : RecyclerView.Adapter<GiftCategoryPageHolder>() {
    private var categories: List<GiftCategory> = emptyList()
    private val pages = mutableSetOf<GiftGridPageView>()

    fun update(categories: List<GiftCategory>) {
        clearSelections()
        this.categories = categories
        notifyDataSetChanged()
    }

    fun clearSelections() = pages.forEach { it.clearSelection() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GiftCategoryPageHolder {
        val page = GiftGridPageView(parent.context).apply {
            layoutParams =
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            onSend = this@GiftCategoryPagerAdapter.onSend
            onAdvancedComboRequested = this@GiftCategoryPagerAdapter.onAdvancedComboRequested
        }
        return GiftCategoryPageHolder(page)
    }

    override fun onBindViewHolder(holder: GiftCategoryPageHolder, position: Int) {
        holder.page.clearSelection()
        holder.page.update(categories[position].giftList)
        pages.add(holder.page)
    }

    override fun onViewRecycled(holder: GiftCategoryPageHolder) {
        holder.page.clearSelection()
        pages.remove(holder.page)
        super.onViewRecycled(holder)
    }

    override fun onViewDetachedFromWindow(holder: GiftCategoryPageHolder) {
        holder.page.clearSelection()
        super.onViewDetachedFromWindow(holder)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        clearSelections()
        pages.clear()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun getItemCount() = categories.size
}

private class GiftCategoryPageHolder(val page: GiftGridPageView) : RecyclerView.ViewHolder(page)

private class GiftCategoryTabAdapter(private val onSelect: (Int) -> Unit) :
    RecyclerView.Adapter<GiftCategoryTabHolder>() {
    private var categories: List<GiftCategory> = emptyList()
    private var selectedIndex = 0

    fun update(categories: List<GiftCategory>, selectedIndex: Int) {
        this.categories = categories
        this.selectedIndex = selectedIndex
        notifyDataSetChanged()
    }

    fun select(index: Int) {
        val previous = selectedIndex
        if (previous == index) return
        selectedIndex = index
        if (previous in categories.indices) notifyItemChanged(previous)
        if (index in categories.indices) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GiftCategoryTabHolder = GiftCategoryTabHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.livekit_gift_layout_tab_item_container, parent, false)
    )

    override fun onBindViewHolder(holder: GiftCategoryTabHolder, position: Int) {
        holder.bind(categories[position].name, position == selectedIndex)
        holder.itemView.setOnClickListener {
            val index = holder.bindingAdapterPosition
            if (index != RecyclerView.NO_POSITION) onSelect(index)
        }
    }

    override fun getItemCount() = categories.size
}

private class GiftCategoryTabHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val title = itemView.findViewById<TextView>(R.id.tv_tab_title)
    private val indicator = itemView.findViewById<View>(R.id.v_tab_indicator)

    fun bind(name: String, selected: Boolean) {
        title.text = name
        title.isSelected = selected
        title.setTextColor(
            ContextCompat.getColor(
                itemView.context, if (selected) R.color.common_color_white_e5 else R.color.common_text_color_secondary
            )
        )
        indicator.visibility = if (selected) View.VISIBLE else View.INVISIBLE
    }
}
