package com.tencent.uikit.app.common.widget

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.tencent.uikit.app.R

class UnreadCountTextView : AppCompatTextView {
    private var mNormalSize: Int = 0
    private lateinit var mPaint: Paint

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        textDirection = View.TEXT_DIRECTION_LTR
        mNormalSize = dp2px(18.4f)
        val typedArray: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.UnreadCountTextView)
        val paintColor = typedArray.getColor(R.styleable.UnreadCountTextView_paint_color, resources.getColor(R.color.app_color_read_dot_bg))
        typedArray.recycle()

        mPaint = Paint()
        mPaint.color = paintColor
        mPaint.isAntiAlias = true
    }

    fun setPaintColor(color: Int) {
        mPaint.color = color
    }

    override fun onDraw(canvas: Canvas) {
        when (text.length) {
            0 -> {
                val l = (measuredWidth - dp2px(6f)) / 2
                val t = l
                val r = measuredWidth - l
                val b = r
                canvas.drawOval(RectF(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat()), mPaint)
            }
            1 -> {
                canvas.drawOval(RectF(0f, 0f, mNormalSize.toFloat(), mNormalSize.toFloat()), mPaint)
            }
            else -> {
                canvas.drawRoundRect(
                    RectF(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat()), 
                    measuredHeight / 2f, 
                    measuredHeight / 2f, 
                    mPaint
                )
            }
        }
        super.onDraw(canvas)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = mNormalSize
        val height = mNormalSize
        if (text.length > 1) {
            width = mNormalSize + dp2px((text.length - 1) * 10f)
        }
        setMeasuredDimension(width, height)
    }

    private fun dp2px(dp: Float): Int {
        val displayMetrics: DisplayMetrics = resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics).toInt()
    }
}