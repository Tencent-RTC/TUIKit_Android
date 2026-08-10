package io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomAction
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlin.math.min
import kotlin.math.roundToInt

internal class AuxiliaryTextLongPressPopup(
    private val context: Context,
    private val anchorView: View,
    private val messageListView: View,
    private val message: MessageInfo,
    actions: List<MessageCustomAction>
) {

    companion object {
        private const val MAX_COLUMNS = 4

        private const val ITEM_CELL_WIDTH_DP = 42
        private const val ITEM_CELL_HEIGHT_DP = 48
        private const val PAGE_PADDING_H_DP = 7
        private const val PAGE_PADDING_V_DP = 4
        private const val ITEM_PADDING_H_DP = 3
        private const val ITEM_PADDING_TOP_DP = 7
        private const val ITEM_PADDING_BOTTOM_DP = 6
        private const val ICON_SIZE_DP = 16
        private const val TEXT_TOP_PADDING_DP = 3
        private const val DIVIDER_MARGIN_DP = 3
    }

    private val density = context.resources.displayMetrics.density
    private val colors: ColorTokens
        get() = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

    private val popupActions = actions.map { action ->
        PopupAction(
            title = action.title,
            dangerousAction = action.dangerous,
            iconResId = action.iconResID,
            onClick = action.action
        )
    }

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

        val columnCount = popupActions.size.coerceIn(1, MAX_COLUMNS)
        val cardWidth = popupCardWidth(columnCount)
        val cardView = buildPage(popupActions, columnCount)
        cardView.measure(
            View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val contentW = cardView.measuredWidth
        val contentH = cardView.measuredHeight

        val anchorLocation = IntArray(2)
        anchorView.getLocationOnScreen(anchorLocation)
        val anchorScreenX = anchorLocation[0]
        val anchorScreenY = anchorLocation[1]
        val anchorW = anchorView.width
        val anchorH = anchorView.height
        val anchorCenterX = anchorScreenX + anchorW / 2f

        val listLocation = IntArray(2)
        messageListView.getLocationOnScreen(listLocation)
        val listTop = listLocation[1]
        val listBottom = listLocation[1] + messageListView.height

        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenMargin = 8.dp
        val visualGap = 3.dp
        val popupW = contentW + shadowPadH * 2
        val preferredX = (anchorCenterX - popupW / 2f).toInt()
        val maxX = screenWidth - popupW - screenMargin
        val popupX = preferredX.coerceIn(screenMargin, maxX)

        val totalH = contentH + arrowHeightPx + shadowPadTop + shadowPadBottom
        val abovePopupY = anchorScreenY - totalH - visualGap + shadowPadBottom
        val belowPopupY = anchorScreenY + anchorH + visualGap - shadowPadTop
        val showAbove = abovePopupY >= listTop - shadowPadTop
        val popupY = if (showAbove) {
            abovePopupY.coerceAtLeast(listTop - shadowPadTop)
        } else {
            val maxY = listBottom - totalH + shadowPadBottom
            belowPopupY.coerceAtMost(maxY)
        }

        val arrowCenterX = anchorCenterX - popupX - shadowPadH
        bubbleLayout.setBubbleStyle(
            bubbleColor = colors.dropdownColorDefault,
            isArrowOnTop = !showAbove,
            arrowCenterX = arrowCenterX
        )
        bubbleLayout.addView(
            cardView,
            FrameLayout.LayoutParams(contentW, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        bubbleLayout.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
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
        popup.showAtLocation(anchorView, Gravity.NO_GRAVITY, popupX, popupY)
    }

    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    private fun buildPage(items: List<PopupAction>, columnCount: Int): View {
        val itemCellWidth = popupItemCellWidth()
        val itemCellHeight = popupItemCellHeight()
        val rows = items.chunked(columnCount)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                PAGE_PADDING_H_DP.dp,
                PAGE_PADDING_V_DP.dp,
                PAGE_PADDING_H_DP.dp,
                PAGE_PADDING_V_DP.dp
            )
            rows.forEachIndexed { rowIndex, rowItems ->
                addView(buildRow(rowItems, itemCellWidth, itemCellHeight, columnCount))
                if (rowIndex < rows.lastIndex) {
                    addView(buildDivider())
                }
            }
        }
    }

    private fun buildRow(
        items: List<PopupAction>,
        cellWidth: Int,
        cellHeight: Int,
        columnCount: Int
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            isBaselineAligned = false
            minimumHeight = cellHeight
            for (col in 0 until columnCount) {
                val action = items.getOrNull(col)
                val child = if (action != null) {
                    buildActionItem(action, cellHeight)
                } else {
                    View(context).apply { minimumHeight = cellHeight }
                }
                addView(child, LinearLayout.LayoutParams(cellWidth, cellHeight))
            }
        }
    }

    private fun buildActionItem(action: PopupAction, cellHeight: Int): View {
        val textColor = if (action.dangerousAction) {
            colors.textColorError
        } else {
            colors.textColorSecondary
        }
        val iconTint = textColor
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = cellHeight
            setPadding(
                ITEM_PADDING_H_DP.dp,
                ITEM_PADDING_TOP_DP.dp,
                ITEM_PADDING_H_DP.dp,
                ITEM_PADDING_BOTTOM_DP.dp
            )
            isClickable = true
            isFocusable = true
            background = createActionItemRipple()
            setOnClickListener {
                dismiss()
                action.onClick(message)
            }
            addView(
                ImageView(context).apply {
                    if (action.iconResId != 0) {
                        setImageResource(action.iconResId)
                        imageTintList = ColorStateList.valueOf(iconTint)
                    } else {
                        visibility = View.GONE
                    }
                },
                LinearLayout.LayoutParams(ICON_SIZE_DP.dp, ICON_SIZE_DP.dp).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            )
            addView(
                TextView(context).apply {
                    text = action.title
                    gravity = Gravity.CENTER
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    maxLines = 1
                    setPadding(0, TEXT_TOP_PADDING_DP.dp, 0, 0)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            )
        }
    }

    private fun buildDivider(): View {
        return View(context).apply {
            setBackgroundColor(ColorUtils.setAlphaComponent(colors.strokeColorPrimary, 140))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                topMargin = DIVIDER_MARGIN_DP.dp
                bottomMargin = DIVIDER_MARGIN_DP.dp
            }
        }
    }

    private fun createActionItemRipple(): RippleDrawable {
        val rippleColor = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(colors.textColorPrimary, 26)
        )
        val maskDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 6 * density
            setColor(0xFFFFFFFF.toInt())
        }
        return RippleDrawable(rippleColor, null, maskDrawable)
    }

    private fun popupItemCellWidth(): Int = ITEM_CELL_WIDTH_DP.dp

    private fun popupItemCellHeight(): Int = ITEM_CELL_HEIGHT_DP.dp

    private fun popupCardWidth(columnCount: Int): Int {
        return popupItemCellWidth() * columnCount + PAGE_PADDING_H_DP.dp * 2
    }

    private val Int.dp: Int
        get() = (this * density).roundToInt()

    private data class PopupAction(
        val title: String,
        val dangerousAction: Boolean,
        @DrawableRes val iconResId: Int,
        val onClick: (MessageInfo) -> Unit
    )

    private class BubbleMenuLayout(context: Context) : FrameLayout(context) {

        private val density = context.resources.displayMetrics.density
        private fun dp(value: Int): Float = value * density

        private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val cornerRadius = dp(5)
        private val arrowWidth = dp(10)
        private val arrowHeightPx = dp(5)
        private val shadowBlur = dp(6)
        private val shadowOffsetY = dp(2)
        private val shadowColor = 0x33000000
        private val shadowPadH = dp(6).toInt()
        private val shadowPadTop = dp(4).toInt()
        private val shadowPadBottom = dp(7).toInt()
        private val bubbleRect = RectF()

        private var bubbleColor: Int = 0
        private var isArrowOnTop: Boolean = true
        private var arrowCenterXInBubble: Float = 0f

        init {
            setWillNotDraw(false)
            clipChildren = false
            clipToPadding = false
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        fun setBubbleStyle(
            bubbleColor: Int,
            isArrowOnTop: Boolean,
            arrowCenterX: Float
        ) {
            this.bubbleColor = bubbleColor
            this.isArrowOnTop = isArrowOnTop
            this.arrowCenterXInBubble = arrowCenterX + shadowPadH
            val topPadding = shadowPadTop + if (isArrowOnTop) arrowHeightPx.toInt() else 0
            val bottomPadding = shadowPadBottom + if (isArrowOnTop) 0 else arrowHeightPx.toInt()
            setPadding(shadowPadH, topPadding, shadowPadH, bottomPadding)
            invalidate()
        }

        fun getShadowPadH(): Int = shadowPadH

        fun getShadowPadTop(): Int = shadowPadTop

        fun getShadowPadBottom(): Int = shadowPadBottom

        fun getArrowHeight(): Int = arrowHeightPx.toInt()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val left = shadowPadH.toFloat()
            val top = shadowPadTop.toFloat() + if (isArrowOnTop) arrowHeightPx else 0f
            val right = width - shadowPadH.toFloat()
            val bottom = height - shadowPadBottom.toFloat() - if (isArrowOnTop) 0f else arrowHeightPx
            bubbleRect.set(left, top, right, bottom)
            val combinedPath = createBubbleWithArrowPath()
            bubblePaint.color = bubbleColor
            bubblePaint.setShadowLayer(shadowBlur, 0f, shadowOffsetY, shadowColor)
            canvas.drawPath(combinedPath, bubblePaint)
            bubblePaint.clearShadowLayer()
            canvas.drawPath(combinedPath, bubblePaint)
        }

        private fun createBubbleWithArrowPath(): Path {
            val availableWidth = bubbleRect.width().coerceAtLeast(0f)
            val availableHeight = bubbleRect.height().coerceAtLeast(0f)
            val safeCornerRadius = min(cornerRadius, min(availableWidth, availableHeight) / 2f)
            val safeArrowHalfWidth = min(arrowWidth / 2f, (availableWidth / 2f - safeCornerRadius).coerceAtLeast(0f))
            val centerMin = bubbleRect.left + safeCornerRadius + safeArrowHalfWidth
            val centerMax = bubbleRect.right - safeCornerRadius - safeArrowHalfWidth
            val centerX = if (centerMin <= centerMax) {
                arrowCenterXInBubble.coerceIn(centerMin, centerMax)
            } else {
                bubbleRect.centerX()
            }
            val tipY = if (isArrowOnTop) {
                bubbleRect.top - arrowHeightPx
            } else {
                bubbleRect.bottom + arrowHeightPx
            }
            val l = bubbleRect.left
            val t = bubbleRect.top
            val r = bubbleRect.right
            val b = bubbleRect.bottom
            val cr = safeCornerRadius
            return Path().apply {
                if (isArrowOnTop) {
                    moveTo(l + cr, t)
                    lineTo(centerX - safeArrowHalfWidth, t)
                    lineTo(centerX, tipY)
                    lineTo(centerX + safeArrowHalfWidth, t)
                    lineTo(r - cr, t)
                    arcTo(r - 2 * cr, t, r, t + 2 * cr, 270f, 90f, false)
                    lineTo(r, b - cr)
                    arcTo(r - 2 * cr, b - 2 * cr, r, b, 0f, 90f, false)
                    lineTo(l + cr, b)
                    arcTo(l, b - 2 * cr, l + 2 * cr, b, 90f, 90f, false)
                    lineTo(l, t + cr)
                    arcTo(l, t, l + 2 * cr, t + 2 * cr, 180f, 90f, false)
                } else {
                    moveTo(l + cr, t)
                    lineTo(r - cr, t)
                    arcTo(r - 2 * cr, t, r, t + 2 * cr, 270f, 90f, false)
                    lineTo(r, b - cr)
                    arcTo(r - 2 * cr, b - 2 * cr, r, b, 0f, 90f, false)
                    lineTo(centerX + safeArrowHalfWidth, b)
                    lineTo(centerX, tipY)
                    lineTo(centerX - safeArrowHalfWidth, b)
                    lineTo(l + cr, b)
                    arcTo(l, b - 2 * cr, l + 2 * cr, b, 90f, 90f, false)
                    lineTo(l, t + cr)
                    arcTo(l, t, l + 2 * cr, t + 2 * cr, 180f, 90f, false)
                }
                close()
            }
        }
    }
}
