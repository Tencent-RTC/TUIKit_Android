package io.trtc.tuikit.chat.demo.ai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import kotlin.math.min

/**
 * 从中心向外扩散、逐渐变淡的光环，用在 AI 首屏的光球和通话按钮外圈。
 *
 * @param startFraction 光环起始直径占 View 短边的比例，一般等于被围住的元素大小
 */
class PulseRingsView(
    context: Context,
    private val ringColor: Int,
    private val ringCount: Int,
    private val periodMs: Long,
    private val startFraction: Float,
    private val maxAlpha: Float = 0.6f,
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val half = min(width, height) / 2f - paint.strokeWidth
        if (half <= 0f) {
            return
        }
        val base = (SystemClock.uptimeMillis() % periodMs).toFloat() / periodMs
        val density = resources.displayMetrics.density
        for (i in 0 until ringCount) {
            val p = (base + i.toFloat() / ringCount) % 1f
            // 减速扩散：刚出来快，越往外越慢
            val eased = 1f - (1f - p) * (1f - p)
            val radius = half * (startFraction + (1f - startFraction) * eased)
            // 先从 0 渐显，再随扩散渐隐，避免一出现就是一道贴着球边的硬线
            val fadeIn = (p / 0.18f).coerceAtMost(1f)
            val fadeOut = (1f - p) * (1f - p)
            val alpha = (maxAlpha * fadeIn * fadeOut * 255).toInt().coerceIn(0, 255)
            paint.strokeWidth = density * (2f - 1.2f * p)
            paint.color = (ringColor and 0x00FFFFFF) or (alpha shl 24)
            canvas.drawCircle(width / 2f, height / 2f, radius, paint)
        }
        if (isShown) {
            postInvalidateOnAnimation()
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            postInvalidateOnAnimation()
        }
    }
}
