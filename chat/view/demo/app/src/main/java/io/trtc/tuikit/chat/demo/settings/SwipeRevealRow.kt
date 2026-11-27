package io.trtc.tuikit.chat.demo.settings

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs

// Self-contained swipe-to-reveal row. A leftward swipe slides the foreground
// content aside to expose a trailing "delete" button; tapping the content
// selects it (or closes the row when already open). Default voices simply don't
// use this container, which is how they stay non-swipeable.
class SwipeRevealRow(context: Context) : FrameLayout(context) {

    val deleteButton: TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        maxLines = 1
    }

    private var content: View? = null
    private val deleteWidth = 80f * resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var downX = 0f
    private var downY = 0f
    private var downTranslation = 0f
    private var dragging = false

    var isOpen = false
        private set

    var rowClickListener: (() -> Unit)? = null
    var deleteClickListener: (() -> Unit)? = null
    var onOpenListener: ((SwipeRevealRow) -> Unit)? = null

    // In RTL the trailing delete button sits on the left, so the content reveals
    // it by sliding toward the positive (right) direction instead of negative.
    private val isRtl: Boolean
        get() = layoutDirection == LAYOUT_DIRECTION_RTL

    private val revealTranslation: Float
        get() = if (isRtl) deleteWidth else -deleteWidth

    init {
        addView(
            deleteButton,
            LayoutParams(deleteWidth.toInt(), LayoutParams.MATCH_PARENT, Gravity.END)
        )
        deleteButton.setOnClickListener { deleteClickListener?.invoke() }
    }

    fun setContent(view: View) {
        content?.let { removeView(it) }
        content = view
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        view.setOnClickListener {
            if (isOpen) close() else rowClickListener?.invoke()
        }
    }

    fun open() {
        val target = content ?: return
        isOpen = true
        target.animate().translationX(revealTranslation).setDuration(ANIM_DURATION_MS).start()
        onOpenListener?.invoke(this)
    }

    fun close() {
        val target = content ?: return
        isOpen = false
        target.animate().translationX(0f).setDuration(ANIM_DURATION_MS).start()
    }

    fun closeImmediate() {
        isOpen = false
        content?.animate()?.cancel()
        content?.translationX = 0f
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                downTranslation = content?.translationX ?: 0f
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!dragging && abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val target = content ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTranslation = target.translationX
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                if (dragging) {
                    val raw = downTranslation + (event.x - downX)
                    val newTranslation = if (isRtl) {
                        raw.coerceIn(0f, deleteWidth)
                    } else {
                        raw.coerceIn(-deleteWidth, 0f)
                    }
                    target.translationX = newTranslation
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    val pastHalf = if (isRtl) {
                        target.translationX >= deleteWidth / 2f
                    } else {
                        target.translationX <= -deleteWidth / 2f
                    }
                    if (pastHalf) open() else close()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        private const val ANIM_DURATION_MS = 180L
    }
}
