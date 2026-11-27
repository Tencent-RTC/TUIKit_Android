package io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomAction
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus

internal class MessageLongPressPopup(
    private val context: Context,
    private val anchorView: View,
    private val messageListView: View,
    private val message: MessageInfo,
    private val viewModel: MessageListViewModel,
    private val config: MessageListConfigProtocol,
    actions: List<MessageCustomAction>
) {

    private val density = context.resources.displayMetrics.density
    private val colors: ColorTokens
        get() = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

    private val allActions = buildPopupActions(actions)
    private var popupWindow: PopupWindow? = null

    private var bubbleLayout: BubbleMenuLayout? = null
    private var menuView: View? = null
    private var expandedEmojiView: View? = null
    private var switchContainer: FrameLayout? = null
    private var popupRoot: FrameLayout? = null

    private var showAbove: Boolean = true
    private var popupX: Int = 0
    private var popupY: Int = 0

    private var menuSwitchHeightPx: Int = 0
    private var isEmojiExpanded: Boolean = false
    private var expandAnimator: ValueAnimator? = null

    fun show() {
        if (allActions.isEmpty()) {
            return
        }

        val bubbleLayout = BubbleMenuLayout(context)
        this.bubbleLayout = bubbleLayout
        val shadowPadH = bubbleLayout.getShadowPadH()
        val shadowPadTop = bubbleLayout.getShadowPadTop()
        val shadowPadBottom = bubbleLayout.getShadowPadBottom()
        val arrowHeightPx = bubbleLayout.getArrowHeight()
        val popupCard = buildCard()
        val card = popupCard.view
        card.measure(
            View.MeasureSpec.makeMeasureSpec(popupCard.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val contentW = card.measuredWidth

        val anchorLocation = IntArray(2)
        anchorView.getLocationOnScreen(anchorLocation)
        val anchorScreenX = anchorLocation[0]
        val anchorScreenY = anchorLocation[1]
        val anchorW = anchorView.width
        val anchorH = anchorView.height

        val listLocation = IntArray(2)
        messageListView.getLocationOnScreen(listLocation)
        val listTop = listLocation[1]
        val listBottom = listLocation[1] + messageListView.height

        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenMargin = LongPressDimens.screenMargin(density)
        val visualGap = LongPressDimens.visualGap(density)

        val maxSwitchH = maxOf(menuSwitchHeightPx, emojiPanelTotalHeight())
        val chromeH = arrowHeightPx + shadowPadTop + shadowPadBottom
        val maxBubbleH = maxSwitchH + chromeH

        val position = LongPressPositionCalculator.calculate(
            anchor = LongPressPositionCalculator.AnchorBounds(
                screenX = anchorScreenX,
                screenY = anchorScreenY,
                width = anchorW,
                height = anchorH
            ),
            list = LongPressPositionCalculator.ListBounds(
                top = listTop,
                bottom = listBottom
            ),
            screenWidth = screenWidth,
            contentWidth = contentW,
            maxBubbleHeight = maxBubbleH,
            screenMargin = screenMargin,
            visualGap = visualGap,
            chrome = LongPressPositionCalculator.Chrome(
                shadowPadH = shadowPadH,
                shadowPadTop = shadowPadTop,
                shadowPadBottom = shadowPadBottom
            )
        )
        val popupW = position.popupWidth
        this.showAbove = position.showAbove
        this.popupX = position.popupX
        this.popupY = position.popupY
        this.isEmojiExpanded = false

        bubbleLayout.setBubbleStyle(
            bubbleColor = colors.dropdownColorDefault,
            isArrowOnTop = !position.showAbove,
            arrowCenterX = position.arrowCenterX
        )
        bubbleLayout.addView(
            card,
            FrameLayout.LayoutParams(contentW, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val popupRoot = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(popupW, maxBubbleH)
            clipChildren = false
            clipToPadding = false
            addView(
                bubbleLayout,
                FrameLayout.LayoutParams(
                    popupW,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    if (position.showAbove) Gravity.BOTTOM else Gravity.TOP
                )
            )
        }
        this.popupRoot = popupRoot

        popupRoot.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val bubble = this.bubbleLayout
                if (bubble != null) {
                    val x = event.x.toInt()
                    val y = event.y.toInt()
                    val insideBubble = x >= bubble.left && x < bubble.right &&
                        y >= bubble.top && y < bubble.bottom
                    if (!insideBubble) {
                        dismiss()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        val popup = PopupWindow(
            popupRoot,
            popupW,
            maxBubbleH,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(0))
            isOutsideTouchable = true
            isFocusable = false
            animationStyle = android.R.style.Animation_Dialog
            elevation = 0f
            setOnDismissListener {
                if (popupWindow == this) {
                    popupWindow = null
                }
            }
        }
        popupWindow = popup
        popup.showAtLocation(anchorView, Gravity.NO_GRAVITY, position.popupX, position.popupY)
    }

    fun dismiss() {
        expandAnimator?.cancel()
        expandAnimator = null
        popupWindow?.dismiss()
        popupWindow = null
        bubbleLayout = null
        menuView = null
        expandedEmojiView = null
        switchContainer = null
        popupRoot = null
    }

    private fun buildPopupActions(actions: List<MessageCustomAction>): List<LongPressPopupAction> {
        val popupActions = toLongPressPopupActions(actions).toMutableList()
        if (actions.isNotEmpty() &&
            config.isSupportReaction &&
            message.status == MessageStatus.SEND_SUCCESS &&
            EmojiManager.reactionEmojiListForPicker().isNotEmpty()
        ) {
            popupActions += LongPressPopupAction(
                title = context.getString(R.string.message_list_menu_reaction),
                dangerousAction = false,
                iconResId = R.drawable.message_list_menu_reaction_icon,
                onClick = {},
                isReactionEntry = true
            )
        }
        return popupActions
    }

    private fun buildCard(): PopupCard {
        val hasReactionEntry = allActions.any { it.isReactionEntry }
        val menuContent = LongPressActionMenuBuilder(
            context = context,
            density = density,
            colors = colors,
            message = message,
            actions = allActions,
            onDismiss = ::dismiss,
            onReactionEntry = { handleExpandedToggle(true) }
        ).build(forceFullColumns = hasReactionEntry)
        menuView = menuContent.view
        val cardWidth = menuContent.width

        val switchArea = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                cardWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(
                menuContent.view,
                FrameLayout.LayoutParams(
                    menuContent.contentWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL
                )
            )
        }
        switchContainer = switchArea

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                cardWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(
                switchArea,
                LinearLayout.LayoutParams(
                    cardWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        root.measure(
            View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        menuSwitchHeightPx = menuContent.height
        return PopupCard(
            view = root,
            width = root.measuredWidth
        )
    }

    private fun handleExpandedToggle(expanded: Boolean) {
        val container = switchContainer ?: return
        val menu = menuView ?: return
        val cardWidth = container.layoutParams.width.takeIf { it > 0 }
            ?: LongPressDimens.popupCardWidth(LongPressDimens.COLUMNS, density)

        val emojiPanel = if (expanded) {
            expandedEmojiView ?: emojiPanelBuilder().build(cardWidth).also { panel ->
                expandedEmojiView = panel
                container.addView(
                    panel,
                    FrameLayout.LayoutParams(
                        cardWidth,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_HORIZONTAL
                    )
                )
            }
        } else {
            expandedEmojiView
        }

        if (expanded) {
            menu.visibility = View.GONE
            emojiPanel?.visibility = View.VISIBLE
        } else {
            emojiPanel?.visibility = View.GONE
            menu.visibility = View.VISIBLE
        }

        val currentExpanded = isEmojiExpanded
        val startSwitchH = container.layoutParams.height.takeIf { it > 0 }
            ?: if (currentExpanded) emojiPanelTotalHeight() else menuSwitchHeightPx
        val targetSwitchH = if (expanded) emojiPanelTotalHeight() else menuSwitchHeightPx

        isEmojiExpanded = expanded

        val containerLp = container.layoutParams
        containerLp.height = startSwitchH
        container.layoutParams = containerLp

        expandAnimator?.cancel()
        expandAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = LongPressDimens.SWITCH_ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                val curSwitchH = (startSwitchH + (targetSwitchH - startSwitchH) * t).toInt()
                containerLp.height = curSwitchH
                container.layoutParams = containerLp
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (expandAnimator !== animation) {
                        return
                    }
                    containerLp.height = targetSwitchH
                    container.layoutParams = containerLp
                    expandAnimator = null
                }
            })
            start()
        }
    }

    private fun emojiPanelTotalHeight(): Int {
        return emojiPanelBuilder().totalHeight()
    }

    private fun emojiPanelBuilder(): LongPressEmojiPanelBuilder {
        return LongPressEmojiPanelBuilder(
            context = context,
            density = density,
            colors = colors,
            message = message,
            viewModel = viewModel,
            onDismiss = ::dismiss,
            onCollapse = { handleExpandedToggle(false) },
            onShowAllEmoji = {
                dismiss()
                viewModel.showEmojiPicker(message)
            }
        )
    }

    private data class PopupCard(
        val view: View,
        val width: Int
    )
}
