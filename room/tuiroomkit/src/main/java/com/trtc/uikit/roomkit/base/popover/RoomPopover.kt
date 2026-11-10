package com.trtc.uikit.roomkit.base.popover

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.util.dp
import com.trtc.uikit.roomkit.base.util.dpF
import kotlin.math.max

class RoomPopover(private val anchor: View) {

    enum class Align { START, CENTER, END }

    var align: Align = Align.CENTER
    var gap: Int = anchor.context.dp(GAP_DP)
    var minWidth: Int = anchor.context.dp(MIN_WIDTH_DP)
    var showsArrow: Boolean = false

    var onClose: (() -> Unit)? = null

    val contentView: FrameLayout = FrameLayout(anchor.context)

    private val bubble: FrameLayout
    private val mask: View
    private var host: ViewGroup? = null
    private var isClosing = false

    init {
        val context = anchor.context
        bubble = FrameLayout(context).apply {
            elevation = context.dp(BUBBLE_ELEVATION_DP).toFloat()
        }
        bubble.addView(
            BubbleView(context),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        bubble.addView(
            contentView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        mask = View(context).apply {
            setOnClickListener { close() }
        }
    }

    private val context: Context get() = anchor.context

    fun show(inHost: ViewGroup) {
        if (host != null || isClosing) return
        check(inHost is FrameLayout) { "RoomPopover host must be a FrameLayout" }
        host = inHost
        inHost.addView(
            mask,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        val arrowArea = if (showsArrow) context.dp(ARROW_AREA_DP) else 0
        val inset = context.dp(CONTENT_INSET_DP)
        (contentView.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = arrowArea + inset
            leftMargin = inset
            rightMargin = inset
            bottomMargin = inset
        }
        val contentWidth = max(minWidth - inset * 2, contentDesiredWidth(inHost.width)).coerceAtMost(inHost.width - inset * 2)
        contentView.measure(
            View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(inHost.height, View.MeasureSpec.AT_MOST),
        )
        val bubbleWidth = contentView.measuredWidth + inset * 2
        val bubbleHeight = contentView.measuredHeight + inset * 2 + arrowArea
        bubble.measure(
            View.MeasureSpec.makeMeasureSpec(bubbleWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(bubbleHeight, View.MeasureSpec.EXACTLY),
        )

        val anchorRect = AnchorRect(anchor, inHost)
        val left = when (align) {
            Align.START -> anchorRect.left
            Align.CENTER -> anchorRect.centerX - bubbleWidth / 2
            Align.END -> anchorRect.right - bubbleWidth
        }.coerceIn(0, max(0, inHost.width - bubbleWidth))
        val top = (anchorRect.bottom + gap).coerceAtMost(max(0, inHost.height - bubbleHeight))

        val params = FrameLayout.LayoutParams(bubbleWidth, bubbleHeight).apply {
            leftMargin = left
            topMargin = top
        }
        inHost.addView(bubble, params)

        bubble.alpha = 0f
        bubble.translationY = context.dpF(ENTER_OFFSET_DP.toFloat())
        bubble.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ANIM_DURATION)
            .setInterpolator(ENTER_INTERPOLATOR)
            .start()
    }

    fun close() {
        if (isClosing) return
        val currentHost = host ?: return
        isClosing = true
        host = null
        onClose?.invoke()
        bubble.animate()
            .alpha(0f)
            .translationY(context.dpF(ENTER_OFFSET_DP.toFloat()))
            .setDuration(ANIM_DURATION)
            .setInterpolator(ENTER_INTERPOLATOR)
            .withEndAction {
                currentHost.removeView(bubble)
                currentHost.removeView(mask)
                isClosing = false
            }
            .start()
    }

    private fun contentDesiredWidth(hostWidth: Int): Int {
        contentView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return contentView.measuredWidth
    }

    private class AnchorRect(private val view: View, private val ancestor: ViewGroup) {
        val left: Int get() = location()[0]
        val top: Int get() = location()[1]
        val right: Int get() = left + view.width
        val bottom: Int get() = top + view.height
        val centerX: Int get() = left + view.width / 2
        private fun location(): IntArray {
            val own = IntArray(2).also { view.getLocationInWindow(it) }
            val base = IntArray(2).also { ancestor.getLocationInWindow(it) }
            return intArrayOf(own[0] - base[0], own[1] - base[1])
        }
    }

    private inner class BubbleView(context: Context) : View(context) {

        private val path = Path()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val stroke = resources.displayMetrics.density
            val inset = stroke / 2f
            val radius = context.dp(BUBBLE_RADIUS_DP) - inset
            val arrowArea = if (showsArrow) context.dp(ARROW_AREA_DP).toFloat() else 0f
            val arrowHalf = context.dpF(ARROW_HALF_DP)
            val arrowCenterX = when (align) {
                Align.START -> context.dp(ARROW_EDGE_INSET_DP).toFloat()
                Align.CENTER -> width / 2f
                Align.END -> (width - context.dp(ARROW_EDGE_INSET_DP)).toFloat()
            }
            path.reset()
            path.moveTo(inset + radius, arrowArea + inset)
            if (showsArrow) {
                path.lineTo(arrowCenterX - arrowHalf, arrowArea + inset)
                path.lineTo(arrowCenterX, inset)
                path.lineTo(arrowCenterX + arrowHalf, arrowArea + inset)
            }
            path.lineTo(width - inset - radius, arrowArea + inset)
            path.arcTo(width - inset - radius, arrowArea + inset, width - inset, arrowArea + inset + radius * 2, -90f, 90f, false)
            path.lineTo(width - inset, height - inset - radius)
            path.arcTo(width - inset - radius, height - inset - radius, width - inset, height - inset, 0f, 90f, false)
            path.lineTo(inset + radius, height - inset)
            path.arcTo(inset, height - inset - radius, inset + radius, height - inset, 90f, 90f, false)
            path.lineTo(inset, arrowArea + inset + radius)
            path.arcTo(inset, arrowArea + inset, inset + radius, arrowArea + inset + radius, 180f, 90f, false)
            path.close()
            paint.style = Paint.Style.FILL
            paint.color = ContextCompat.getColor(context, R.color.roomkit_bg_popover_solid)
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = ContextCompat.getColor(context, R.color.roomkit_border_strong)
            canvas.drawPath(path, paint)
        }
    }

    companion object {
        private const val GAP_DP = 10
        private const val MIN_WIDTH_DP = 176
        private const val BUBBLE_RADIUS_DP = 14
        private const val BUBBLE_ELEVATION_DP = 8
        private const val CONTENT_INSET_DP = 9
        private const val ARROW_AREA_DP = 8
        private const val ARROW_HALF_DP = 7.5f
        private const val ARROW_EDGE_INSET_DP = 28
        private const val ENTER_OFFSET_DP = -8
        private const val ANIM_DURATION = 200L
        private val ENTER_INTERPOLATOR = PathInterpolator(0.23f, 1f, 0.32f, 1f)
    }
}
