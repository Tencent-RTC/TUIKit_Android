package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import android.annotation.SuppressLint
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import kotlin.math.abs

/**
 * 让 Markdown 气泡里的链接可点，同时保住气泡的长按菜单。
 *
 * 不能直接用 Markwon 默认的 LinkMovementMethod：它在 ACTION_DOWN 就消费事件，长按手势
 * 到不了 MessageInteractionBinder 绑在气泡容器上的监听，复制 / 转发 / 引用菜单会失效。
 *
 * 这里只在按下点正好落在链接上时才接管手势，落在别处一律返回 false，触摸链路和没装这个
 * 监听时完全一致。接管之后自己区分点击和长按：到了长按阈值就把长按转交给容器，抬手时
 * 没触发过长按、手指也没滑出去，才算点中链接。
 *
 * 多选态不用特殊处理：那时 MessageListAdapter 会在 item 上盖一层可点的 multiSelectOverlay，
 * 触摸根本到不了这里。
 */
internal class MarkdownLinkTouchListener private constructor(
    private val longPressTimeoutMs: Long,
    private val touchSlop: Int
) : View.OnTouchListener {

    private var pressedSpan: ClickableSpan? = null
    private var downX = 0f
    private var downY = 0f
    private var longPressDispatched = false
    private var pendingLongPress: Runnable? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val textView = view as? TextView ?: return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(textView, event)
            MotionEvent.ACTION_MOVE -> handleMove(textView, event)
            MotionEvent.ACTION_UP -> handleUp(textView)
            MotionEvent.ACTION_CANCEL -> handleCancel(textView)
            else -> false
        }
    }

    private fun handleDown(textView: TextView, event: MotionEvent): Boolean {
        val span = findLinkAt(textView, event.x, event.y) ?: return false
        pressedSpan = span
        downX = event.x
        downY = event.y
        longPressDispatched = false
        val runnable = Runnable {
            longPressDispatched = true
            dispatchLongClickToBubble(textView)
        }
        pendingLongPress = runnable
        textView.postDelayed(runnable, longPressTimeoutMs)
        return true
    }

    private fun handleMove(textView: TextView, event: MotionEvent): Boolean {
        if (pressedSpan == null) {
            return false
        }
        if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
            cancelPendingLongPress(textView)
            pressedSpan = null
        }
        return true
    }

    private fun handleUp(textView: TextView): Boolean {
        val span = pressedSpan ?: return false
        cancelPendingLongPress(textView)
        pressedSpan = null
        if (!longPressDispatched) {
            span.onClick(textView)
        }
        return true
    }

    private fun handleCancel(textView: TextView): Boolean {
        if (pressedSpan == null) {
            return false
        }
        cancelPendingLongPress(textView)
        pressedSpan = null
        return true
    }

    private fun cancelPendingLongPress(textView: TextView) {
        pendingLongPress?.let { textView.removeCallbacks(it) }
        pendingLongPress = null
    }

    private fun findLinkAt(textView: TextView, x: Float, y: Float): ClickableSpan? {
        val spanned = textView.text as? Spanned ?: return null
        val layout = textView.layout ?: return null
        val contentX = x - textView.totalPaddingLeft + textView.scrollX
        val contentY = y - textView.totalPaddingTop + textView.scrollY
        val line = layout.getLineForVertical(contentY.toInt())
        // 落在行距、段间空白里不算命中
        if (contentY < layout.getLineTop(line) || contentY > layout.getLineBottom(line)) {
            return null
        }
        // 超出这一行实际文字宽度不算命中，否则行尾整片空白都会误触
        if (contentX < layout.getLineLeft(line) || contentX > layout.getLineRight(line)) {
            return null
        }
        val offset = layout.getOffsetForHorizontal(line, contentX)
        return spanned.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull()
    }

    /** 逐级往上找真正装了长按监听的气泡容器，消费掉就停。 */
    private fun dispatchLongClickToBubble(view: View): Boolean {
        var current = view.parent as? View
        while (current != null) {
            if (current.isLongClickable && current.performLongClick()) {
                return true
            }
            current = current.parent as? View
        }
        return false
    }

    companion object {
        @SuppressLint("ClickableViewAccessibility")
        fun attach(textView: TextView) {
            val configuration = ViewConfiguration.get(textView.context)
            textView.setOnTouchListener(
                MarkdownLinkTouchListener(
                    longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong(),
                    touchSlop = configuration.scaledTouchSlop
                )
            )
        }
    }
}
