package io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomAction
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

internal class AuxiliaryTextLongPressPopup(
    private val context: Context,
    private val anchorView: View,
    private val messageListView: View,
    private val message: MessageInfo,
    actions: List<MessageCustomAction>
) {

    private val density = context.resources.displayMetrics.density
    private val colors: ColorTokens
        get() = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

    private val popupActions = toLongPressPopupActions(actions)

    private var popupWindow: PopupWindow? = null

    fun show() {
        if (popupActions.isEmpty()) {
            return
        }

        val bubbleLayout = BubbleMenuLayout(context)
        val shadowPadH = bubbleLayout.getShadowPadH()
        val shadowPadTop = bubbleLayout.getShadowPadTop()
        val shadowPadBottom = bubbleLayout.getShadowPadBottom()
        val arrowHeightPx = bubbleLayout.getArrowHeight()

        val menuContent = LongPressActionMenuBuilder(
            context = context,
            density = density,
            colors = colors,
            message = message,
            actions = popupActions,
            onDismiss = ::dismiss,
            onReactionEntry = {}
        ).build()
        val cardView = menuContent.view
        val contentW = menuContent.contentWidth
        val contentH = menuContent.height

        val anchorLocation = IntArray(2)
        anchorView.getLocationOnScreen(anchorLocation)

        val listLocation = IntArray(2)
        messageListView.getLocationOnScreen(listLocation)

        val position = LongPressPositionCalculator.calculate(
            anchor = LongPressPositionCalculator.AnchorBounds(
                screenX = anchorLocation[0],
                screenY = anchorLocation[1],
                width = anchorView.width,
                height = anchorView.height
            ),
            list = LongPressPositionCalculator.ListBounds(
                top = listLocation[1],
                bottom = listLocation[1] + messageListView.height
            ),
            screenWidth = context.resources.displayMetrics.widthPixels,
            contentWidth = contentW,
            maxBubbleHeight = contentH + arrowHeightPx + shadowPadTop + shadowPadBottom,
            screenMargin = LongPressDimens.screenMargin(density),
            visualGap = LongPressDimens.visualGap(density),
            chrome = LongPressPositionCalculator.Chrome(
                shadowPadH = shadowPadH,
                shadowPadTop = shadowPadTop,
                shadowPadBottom = shadowPadBottom
            )
        )

        bubbleLayout.setBubbleStyle(
            bubbleColor = colors.dropdownColorDefault,
            isArrowOnTop = !position.showAbove,
            arrowCenterX = position.arrowCenterX
        )
        bubbleLayout.addView(
            cardView,
            FrameLayout.LayoutParams(contentW, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val popup = PopupWindow(
            bubbleLayout,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(0))
            isOutsideTouchable = true
            isFocusable = true
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
        popupWindow?.dismiss()
        popupWindow = null
    }
}
