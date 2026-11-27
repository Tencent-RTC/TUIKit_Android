package io.trtc.tuikit.chat.uikit.pages
import android.R
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextPaint
import android.text.TextUtils
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
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import kotlin.math.ceil


data class PopupMenuItem(
    val title: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    @DrawableRes val iconResId: Int = 0
)

class PopupMenuHelper(private val context: Context) {

    companion object {
        private const val ITEM_CLICK_FEEDBACK_DELAY_MS = 120L
    }

    private val density = context.resources.displayMetrics.density
    private var currentPopupWindow: PopupWindow? = null

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()
    private fun dpF(value: Int): Float = value * density

    private fun sp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value,
            context.resources.displayMetrics
        )
    }

    fun show(anchor: View, items: List<PopupMenuItem>) {
        if (items.isEmpty()) {
            return
        }

        currentPopupWindow?.dismiss()

        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        val arrowHeight = dpF(6)

        val menuContentWidth = calculateMenuContentWidth(items)
        val menuLayout = buildMenuLayout(items, colors, menuContentWidth)
        menuLayout.measure(
            View.MeasureSpec.makeMeasureSpec(menuContentWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val contentW = menuContentWidth
        val contentH = menuLayout.measuredHeight

        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val anchorScreenX = anchorLocation[0]
        val anchorScreenY = anchorLocation[1]
        val anchorW = anchor.width
        val anchorH = anchor.height
        val anchorCenterX = anchorScreenX + anchorW / 2f

        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val screenMargin = dp(8)
        val visualGap = dp(4)

        val bubbleLayout = BubbleMenuLayout(context)
        val shadowPadH = bubbleLayout.getShadowPadH()
        val shadowPadTop = bubbleLayout.getShadowPadTop()
        val shadowPadBottom = bubbleLayout.getShadowPadBottom()

        val popupW = contentW + shadowPadH * 2

        val preferredX = (anchorCenterX - shadowPadH - contentW / 2f).toInt()
        val maxX = (screenWidth - popupW - screenMargin).coerceAtLeast(screenMargin)
        val popupX = preferredX.coerceIn(screenMargin, maxX)

        val belowPopupY = anchorScreenY + anchorH + visualGap - shadowPadTop
        val totalH = contentH + arrowHeight.toInt() + shadowPadTop + shadowPadBottom
        val abovePopupY = anchorScreenY - totalH - visualGap + shadowPadBottom
        val showAbove = belowPopupY + totalH > screenHeight && abovePopupY >= screenMargin
        val popupY = if (showAbove) {
            abovePopupY.coerceAtLeast(screenMargin)
        } else {
            belowPopupY.coerceAtMost(screenHeight - totalH - screenMargin)
        }

        val arrowCenterX = anchorCenterX - popupX - shadowPadH

        bubbleLayout.setBubbleStyle(
            bubbleColor = colors.bgColorDialog,
            isArrowOnTop = !showAbove,
            arrowCenterX = arrowCenterX
        )
        bubbleLayout.addView(
            menuLayout,
            FrameLayout.LayoutParams(contentW, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val contentView = bubbleLayout
        contentView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val popupWindow = PopupWindow(
            contentView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(0))
            isOutsideTouchable = true
            isFocusable = true
            animationStyle = R.style.Animation_Dialog
            elevation = 0f
            setOnDismissListener {
                if (currentPopupWindow == this) {
                    currentPopupWindow = null
                }
            }
        }
        currentPopupWindow = popupWindow
        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, popupX, popupY)
    }

    fun dismiss() {
        currentPopupWindow?.dismiss()
    }

    private fun buildMenuLayout(
        items: List<PopupMenuItem>,
        colors: ColorTokens,
        menuWidth: Int
    ): LinearLayout {
        val menuLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val hasAnyIcon = items.any { it.iconResId != 0 }
        items.forEachIndexed { index, item ->
            val itemContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(10), dp(16), dp(10))
                isEnabled = item.enabled
                isClickable = item.enabled
                isFocusable = item.enabled
                background = createItemBackground(
                    colors = colors,
                    isFirst = index == 0,
                    isLast = index == items.lastIndex
                )
                setOnClickListener { view ->
                    if (!item.enabled) {
                        return@setOnClickListener
                    }
                    view.postDelayed({
                        item.onClick()
                        dismiss()
                    }, ITEM_CLICK_FEEDBACK_DELAY_MS)
                }
            }

            if (hasAnyIcon) {
                val iconView = ImageView(context).apply {
                    if (item.iconResId != 0) {
                        setImageResource(item.iconResId)
                        colorFilter = PorterDuffColorFilter(
                            if (item.enabled) colors.textColorPrimary else colors.textColorDisable,
                            PorterDuff.Mode.SRC_IN
                        )
                    }
                }
                val iconSize = dp(20)
                itemContainer.addView(
                    iconView,
                    LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        marginEnd = dp(8)
                    }
                )
            }

            val textView = TextView(context).apply {
                text = item.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.DEFAULT
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(if (item.enabled) colors.textColorPrimary else colors.textColorDisable)
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
            }
            itemContainer.addView(
                textView,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            menuLayout.addView(
                itemContainer,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            if (index < items.lastIndex) {
                menuLayout.addView(createDivider(colors))
            }
        }
        return menuLayout
    }

    private fun calculateMenuContentWidth(items: List<PopupMenuItem>): Int {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sp(16f)
            typeface = Typeface.DEFAULT
        }
        val maxTextWidth = items.maxOf { item ->
            ceil(textPaint.measureText(item.title).toDouble()).toInt()
        }
        val hasAnyIcon = items.any { it.iconResId != 0 }
        val iconSpace = if (hasAnyIcon) dp(20) + dp(8) else 0
        val contentPadding = dp(16) * 2
        val minWidth = dp(72)
        val maxWidth = context.resources.displayMetrics.widthPixels - dp(32)
        return (maxTextWidth + iconSpace + contentPadding).coerceIn(minWidth, maxWidth)
    }

    private fun createItemBackground(
        colors: ColorTokens,
        isFirst: Boolean,
        isLast: Boolean
    ): Drawable {
        val cornerRadius = dpF(8)
        val radii = floatArrayOf(
            if (isFirst) cornerRadius else 0f,
            if (isFirst) cornerRadius else 0f,
            if (isFirst) cornerRadius else 0f,
            if (isFirst) cornerRadius else 0f,
            if (isLast) cornerRadius else 0f,
            if (isLast) cornerRadius else 0f,
            if (isLast) cornerRadius else 0f,
            if (isLast) cornerRadius else 0f
        )
        val rippleColor = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(colors.textColorPrimary, 30)
        )
        val highlightColor = ColorUtils.setAlphaComponent(colors.textColorPrimary, 26)
        val defaultBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(colors.bgColorDialog)
        }
        val highlightBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(highlightColor)
        }
        val stateBackground = StateListDrawable().apply {
            addState(intArrayOf(R.attr.state_pressed), highlightBackground)
            addState(intArrayOf(R.attr.state_hovered), highlightBackground)
            addState(intArrayOf(R.attr.state_focused), highlightBackground)
            addState(intArrayOf(), defaultBackground)
        }
        val maskDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(colors.bgColorDialog)
        }
        return RippleDrawable(rippleColor, stateBackground, maskDrawable)
    }

    private fun createDivider(colors: ColorTokens): View {
        return View(context).apply {
            setBackgroundColor(colors.strokeColorSecondary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1).coerceAtLeast(1)
            ).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
            }
        }
    }

    private class BubbleMenuLayout(context: Context) : FrameLayout(context) {

        private val density = context.resources.displayMetrics.density
        private fun dp(value: Int): Float = value * density

        private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val arrowPath = Path()
        private val bubbleRect = RectF()
        private val cornerRadius = dp(8)
        private val arrowWidth = dp(12)
        private val arrowHeightPx = dp(6)

        private val shadowBlur = dp(8)
        private val shadowOffsetY = dp(2)
        private val shadowColor = 0x33000000
        private val shadowPadH = dp(8).toInt()
        private val shadowPadTop = dp(6).toInt()
        private val shadowPadBottom = dp(10).toInt()

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
            val arrowHalfWidth = arrowWidth / 2f
            val minCenter = bubbleRect.left + cornerRadius + arrowHalfWidth
            val maxCenter = bubbleRect.right - cornerRadius - arrowHalfWidth
            val centerX = arrowCenterXInBubble.coerceIn(minCenter, maxCenter)
            val tipY = if (isArrowOnTop) {
                bubbleRect.top - arrowHeightPx
            } else {
                bubbleRect.bottom + arrowHeightPx
            }

            val l = bubbleRect.left
            val t = bubbleRect.top
            val r = bubbleRect.right
            val b = bubbleRect.bottom
            val cr = cornerRadius

            val path = Path()

            if (isArrowOnTop) {
                path.moveTo(l + cr, t)
                path.lineTo(centerX - arrowHalfWidth, t)
                path.lineTo(centerX, tipY)
                path.lineTo(centerX + arrowHalfWidth, t)
                path.lineTo(r - cr, t)
                path.arcTo(r - 2 * cr, t, r, t + 2 * cr, 270f, 90f, false)
                path.lineTo(r, b - cr)
                path.arcTo(r - 2 * cr, b - 2 * cr, r, b, 0f, 90f, false)
                path.lineTo(l + cr, b)
                path.arcTo(l, b - 2 * cr, l + 2 * cr, b, 90f, 90f, false)
                path.lineTo(l, t + cr)
                path.arcTo(l, t, l + 2 * cr, t + 2 * cr, 180f, 90f, false)
            } else {
                path.moveTo(l + cr, t)
                path.lineTo(r - cr, t)
                path.arcTo(r - 2 * cr, t, r, t + 2 * cr, 270f, 90f, false)
                path.lineTo(r, b - cr)
                path.arcTo(r - 2 * cr, b - 2 * cr, r, b, 0f, 90f, false)
                path.lineTo(centerX + arrowHalfWidth, b)
                path.lineTo(centerX, tipY)
                path.lineTo(centerX - arrowHalfWidth, b)
                path.lineTo(l + cr, b)
                path.arcTo(l, b - 2 * cr, l + 2 * cr, b, 90f, 90f, false)
                path.lineTo(l, t + cr)
                path.arcTo(l, t, l + 2 * cr, t + 2 * cr, 180f, 90f, false)
            }

            path.close()
            return path
        }
    }
}
