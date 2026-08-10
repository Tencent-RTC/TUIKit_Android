package io.trtc.tuikit.chat.uikit.components.conversationlist.ui
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.conversationlist.adapter.ConversationItemLayout
import io.trtc.tuikit.chat.uikit.components.conversationlist.adapter.ConversationListAdapter
import io.trtc.tuikit.chat.uikit.components.conversationlist.config.ChatConversationActionConfig
import io.trtc.tuikit.chat.uikit.components.conversationlist.config.ConversationActionConfigProtocol
import io.trtc.tuikit.chat.uikit.components.common.findViewModelStoreOwner
import io.trtc.tuikit.chat.uikit.components.conversationlist.model.ConversationCustomActionContext
import io.trtc.tuikit.chat.uikit.components.conversationlist.model.resolveConversationActionTitle
import io.trtc.tuikit.chat.uikit.components.conversationlist.viewmodel.ConversationListViewModel
import io.trtc.tuikit.chat.uikit.components.conversationlist.viewmodel.ConversationListViewModelFactory
import io.trtc.tuikit.chat.uikit.components.conversationlist.viewmodel.applyConversationActionCustomizer
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ConversationListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val recyclerView: RecyclerView
    private val emptyView: LinearLayout
    private val emptyTextView: TextView
    private lateinit var adapter: ConversationListAdapter
    private lateinit var viewModel: ConversationListViewModel
    private var viewScope: CoroutineScope? = null
    private var dividerDecoration: ConversationDividerDecoration? = null
    private var scrollListenerAttached = false
    private var pendingAnchorToTop = false
    private var anchorObserver: RecyclerView.AdapterDataObserver? = null
    private val viewModelKey = "${ConversationListViewModel::class.java.name}:${System.identityHashCode(this)}"

    private var onConversationClick: (ConversationInfo) -> Unit = {}

    private var config: ConversationActionConfigProtocol = ChatConversationActionConfig()
    private val themeStore = ThemeStore.shared(context)

    private var currentPopup: ConversationPopupMenu? = null

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        LayoutInflater.from(context).inflate(R.layout.conversation_list_view, this, true)
        recyclerView = findViewById(R.id.conversation_list_recycler_view)
        emptyView = findViewById(R.id.conversation_list_empty_view)
        emptyTextView = findViewById(R.id.conversation_list_empty_text)
        recyclerView.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        recyclerView.layoutManager = LinearLayoutManager(context)
        (recyclerView.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)
            ?.supportsChangeAnimations = false
    }

    @JvmOverloads
    fun setup(
        config: ConversationActionConfigProtocol = ChatConversationActionConfig(),
        onConversationClick: (ConversationInfo) -> Unit = {}
    ) {
        this.config = config
        this.onConversationClick = onConversationClick

        cleanupBinding()

        val owner = context.findViewModelStoreOwner()
            ?: error("ConversationListView requires a ViewModelStoreOwner host context.")
        viewModel = ViewModelProvider(
            owner,
            ConversationListViewModelFactory(ConversationListStore.create(), config)
        ).get(viewModelKey, ConversationListViewModel::class.java)
        viewModel.updateConfig(config)

        adapter = ConversationListAdapter(
            context = context,
            onItemClick = { conversation ->
                this@ConversationListView.onConversationClick(conversation)
            },
            onItemLongClick = { conversation, anchorView ->
                showPopupMenu(conversation, anchorView)
            }
        )
        recyclerView.adapter = adapter
        anchorObserver = createAnchorObserver().also { adapter.registerAdapterDataObserver(it) }
        dividerDecoration = ConversationDividerDecoration(context, themeStore).also {
            recyclerView.addItemDecoration(it)
        }
        if (!scrollListenerAttached) {
            recyclerView.addOnScrollListener(loadMoreScrollListener)
            scrollListenerAttached = true
        }

        if (isAttachedToWindow) {
            bindViewModel()
        }
    }

    fun release() {
        cleanupBinding()
        recyclerView.adapter = null
    }

    private val loadMoreScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (!::viewModel.isInitialized) return
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
            val totalItemCount = layoutManager.itemCount
            val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
            if (totalItemCount > 0 && lastVisibleItem >= totalItemCount - 3) {
                viewModel.loadMoreConversation()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!::viewModel.isInitialized) return
        bindViewModel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        currentPopup?.dismissImmediately()
        viewScope?.cancel()
        viewScope = null
    }

    private fun bindViewModel() {
        if (viewScope != null || !::viewModel.isInitialized || !::adapter.isInitialized) {
            return
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        viewScope = scope

        scope.launch {
            combine(
                viewModel.conversationList,
                viewModel.initialLoadFinished
            ) { list, initialLoadFinished -> list to initialLoadFinished }
                .collectLatest { (list, initialLoadFinished) ->
                    currentPopup?.dismissImmediately()
                    pendingAnchorToTop = isFirstItemFullyVisible()
                    adapter.submitList(list)
                    emptyView.visibility = if (initialLoadFinished && list.isEmpty()) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                }
        }

        scope.launch {
            themeStore.themeState.collectLatest {
                val colors = it.currentTheme.tokens.color
                currentPopup?.dismissImmediately()
                setBackgroundColor(colors.bgColorTopBar)
                emptyTextView.setTextColor(colors.textColorTertiary)
                adapter.notifyThemeChanged()
                recyclerView.invalidateItemDecorations()
            }
        }
    }

    private fun cleanupBinding() {
        currentPopup?.dismissImmediately()
        viewScope?.cancel()
        viewScope = null
        if (scrollListenerAttached) {
            recyclerView.removeOnScrollListener(loadMoreScrollListener)
            scrollListenerAttached = false
        }
        anchorObserver?.let { observer ->
            if (::adapter.isInitialized) {
                adapter.unregisterAdapterDataObserver(observer)
            }
        }
        anchorObserver = null
        pendingAnchorToTop = false
        dividerDecoration?.let { recyclerView.removeItemDecoration(it) }
        dividerDecoration = null
    }

    private fun isFirstItemFullyVisible(): Boolean {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return false
        if (lm.itemCount == 0) return false
        return lm.findFirstCompletelyVisibleItemPosition() == 0
    }

    private fun anchorToTopIfNeeded() {
        if (!pendingAnchorToTop) return
        pendingAnchorToTop = false
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        lm.scrollToPositionWithOffset(0, 0)
    }

    private fun createAnchorObserver(): RecyclerView.AdapterDataObserver {
        return object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                anchorToTopIfNeeded()
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                anchorToTopIfNeeded()
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                anchorToTopIfNeeded()
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                anchorToTopIfNeeded()
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                anchorToTopIfNeeded()
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                anchorToTopIfNeeded()
            }
        }
    }

    private fun showPopupMenu(conversation: ConversationInfo, anchorView: View) {
        val colors = themeStore.themeState.value.currentTheme.tokens.color

        val defaults = viewModel.getDefaultActions(conversation)
        val allActions = applyConversationActionCustomizer(
            actionContext = ConversationCustomActionContext(
                androidContext = context,
                conversation = conversation,
            ),
            defaults = defaults,
            customizer = config.actionCustomizer,
        ).map { action ->
            val title = resolveConversationActionTitle(
                title = action.title,
                titleResID = action.titleResID,
                getString = { resID -> context.getString(resID) },
            )
            Triple(
                title,
                action.dangerous,
                Runnable { action.action(conversation) }
            )
        }

        if (allActions.isEmpty()) {
            (anchorView as? ConversationItemLayout)?.setHighlighted(false, colors)
            return
        }

        val popupView = buildPopupMenuView(allActions, colors)
        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = popupView.measuredWidth
        val popupHeight = popupView.measuredHeight

        val popupHostView = this
        val hostLocation = IntArray(2)
        popupHostView.getLocationInWindow(hostLocation)
        val anchorLocation = IntArray(2)
        anchorView.getLocationInWindow(anchorLocation)
        val anchorX = anchorLocation[0] - hostLocation[0]
        val anchorY = anchorLocation[1] - hostLocation[1]
        val anchorWidth = anchorView.width
        val anchorHeight = anchorView.height
        val hostWidth = popupHostView.width
        val hostHeight = popupHostView.height

        val margin = dp2px(context, 4f)
        val horizontalInset = dp2px(context, 16f)
        val aboveOverlap = dp2px(context, 8f)
        val belowOverlap = (anchorHeight * 3 / 5).coerceAtLeast(dp2px(context, 24f))
        val isRtl = anchorView.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val preferredX = if (isRtl) {
            anchorX + horizontalInset
        } else {
            anchorX + anchorWidth - popupWidth - horizontalInset
        }
        val maxX = (hostWidth - popupWidth - margin).coerceAtLeast(margin)
        val xOffset = preferredX.coerceIn(margin, maxX)
        val belowY = anchorY + anchorHeight - belowOverlap
        val aboveY = anchorY - popupHeight + aboveOverlap
        val showBelow = belowY + popupHeight <= hostHeight - margin || aboveY < margin
        val yOffset = if (showBelow) {
            belowY.coerceAtMost(hostHeight - popupHeight - margin)
        } else {
            aboveY.coerceAtLeast(margin)
        }

        currentPopup?.dismissImmediately()
        currentPopup = ConversationPopupMenu(
            anchorView = anchorView,
            popupHostView = popupHostView,
            popupWindowX = hostLocation[0],
            popupWindowY = hostLocation[1],
            popupWindowWidth = hostWidth,
            popupWindowHeight = hostHeight,
            menuView = popupView,
            popupX = xOffset,
            popupY = yOffset,
            showBelow = showBelow,
            colors = colors
        ).also {
            it.show()
        }
    }

    private fun buildPopupMenuView(
        actions: List<Triple<String, Boolean, Runnable>>,
        colors: ColorTokens
    ): View {
        val horizontalPadding = dp2px(context, 16f)
        val verticalPadding = dp2px(context, 12f)

        val menuItems = mutableListOf<TextView>()
        var maxTextWidth = 0

        actions.forEach { (title, isDangerous, _) ->
            val menuItem = TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(if (isDangerous) colors.textColorError else colors.textColorPrimary)
            }
            menuItem.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            maxTextWidth = maxOf(maxTextWidth, menuItem.measuredWidth)
            menuItems.add(menuItem)
        }

        val menuWidth = maxTextWidth + horizontalPadding * 2

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            background = GradientDrawable().apply {
                setColor(colors.bgColorOperate)
                cornerRadius = dp2px(context, 8f).toFloat()
            }
            elevation = dp2px(context, 8f).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { }
        }

        menuItems.forEachIndexed { index, menuItem ->
            val (_, _, action) = actions[index]
            menuItem.layoutParams = LinearLayout.LayoutParams(
                menuWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            menuItem.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            menuItem.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            menuItem.textDirection = View.TEXT_DIRECTION_LOCALE
            menuItem.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            menuItem.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            menuItem.background = createMenuItemRipple(
                colors = colors,
                isFirst = index == 0,
                isLast = index == actions.lastIndex
            )
            menuItem.isClickable = true
            menuItem.setOnClickListener {
                currentPopup?.dismiss {
                    action.run()
                } ?: action.run()
            }
            container.addView(menuItem)

            if (index < actions.size - 1) {
                val divider = View(context).apply {
                    setBackgroundColor(colors.strokeColorSecondary)
                }
                container.addView(
                    divider,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp2px(context, 0.5f).coerceAtLeast(1)
                    ).apply {
                        marginStart = dp2px(context, 16f)
                        marginEnd = dp2px(context, 16f)
                    }
                )
            }
        }

        return container
    }

    private fun createMenuItemRipple(
        colors: ColorTokens,
        isFirst: Boolean,
        isLast: Boolean
    ): android.graphics.drawable.Drawable {
        val rippleColor = Color.argb(
            30,
            Color.red(colors.textColorPrimary),
            Color.green(colors.textColorPrimary),
            Color.blue(colors.textColorPrimary)
        )
        val cornerRadius = dp2px(context, 8f).toFloat()
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(
                if (isFirst) cornerRadius else 0f,
                if (isFirst) cornerRadius else 0f,
                if (isFirst) cornerRadius else 0f,
                if (isFirst) cornerRadius else 0f,
                if (isLast) cornerRadius else 0f,
                if (isLast) cornerRadius else 0f,
                if (isLast) cornerRadius else 0f,
                if (isLast) cornerRadius else 0f
            )
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
    }

    private inner class ConversationPopupMenu(
        private val anchorView: View,
        private val popupHostView: View,
        private val popupWindowX: Int,
        private val popupWindowY: Int,
        private val popupWindowWidth: Int,
        private val popupWindowHeight: Int,
        private val menuView: View,
        private val popupX: Int,
        private val popupY: Int,
        private val showBelow: Boolean,
        private val colors: ColorTokens
    ) {

        private val popupWindow: PopupWindow
        private var isDismissed = false
        private var isDismissAnimating = false

        init {
            val overlayView = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setOnClickListener {
                    dismiss()
                }
                addView(
                    menuView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        leftMargin = popupX
                        topMargin = popupY
                    }
                )
            }
            popupWindow = PopupWindow(
                overlayView,
                popupWindowWidth,
                popupWindowHeight,
                true
            ).apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                isOutsideTouchable = true
                isFocusable = true
                animationStyle = 0
                elevation = 0f
                setOnDismissListener {
                    if (currentPopup == this@ConversationPopupMenu) {
                        currentPopup = null
                    }
                    (anchorView as? ConversationItemLayout)?.setHighlighted(false, colors)
                }
            }
        }

        fun show() {
            popupWindow.showAtLocation(
                popupHostView,
                Gravity.NO_GRAVITY,
                popupWindowX,
                popupWindowY
            )
            startShowAnimation()
        }

        fun dismiss(afterDismiss: (() -> Unit)? = null) {
            if (isDismissed) {
                afterDismiss?.invoke()
                return
            }
            if (isDismissAnimating) {
                return
            }
            isDismissAnimating = true
            val dismissTranslation = if (showBelow) {
                -dp2px(context, 6f).toFloat()
            } else {
                dp2px(context, 6f).toFloat()
            }
            menuView.animate()
                .alpha(0f)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .translationY(dismissTranslation)
                .setDuration(160L)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        finishDismiss(afterDismiss)
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        finishDismiss(afterDismiss)
                    }
                })
                .start()
        }

        fun dismissImmediately() {
            finishDismiss(afterDismiss = null)
        }

        private fun startShowAnimation() {
            menuView.pivotX = if (anchorView.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                dp2px(context, 16f).toFloat()
            } else {
                (menuView.measuredWidth - dp2px(context, 16f)).toFloat()
            }
            menuView.pivotY = if (showBelow) {
                0f
            } else {
                menuView.measuredHeight.toFloat()
            }
            val enterTranslation = if (showBelow) {
                -dp2px(context, 8f).toFloat()
            } else {
                dp2px(context, 8f).toFloat()
            }
            menuView.alpha = 0f
            menuView.scaleX = 0.92f
            menuView.scaleY = 0.92f
            menuView.translationY = enterTranslation
            menuView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(180L)
                .setListener(null)
                .start()
        }

        private fun finishDismiss(afterDismiss: (() -> Unit)?) {
            if (isDismissed) {
                afterDismiss?.invoke()
                return
            }
            isDismissed = true
            isDismissAnimating = false
            menuView.animate().setListener(null)
            popupWindow.dismiss()
            afterDismiss?.invoke()
        }
    }
}

class ConversationDividerDecoration(
    private val context: Context,
    private val themeStore: ThemeStore
) : RecyclerView.ItemDecoration() {

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        paint.color = colors.strokeColorSecondary

        val startInset = dp2px(context, 64f).toFloat()
        val lineHeight = dp2px(context, 0.5f).toFloat().coerceAtLeast(1f)
        val isRtl = parent.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val adapterItemCount = parent.adapter?.itemCount ?: return

        val childCount = parent.childCount
        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            val adapterPosition = parent.getChildAdapterPosition(child)
            if (adapterPosition == RecyclerView.NO_POSITION || adapterPosition >= adapterItemCount - 1) {
                continue
            }
            val bottom = (parent.layoutManager?.getDecoratedBottom(child) ?: child.bottom).toFloat()
            val left = if (isRtl) {
                parent.paddingLeft.toFloat()
            } else {
                parent.paddingLeft + startInset
            }
            val right = if (isRtl) {
                parent.width.toFloat() - parent.paddingRight - startInset
            } else {
                parent.width.toFloat() - parent.paddingRight
            }
            if (right > left) {
                c.drawRect(left, bottom, right, bottom + lineHeight, paint)
            }
        }
    }
}

fun dp2px(context: Context, dpValue: Float): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dpValue, context.resources.displayMetrics
    ).toInt()
}