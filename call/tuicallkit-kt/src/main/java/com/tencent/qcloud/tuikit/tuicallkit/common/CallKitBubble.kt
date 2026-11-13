package com.tencent.qcloud.tuikit.tuicallkit.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.tencent.qcloud.tuikit.tuicallkit.R

internal open class CallKitBubble @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    protected val stackView = LinearLayout(context)

    private val arrowView = ArrowView(context)
    private var arrowOffsetX = 0f

    init {
        clipToPadding = false
        clipChildren = false
        elevation = 14f.dp(context).toFloat()
        setBackgroundResource(R.drawable.tuicallkit_bg_bubble)

        stackView.orientation = LinearLayout.VERTICAL
        val stackParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        stackParams.topMargin = 6.dp(context)
        stackParams.bottomMargin = 6.dp(context)
        addView(stackView, stackParams)

        val arrowWidth = 18.dp(context)
        val arrowHeight = 11.dp(context)
        addView(arrowView, LayoutParams(arrowWidth, arrowHeight))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val arrowX = (arrowOffsetX - arrowView.width / 2f).coerceIn(0f, (width - arrowView.width).toFloat())
        arrowView.layout(arrowX.toInt(), height - 1, arrowX.toInt() + arrowView.width, height - 1 + arrowView.height)
    }

    fun setArrowCenterX(offset: Float) {
        arrowOffsetX = offset
        requestLayout()
    }

    private inner class ArrowView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.tuicallkit_bubble_background)
            style = Paint.Style.FILL
        }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            path.reset()
            path.moveTo(0f, 0f)
            path.lineTo(width.toFloat(), 0f)
            path.lineTo(width / 2f, height.toFloat())
            path.close()
            canvas.drawPath(path, paint)
        }
    }
}
