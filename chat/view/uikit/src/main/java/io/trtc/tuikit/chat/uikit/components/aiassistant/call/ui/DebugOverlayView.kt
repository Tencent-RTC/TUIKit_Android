package io.trtc.tuikit.chat.uikit.components.aiassistant.call.ui

import android.content.Context
import android.graphics.Typeface
import android.text.method.ScrollingMovementMethod
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通话页调试浮层：上半是当前状态快照，下半是滚动日志。
 *
 * 默认隐藏。日志在关闭时也会缓存，打开后能看到开关之前的事件。
 */
class DebugOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val statusView: TextView
    private val logScroller: ScrollView
    private val logView: TextView
    private val lines = ArrayList<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    init {
        orientation = VERTICAL
        setBackgroundColor(BACKGROUND)
        val pad = dp(10)
        setPadding(pad, pad, pad, pad)

        statusView = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            setTextColor(COLOR_STATUS)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setLineSpacing(0f, 1.15f)
        }
        addView(
            statusView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )

        logScroller = ScrollView(context).apply {
            isFillViewport = true
        }
        logView = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            setTextColor(COLOR_LOG)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setLineSpacing(0f, 1.1f)
            movementMethod = ScrollingMovementMethod.getInstance()
        }
        logScroller.addView(
            logView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        addView(
            logScroller,
            LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(8)
            }
        )
    }

    fun setStatus(text: String) {
        statusView.text = text
    }

    fun appendLog(message: String) {
        val line = "${timeFormat.format(Date())}  $message"
        lines.add(line)
        while (lines.size > MAX_LINES) {
            lines.removeAt(0)
        }
        logView.text = lines.joinToString("\n")
        logScroller.post {
            logScroller.fullScroll(FOCUS_DOWN)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_LINES = 80
        private const val BACKGROUND = 0xCC0B0E12.toInt()
        private const val COLOR_STATUS = 0xFF7EE0D8.toInt()
        private const val COLOR_LOG = 0xFFE6E8EB.toInt()
    }
}
