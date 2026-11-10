package com.tencent.uikit.app.common.widget

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.tencent.uikit.app.R

class RoundFrameLayout : FrameLayout {
    private val path = Path()
    private val rectF = RectF()
    private val aliasFilter = PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var radius: Int = 0
    private var leftTopRadius: Int = 0
    private var rightTopRadius: Int = 0
    private var rightBottomRadius: Int = 0
    private var leftBottomRadius: Int = 0

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private fun init(context: Context, attrs: AttributeSet?) {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val defaultRadius = 0
        if (attrs != null) {
            val array: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.RoundFrameLayout)
            radius = array.getDimensionPixelOffset(R.styleable.RoundFrameLayout_corner_radius, defaultRadius)
            leftTopRadius = array.getDimensionPixelOffset(R.styleable.RoundFrameLayout_left_top_corner_radius, defaultRadius)
            rightTopRadius = array.getDimensionPixelOffset(R.styleable.RoundFrameLayout_right_top_corner_radius, defaultRadius)
            rightBottomRadius = array.getDimensionPixelOffset(R.styleable.RoundFrameLayout_right_bottom_corner_radius, defaultRadius)
            leftBottomRadius = array.getDimensionPixelOffset(R.styleable.RoundFrameLayout_left_bottom_corner_radius, defaultRadius)
            array.recycle()
        }

        if (defaultRadius == leftTopRadius) {
            leftTopRadius = radius
        }
        if (defaultRadius == rightTopRadius) {
            rightTopRadius = radius
        }
        if (defaultRadius == rightBottomRadius) {
            rightBottomRadius = radius
        }
        if (defaultRadius == leftBottomRadius) {
            leftBottomRadius = radius
        }
    }

    fun setLeftBottomRadius(leftBottomRadius: Int) {
        this.leftBottomRadius = leftBottomRadius
    }

    fun setLeftTopRadius(leftTopRadius: Int) {
        this.leftTopRadius = leftTopRadius
    }

    fun setRadius(radius: Int) {
        this.radius = radius
        leftBottomRadius = radius
        rightBottomRadius = radius
        rightTopRadius = radius
        leftTopRadius = radius
    }

    fun setRightBottomRadius(rightBottomRadius: Int) {
        this.rightBottomRadius = rightBottomRadius
    }

    fun setRightTopRadius(rightTopRadius: Int) {
        this.rightTopRadius = rightTopRadius
    }

    fun getLeftBottomRadius(): Int {
        return leftBottomRadius
    }

    fun getLeftTopRadius(): Int {
        return leftTopRadius
    }

    fun getRadius(): Int {
        return radius
    }

    fun getRightBottomRadius(): Int {
        return rightBottomRadius
    }

    fun getRightTopRadius(): Int {
        return rightTopRadius
    }

    override fun dispatchDraw(canvas: Canvas) {
        path.reset()
        canvas.drawFilter = aliasFilter
        rectF.set(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())
        // left-top -> right-top -> right-bottom -> left-bottom
        val radius = floatArrayOf(
            leftTopRadius.toFloat(), leftTopRadius.toFloat(), 
            rightTopRadius.toFloat(), rightTopRadius.toFloat(), 
            rightBottomRadius.toFloat(), rightBottomRadius.toFloat(), 
            leftBottomRadius.toFloat(), leftBottomRadius.toFloat()
        )
        path.addRoundRect(rectF, radius, Path.Direction.CW)
        canvas.clipPath(path)
        super.dispatchDraw(canvas)
    }
}